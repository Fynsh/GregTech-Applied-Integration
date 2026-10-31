package com.Fynsh.gtai.blockentity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.blockentity.AEBaseBlockEntity;

import com.Fynsh.gtai.block.MatrixFrameBlock;
import com.Fynsh.gtai.config.GtaiConfig;
import com.Fynsh.gtai.multiblock.MatrixMultiblockDefinition;
import com.Fynsh.gtai.multiblock.MatrixMultiblocks;
import com.Fynsh.gtai.multiblock.MatrixStructureBounds;
import com.Fynsh.gtai.multiblock.MatrixStructureInfo;
import com.Fynsh.gtai.multiblock.MatrixStructureMatch;
import com.Fynsh.gtai.util.TickTaskQueue;

/**
 * 矩阵监控器核心的方块实体。
 *
 * <p>本阶段保存成型状态、渲染信息与多方块详细信息，并负责把结构接入 AE 网络
 * （监控器侧频道节点 + 框架侧端口节点）；不实现 GUI、能量或配方交互，仅保留一个
 * 服务端 tick 用于结构失效后的延迟数据清理。结构从"未成型"变为"成型"时播放一次
 * 玩家获得经验球的声音；成型结果中的框架外观与能源仓位置会同步到客户端，供 BER 绘制覆盖层。</p>
 *
 * <p><b>状态分层：</b>AE2 侧只使用 {@link #isFormed()} 这个“是否成型”布尔状态；
 * 外壳类型/等级、能源仓电压、内容方块与过滤器等详细信息保存在
 * {@link MatrixStructureInfo} 中，并以 {@value MatrixStructureInfo#TAG_ROOT} 子标签写入
 * 本方块实体的 NBT。</p>
 */
public class MatrixMonitorBlockEntity extends AEBaseBlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String TAG_FORMED = "gtai_formed";
    private static final String TAG_FRAME = "gtai_frame";
    private static final String TAG_HATCHES = "gtai_hatches";

    /** 单个框架端口的最大承载频道数（DENSE_CAPACITY 对应 32）。 */
    private static final int FRAME_PORT_CHANNEL_CAPACITY = 32;

    /** 防止异常结构创建过多频道节点；超过时视为频道过载，禁止接入。 */
    private static final int MAX_CHANNEL_NODES = 8192;

    /** 重进世界时结构判定的最大重试次数，避免区块加载顺序造成误判。 */
    private static final int MAX_INITIAL_VALIDATION_RETRIES = 3;

    private static final IGridNodeListener<ChannelNodeOwner> CHANNEL_NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(ChannelNodeOwner owner, IGridNode node) {
            // 频道节点完全由结构信息重建，不需要持久化
        }

        @Override
        public void onStateChanged(ChannelNodeOwner owner, IGridNode node, State state) {
            if (state == State.CHANNEL) {
                owner.monitor().onChannelNodeStateChanged();
            }
        }
    };

    /** 每个频道节点使用独立 owner，避免同一 owner 的多个节点在 AE 统计中被合并。 */
    private record ChannelNodeOwner(MatrixMonitorBlockEntity monitor, int index) {
    }

    /** AE2 侧使用的成型状态：只表示结构是否成型，详细信息在 {@link #structureInfo} / NBT 中。 */
    private boolean formed;

    @Nullable
    private MatrixStructureMatch match;
    @Nullable
    private BlockState frameState;
    private List<BlockPos> energyHatches = List.of();

    /** 成型详情快照；结构失效后仍会保留一段时间。 */
    @Nullable
    private MatrixStructureInfo structureInfo;

    /** 延迟清理时间点；-1 表示没有待清理数据。 */
    private long dataClearAt = -1L;

    /** 本次数据是否从存档读取，用于进入世界后的首次存储值/实际值校验。 */
    private boolean infoLoadedFromDisk;

    private boolean recoveryScheduled;
    private boolean reconnectAttempted;
    private boolean initialRefreshDone;

    /** 重进世界时区块可能尚未全部加载；先延迟判定，避免把仍然完好的结构误判为失效。 */
    private boolean initialValidationPending;
    private int initialValidationRetries;
    private boolean initialValidationRetryScheduled;

    /** 每次结构状态切换时递增；旧的延迟恢复任务据此自动失效。 */
    private int recoveryToken;

    /** 严格按 gameTime 执行的任务队列；TickTask 在服务端有空闲时间时可能提前执行。 */
    private final TickTaskQueue scheduledTasks = new TickTaskQueue();

    /** 监控器侧真正占用频道的节点；数量 = 1 + 样板供应器等价数量。 */
    private final List<IManagedGridNode> channelNodes = new ArrayList<>();

    /** 框架端口与频道节点之间的逻辑连接，重建频道时统一销毁。 */
    private final List<IGridConnection> channelConnections = new ArrayList<>();

    /** 当前已接入的框架端口；只有真正有线缆访问过的框架才会出现在这里。 */
    private final Map<IGridNode, MatrixFrameBlockEntity> framePorts = new LinkedHashMap<>();

    /** 上次成型扫描到的框架位置，用于失效时解绑。 */
    private List<BlockPos> framePositions = List.of();

    /** 当前结构应占用的频道数。 */
    private int requiredChannels;

    /** 频道容量不足或频道数超过上限时为 true；仅作为状态标记，当前仍交给 AE 路径分配。 */
    private boolean channelOverloaded;

    /** AE 实际分配后仍有频道节点没有拿到频道（线缆/控制器容量不足）。 */
    private boolean networkOverloaded;

    /** 区块/方块实体刚加载时，强制让相邻线缆重新查询一次框架端口。 */
    private boolean revalidateFrames;

    /** 防止同一 tick 内重复调度整壳更新。 */
    private boolean shellRevalidationScheduled;

    /**
     * 从存档/区块恢复时的首次判定不播放声音，避免每次加载世界都响一次。
     * 玩家实际完成结构（方块变化触发判定）时不抑制。
     */
    private boolean suppressFormationSound;

    public MatrixMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(MatrixBlockEntities.MATRIX_MONITOR.get(), pos, state);
    }

    @Override
    public void onReady() {
        super.onReady();
        initialRefreshDone = true;
        // 加载后线缆可能先于/后于本监控器加载，强制重新检查一次框架端口
        reconnectAttempted = false;
        revalidateFrames = true;
        if ((formed || structureInfo != null) && !initialValidationPending) {
            initialValidationPending = true;
            initialValidationRetries = 0;
            initialValidationRetryScheduled = false;
        }
        refreshFormed();

        // 结构可能跨越尚未完成加载的区块，下一 tick 再核对一次存储值与实际结构
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getServer().execute(() -> {
                if (!isRemoved() && level != null) {
                    revalidateFrames = true;
                    refreshFormed();
                }
            });
        }
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);

        // 客户端收到的是 getUpdateTag 的同步数据；服务端收到的是存档数据。
        // 两边都先恢复状态；服务端随后重新判定，判定失败会清空全部成型信息。
        readState(data);
        readRuntimeData(data);

        if (level == null || level.isClientSide) {
            return;
        }

        if (level instanceof ServerLevel) {
            // 刚读档时相邻区块/框架可能还没加载；只有确实存过成型数据时才延迟判定。
            initialValidationPending = formed || structureInfo != null;
            initialValidationRetries = 0;
            initialValidationRetryScheduled = false;
            suppressFormationSound = true;
            try {
                refreshFormed();
            } finally {
                suppressFormationSound = false;
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeState(tag);
        writeRuntimeData(tag);
    }

    /**
     * 通知一个位置附近已加载的监控器重新判定。
     *
     * <p>矩阵方块没有方块实体，无法像 EAE 那样由每个结构件自己维护集群；这里在
     * MatrixBlock 放置/移除时扫描附近已加载区块中的监控器。只遍历已加载区块的
     * block entity 表，不强制加载区块。</p>
     */
    public static void refreshNear(Level level, BlockPos changed) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int radius = Math.max(1, GtaiConfig.maxSize());
        int minChunkX = (changed.getX() - radius) >> 4;
        int maxChunkX = (changed.getX() + radius) >> 4;
        int minChunkZ = (changed.getZ() - radius) >> 4;
        int maxChunkZ = (changed.getZ() + radius) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                // 复制一份，避免 refreshFormed 期间方块实体表发生变化导致并发修改
                for (BlockEntity blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
                    if (blockEntity instanceof MatrixMonitorBlockEntity monitor) {
                        BlockPos pos = monitor.getBlockPos();
                        if (Math.abs(pos.getX() - changed.getX()) <= radius
                                && Math.abs(pos.getY() - changed.getY()) <= radius
                                && Math.abs(pos.getZ() - changed.getZ()) <= radius) {
                            monitor.refreshFormed();
                        }
                    }
                }
            }
        }
    }

    /**
     * 重新执行成型判定，并在成功后把详细信息写入唯一矩阵监控器。
     *
     * <p>入口与监控器解耦：这里只是调用
     * {@link MatrixMultiblocks#findMatchAt(Level, BlockPos)}。
     * 成型/失效时调用预留回调，后续阶段可以在回调里接入交互逻辑。</p>
     *
     * <p>信息收集失败不会推翻成型判定：只要几何/方块校验成功，{@link #isFormed()}
     * 仍为 {@code true}；此时只记录一条警告，不写入详情。</p>
     */
    public boolean refreshFormed() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return formed;
        }

        boolean wasFormed = formed;
        boolean forceRevalidate = revalidateFrames;
        MatrixStructureMatch next = MatrixMultiblocks.findMatchAt(serverLevel, worldPosition).orElse(null);

        if (next == null && initialValidationPending) {
            if (initialValidationRetries < MAX_INITIAL_VALIDATION_RETRIES && !initialValidationRetryScheduled) {
                initialValidationRetryScheduled = true;
                initialValidationRetries++;
                scheduleTask(20, () -> {
                    initialValidationRetryScheduled = false;
                    if (!isRemoved()) {
                        refreshFormed();
                    }
                });
                return formed;
            }
            initialValidationPending = false;
        } else if (next != null) {
            initialValidationPending = false;
            initialValidationRetries = 0;
            initialValidationRetryScheduled = false;
        }

        // 只有成型判定成功后才尝试收集详细信息
        MatrixStructureInfo nextInfo = null;
        if (next != null) {
            try {
                nextInfo = MatrixStructureInfo.collect(serverLevel, next);
            } catch (RuntimeException e) {
                // 详情收集属于“成型后的附加信息”，失败不应推翻已经成立的成型判定
                LOGGER.error("Failed to collect matrix structure info at {}", worldPosition, e);
            }
            if (nextInfo == null) {
                LOGGER.warn("No matrix structure info available at {}", worldPosition);
            }
        }

        boolean changed = !Objects.equals(match, next);
        match = next;
        formed = next != null;
        frameState = next == null ? null : next.frameState();
        energyHatches = next == null ? List.of() : next.energyHatches();

        // 接入/断开 AE：频道数 = 1 + 样板供应器等价数量
        if (next != null) {
            dataClearAt = -1L;
            if (changed) {
                // 结构形状/类型发生变化时，旧的延迟恢复任务不再适用于新结构
                recoveryScheduled = false;
                recoveryToken++;
            }
            if (!wasFormed) {
                reconnectAttempted = false;
            }
            // 进入世界/结构变化后，用实际扫描值校正存档里的旧快照；
            // 若实际扫描失败则保留旧数据，绝不因一次失败清空。
            updateStructureInfo(serverLevel, next, nextInfo);

            MatrixStructureInfo effectiveInfo = nextInfo != null ? nextInfo : structureInfo;
            long required = 1L + (effectiveInfo == null ? 0L : effectiveInfo.patternProviderCount());
            updateChannels((int) Math.min(Integer.MAX_VALUE, required));
            syncFrames(next.bounds());

            // 成型/重载后，让整个外壳向周围发送方块更新：
            // 已存在的 AE 线缆会重新查询框架端口，避免“先放线缆后建矩阵”或重进世界后频道为 0。
            if (changed || forceRevalidate) {
                scheduleShellNeighborUpdates(next.bounds());
                scheduleChannelRecovery();
                // syncFrames 可能在本 tick 内才建立端口连接；只在结构状态变化时再 repath
                repathChannelNetworks();
            }
        } else {
            // 结构失效只断开 AE 和端口，数据保留到延迟清理时间
            recoveryScheduled = false;
            reconnectAttempted = false;
            recoveryToken++;
            clearChannels();
            clearFrames();
            scheduleDataClear();
        }

        if (changed) {
            if (formed) {
                // 只在“从未成型到成型”时播放；结构类型切换（已经成型）不重复播放
                if (!wasFormed && !suppressFormationSound) {
                    serverLevel.playSound(null, worldPosition, SoundEvents.EXPERIENCE_ORB_PICKUP,
                            SoundSource.PLAYERS, 1.0F, 1.0F);
                }
                onStructureFormed(next.definition());
            } else {
                onStructureInvalid();
            }
            setChanged();
            markForUpdate();
        }
        return formed;
    }

    /**
     * 用实际扫描值校正监控器详情。
     *
     * <p>结构失效时由调用方保留旧数据，本方法不会被调用；结构正常成型但扫描失败时
     * 也直接返回，避免用 {@code null} 覆盖存档数据。</p>
     *
     * <p>正常情况下 {@code match.monitorPos()} 就是当前方块；保留分支是为了让
     * 未来从能源仓等锚点触发判定时也能写到正确的监控器。</p>
     */
    private void updateStructureInfo(ServerLevel level, MatrixStructureMatch match,
            @Nullable MatrixStructureInfo info) {
        if (info == null) {
            return;
        }

        if (infoLoadedFromDisk) {
            if (structureInfo != null && !structureInfo.equals(info)) {
                LOGGER.info("Monitor structure data at {} differed from the saved value; using actual structure values",
                        worldPosition);
            }
            infoLoadedFromDisk = false;
        }

        if (match.monitorPos().equals(worldPosition)) {
            setStructureInfo(info);
            return;
        }

        if (level.getBlockEntity(match.monitorPos()) instanceof MatrixMonitorBlockEntity monitor) {
            monitor.setStructureInfo(info);
        } else {
            LOGGER.warn("Matrix monitor at {} is not loaded; structure info was not written", match.monitorPos());
        }
    }

    /** 结构失效后延迟清理监控器数据；已经排定清理时不重复延后。 */
    private void scheduleDataClear() {
        if (structureInfo == null || !(level instanceof ServerLevel serverLevel)) {
            dataClearAt = -1L;
            return;
        }
        if (dataClearAt < 0L) {
            dataClearAt = serverLevel.getGameTime() + GtaiConfig.monitorDataRetentionTicks();
        }
    }

    /** 真正删除监控器数据；结构重新成型时会取消该清理。 */
    private void clearStoredData() {
        if (structureInfo != null) {
            setStructureInfo(null);
        }
        // 后续若增加样板槽/材料缓存等持久数据，也必须在这里一起清理
        dataClearAt = -1L;
        infoLoadedFromDisk = false;
        onStructureDataCleared();
    }

    private void scheduleTask(int delay, Runnable action) {
        if (level instanceof ServerLevel serverLevel) {
            scheduledTasks.schedule(serverLevel.getGameTime(), delay, action);
        }
    }

    /** 服务端 tick：执行延迟清理。由 {@code MatrixMonitorBlock} 的 ticker 调用。 */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        scheduledTasks.runDue(serverLevel.getGameTime());
        if (!initialRefreshDone) {
            initialRefreshDone = true;
            // 有些加载路径不会调用 onReady；保证进入世界后下一 tick 主动成型一次
            scheduleTask(1, () -> {
                if (!isRemoved()) {
                    refreshFormed();
                }
            });
        }
        if (dataClearAt >= 0L && serverLevel.getGameTime() >= dataClearAt) {
            dataClearAt = -1L;
            if (!formed) {
                clearStoredData();
            }
        }
    }

    private void setStructureInfo(@Nullable MatrixStructureInfo info) {
        if (Objects.equals(structureInfo, info)) {
            return;
        }
        structureInfo = info;
        setChanged();
        markForUpdate();
    }

    /**
     * 按当前结构重建监控器侧的频道节点。
     *
     * <p>频道节点是普通的、非世界内节点，每个都带 {@code REQUIRE_CHANNEL}，因此
     * AE 会把它们各算作一个独立频道。框架端口节点本身不占频道，只负责把线缆的
     * 频道转发到这些节点。结构总频道数因此严格等于节点数量。</p>
     */
    private void updateChannels(int required) {
        if (required == requiredChannels
                && (channelNodes.size() == required || required > MAX_CHANNEL_NODES)) {
            refreshChannelOverload();
            return;
        }

        clearChannels();
        requiredChannels = Math.max(0, required);

        if (!(level instanceof ServerLevel serverLevel) || requiredChannels == 0) {
            return;
        }

        if (requiredChannels > MAX_CHANNEL_NODES) {
            channelOverloaded = true;
            LOGGER.warn("Matrix at {} requires {} channels, above the supported limit {}; AE connection is disabled",
                    worldPosition, requiredChannels, MAX_CHANNEL_NODES);
            return;
        }

        for (int index = 0; index < requiredChannels; index++) {
            createChannelNode(serverLevel, index);
        }

        refreshChannelOverload();
        // 不因一次容量估算就断开频道节点；AE 路径分配会把未拿到频道的节点标记为离线
        connectAllPorts();
        repathChannelNetworks();
    }

    private void createChannelNode(ServerLevel serverLevel, int index) {
        IManagedGridNode node = GridHelper.createManagedNode(new ChannelNodeOwner(this, index), CHANNEL_NODE_LISTENER)
                .setInWorldNode(false)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(0)
                .setTagName("gtai_matrix_channel_" + index)
                .setVisualRepresentation(getBlockState().getBlock());
        node.create(serverLevel, worldPosition);
        channelNodes.add(node);
    }

    private void clearChannels() {
        disconnectAllPorts();
        for (IManagedGridNode node : channelNodes) {
            node.destroy();
        }
        channelNodes.clear();
        requiredChannels = 0;
        channelOverloaded = false;
        networkOverloaded = false;
    }

    private void refreshChannelOverload() {
        if (requiredChannels > MAX_CHANNEL_NODES) {
            channelOverloaded = true;
            return;
        }
        int capacity = 0;
        for (IGridNode port : framePorts.keySet()) {
            int portCapacity = 0;
            for (IGridConnection connection : port.getInWorldConnections().values()) {
                IGridNode other = connection.getOtherSide(port);
                if (other != null) {
                    try {
                        portCapacity += Math.max(0, other.getMaxChannels());
                    } catch (RuntimeException ignored) {
                        // 节点尚未完成入网时按 0 处理，后续连接事件会重新评估
                    }
                }
            }
            capacity += Math.min(FRAME_PORT_CHANNEL_CAPACITY, portCapacity);
        }
        // capacity == 0 表示线缆/节点尚未完成入网，不能据此判定过载；先乐观接入，
        // 实际频道分配仍交给 AE 路径计算，过载状态只用于提示/后续运行限制。
        channelOverloaded = requiredChannels > 0 && !framePorts.isEmpty()
                && capacity > 0 && requiredChannels > capacity;
    }

    /** 结构成型后延迟 repath；若端口仍为 0 频道，再重建一次线缆/端口/频道节点。 */
    private void scheduleChannelRecovery() {
        if (!(level instanceof ServerLevel) || recoveryScheduled) {
            return;
        }
        recoveryScheduled = true;
        int token = recoveryToken;
        for (int delay : new int[] { 20, 60, 200 }) {
            scheduleTask(delay, () -> {
                if (token != recoveryToken || isRemoved() || !(level instanceof ServerLevel)) {
                    return;
                }
                if (delay >= 20) {
                    repathChannelNetworks();
                }
                if (delay >= 60) {
                    recoverChannelsIfNeeded();
                }
                if (delay >= 200) {
                    recoveryScheduled = false;
                }
            });
        }
    }

    /** 结构节点在重载后可能已齐全，但 AE 路径尚未重新计算；主动 repath 一次。 */
    private void repathChannelNetworks() {
        if (channelNodes.isEmpty() && framePorts.isEmpty()) {
            return;
        }
        Set<IGrid> grids = new HashSet<>();
        for (IManagedGridNode managed : channelNodes) {
            IGridNode node = managed.getNode();
            if (node != null && node.getGrid() != null) {
                grids.add(node.getGrid());
            }
        }
        for (IGridNode port : framePorts.keySet()) {
            if (port.getGrid() != null) {
                grids.add(port.getGrid());
            }
        }
        for (IGrid grid : grids) {
            grid.getPathingService().repath();
        }
    }

    /** 重载后端口存在但 AE 路径没有分配任何频道时，重建一次框架端口。 */
    private void recoverChannelsIfNeeded() {
        if (!formed || channelNodes.isEmpty() || framePorts.isEmpty()
                || maxFramePortUsedChannels() > 0 || reconnectAttempted) {
            return;
        }
        reconnectAttempted = true;
        int required = requiredChannels;
        Set<MatrixFrameBlockEntity> frames = new HashSet<>(framePorts.values());
        for (MatrixFrameBlockEntity frame : frames) {
            frame.refreshNeighborCables();
        }
        // 频道节点本身也可能落在旧的断开 grid 中；连同端口一起重建
        clearChannels();
        updateChannels(required);
        for (MatrixFrameBlockEntity frame : frames) {
            frame.recreatePorts();
        }
        repathChannelNetworks();
    }

    /** 由框架方块实体在端口建立后调用。 */
    void attachFramePort(MatrixFrameBlockEntity frame, IGridNode port) {
        if (port == null || !(level instanceof ServerLevel)) {
            return;
        }

        MatrixFrameBlockEntity previous = framePorts.put(port, frame);
        if (previous == frame) {
            return;
        }

        refreshChannelOverload();
        connectPort(port);
        // 端口是在频道节点之后才出现的；立即重算一次路径，避免 UI 先看到 0 频道
        repathChannelNetworks();
    }

    /** 框架端口的线缆连接发生变化后重新评估可用容量。 */
    void onFramePortConnectionChanged() {
        if (!(level instanceof ServerLevel) || framePorts.isEmpty()) {
            return;
        }

        // 仅更新容量过载状态；频道连接本身交给 AE 路径分配处理
        refreshChannelOverload();
    }

    /** 由框架方块实体在拆除/解绑时调用。 */
    void detachFramePort(IGridNode port) {
        if (port == null || framePorts.remove(port) == null) {
            return;
        }

        disconnectPort(port);
        refreshChannelOverload();
    }

    private void connectPort(IGridNode port) {
        if (port == null) {
            return;
        }

        for (IManagedGridNode managed : channelNodes) {
            IGridNode channel = managed.getNode();
            if (channel == null) {
                continue;
            }

            boolean exists = false;
            for (IGridConnection connection : port.getConnections()) {
                if (connection.getOtherSide(port) == channel) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                try {
                    channelConnections.add(GridHelper.createConnection(port, channel));
                } catch (RuntimeException e) {
                    // 端口/节点可能正处于拆除过程中；记录后继续，不能让结构刷新整体失败
                    LOGGER.warn("Failed to connect matrix frame port to channel node at {}", worldPosition, e);
                }
            }
        }
    }

    private void disconnectPort(IGridNode port) {
        if (port == null) {
            return;
        }
        for (Iterator<IGridConnection> iterator = channelConnections.iterator(); iterator.hasNext();) {
            IGridConnection connection = iterator.next();
            if (connection.a() == port || connection.b() == port) {
                try {
                    connection.destroy();
                } catch (RuntimeException ignored) {
                    // 节点可能已被区块卸载/网络重建销毁
                }
                iterator.remove();
            }
        }
    }

    private void connectAllPorts() {
        disconnectAllPorts();
        for (IGridNode port : framePorts.keySet()) {
            connectPort(port);
        }
    }

    private void disconnectAllPorts() {
        for (IGridConnection connection : channelConnections) {
            try {
                connection.destroy();
            } catch (RuntimeException ignored) {
                // 节点可能已被区块卸载/网络重建销毁
            }
        }
        channelConnections.clear();
    }

    /**
     * 把结构内的框架方块绑定到本监控器；只有位置发生变化时才触发相邻线缆重新扫描。
     */
    private void syncFrames(MatrixStructureBounds bounds) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean forceRevalidate = revalidateFrames;
        revalidateFrames = false;
        List<BlockPos> previous = framePositions;
        Set<BlockPos> currentSet = new HashSet<>();
        List<BlockPos> current = new ArrayList<>();
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int y = bounds.min().getY(); y <= bounds.max().getY(); y++) {
                for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (bounds.boundaryCount(pos) == 0) {
                        continue;
                    }

                    BlockState state = serverLevel.getBlockState(pos);
                    if (!(state.getBlock() instanceof MatrixFrameBlock)) {
                        continue;
                    }

                    current.add(pos);
                    currentSet.add(pos);
                    if (serverLevel.getBlockEntity(pos) instanceof MatrixFrameBlockEntity frame) {
                        boolean changed = frame.bindToMonitor(worldPosition);
                        if (changed || forceRevalidate) {
                            // 延后到下一 tick，避免在成型判定过程中触发监控器/线缆的递归刷新
                            BlockState frameState = state;
                            serverLevel.getServer().execute(() -> {
                                if (!isRemoved() && level != null) {
                                    level.updateNeighborsAt(pos, frameState.getBlock());
                                }
                            });
                        }
                    }
                }
            }
        }

        // 结构边界变化后，旧框架不应继续保留端口/频道容量
        for (BlockPos old : previous) {
            if (!currentSet.contains(old)
                    && serverLevel.getBlockEntity(old) instanceof MatrixFrameBlockEntity frame) {
                frame.unbindFromMonitor();
            }
        }
        framePositions = List.copyOf(current);
    }

    /**
     * 让结构的每一个外壳方块在下一 tick 向六个方向发送方块更新。
     *
     * <p>AE 线缆/总线只有在接到邻居更新时才会重新调用框架的 {@code getGridNode}。
     * 因此在结构成型、世界重载、或框架端口重建后，仅更新框架本身不够：整壳更新可以
     * 保证任何贴在外壳上的线缆都重新尝试接入，同时也不会在结构内部造成无限递归
     * （监控器只在外壳边界之外的方块上发更新，且同一 tick 只调度一次）。</p>
     */
    private void scheduleShellNeighborUpdates(MatrixStructureBounds bounds) {
        if (shellRevalidationScheduled || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        shellRevalidationScheduled = true;

        serverLevel.getServer().execute(() -> {
            shellRevalidationScheduled = false;
            if (isRemoved() || !(level instanceof ServerLevel currentLevel)) {
                return;
            }

            for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
                for (int y = bounds.min().getY(); y <= bounds.max().getY(); y++) {
                    for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (bounds.boundaryCount(pos) == 0) {
                            continue;
                        }
                        BlockState state = currentLevel.getBlockState(pos);
                        currentLevel.updateNeighborsAt(pos, state.getBlock());
                    }
                }
            }
        });
    }

    private void clearFrames() {
        if (level instanceof ServerLevel serverLevel) {
            for (BlockPos pos : framePositions) {
                if (serverLevel.getBlockEntity(pos) instanceof MatrixFrameBlockEntity frame) {
                    frame.unbindFromMonitor();
                }
            }
        }
        framePositions = List.of();
        framePorts.clear();
    }

    /** 当前结构要求的频道数；未成型时为 0。 */
    public int requiredChannels() {
        return requiredChannels;
    }

    /** 端口容量不足、AE 分配不足或频道数超过实现上限时为 true。 */
    public boolean isChannelOverloaded() {
        return channelOverloaded || networkOverloaded;
    }

    /** 仅由端口容量/实现上限导致的过载。 */
    public boolean isCapacityOverloaded() {
        return channelOverloaded;
    }

    private void onChannelNodeStateChanged() {
        if (channelNodes.isEmpty()) {
            return;
        }

        boolean anyOnGrid = false;
        boolean allPowered = true;
        for (IManagedGridNode managed : channelNodes) {
            IGridNode node = managed.getNode();
            if (node == null || node.getGrid() == null) {
                continue;
            }
            anyOnGrid = true;
            if (node.getUsedChannels() <= 0) {
                allPowered = false;
                break;
            }
        }
        networkOverloaded = anyOnGrid && !allPowered;
    }

    /** 当前已创建的频道节点数量；正常情况下等于 {@link #requiredChannels()}。 */
    public int channelNodeCount() {
        return channelNodes.size();
    }

    /** 当前已接入的框架端口数量，用于后续界面/运行状态判断。 */
    public int framePortCount() {
        return framePorts.size();
    }

    /**
     * AE 侧是否已经真正可用：结构成型、频道节点数量正确且 AE 已经分配到频道。
     *
     * <p>后续配方运行逻辑应以此为启动条件；刚重进世界时 {@link #isFormed()} 可能已经为 true，
     * 但端口/路径还未恢复，不能立刻开始发配方。</p>
     */
    public boolean isAeNetworkOperational() {
        if (!formed || initialValidationPending || isChannelOverloaded() || requiredChannels <= 0
                || channelNodes.size() != requiredChannels) {
            return false;
        }
        for (IManagedGridNode managed : channelNodes) {
            IGridNode node = managed.getNode();
            if (node == null || node.getUsedChannels() <= 0) {
                return false;
            }
        }
        return true;
    }

    /** 重进世界后的初次结构判定是否仍在重试。 */
    public boolean isStructureValidationPending() {
        return initialValidationPending;
    }

    /** 已接入端口上的最大频道占用，用于诊断 AE 重载后的实际频道数。 */
    public int maxFramePortUsedChannels() {
        int max = 0;
        for (IGridNode port : framePorts.keySet()) {
            max = Math.max(max, port.getUsedChannels());
        }
        return max;
    }

    /** 当前是否成型。这是 AE2 侧唯一使用的多方块状态。 */
    public boolean isFormed() {
        return formed;
    }

    /** 当前匹配到的结构定义；未成型时为 {@code null}。 */
    @Nullable
    public MatrixMultiblockDefinition structure() {
        return match == null ? null : match.definition();
    }

    /**
     * 当前成型详情快照；结构失效后仍会保留到延迟清理时间，因此可能不为 {@code null}。
     *
     * <p>这是后续交互逻辑（样板槽、并行数、速度、能量等）的只读入口。</p>
     */
    @Nullable
    public MatrixStructureInfo structureInfo() {
        return structureInfo;
    }

    /** 本次结构实际使用的框架方块状态；仅成型时有值。 */
    @Nullable
    public BlockState frameState() {
        return frameState;
    }

    /** 能源仓位置（通常只有一个）；仅成型时有值。 */
    public List<BlockPos> energyHatches() {
        return energyHatches;
    }

    @Override
    public void setRemoved() {
        onStructureUnloaded();
        resetTransientState();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        onStructureUnloaded();
        resetTransientState();
        super.onChunkUnloaded();
    }

    /** 方块实体移除/区块卸载时的统一收尾：断开 AE 连接并丢弃未执行的延迟任务。 */
    private void resetTransientState() {
        clearChannels();
        clearFrames();
        scheduledTasks.clear();
        initialRefreshDone = false;
        initialValidationPending = false;
        initialValidationRetryScheduled = false;
        recoveryToken++;
        dataClearAt = -1L;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(Math.max(1, GtaiConfig.maxSize()));
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeState(tag);
        return tag;
    }

    /** 把成型状态、渲染信息与详情写入 NBT。存档与客户端同步共用同一套键。 */
    private void writeState(CompoundTag tag) {
        tag.putBoolean(TAG_FORMED, formed);

        if (frameState != null) {
            ResourceLocation frameId = ForgeRegistries.BLOCKS.getKey(frameState.getBlock());
            if (frameId != null) {
                tag.putString(TAG_FRAME, frameId.toString());
            } else {
                tag.remove(TAG_FRAME);
            }
        } else {
            tag.remove(TAG_FRAME);
        }

        ListTag hatches = new ListTag();
        for (BlockPos pos : energyHatches) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", pos.getX());
            entry.putInt("y", pos.getY());
            entry.putInt("z", pos.getZ());
            hatches.add(entry);
        }
        tag.put(TAG_HATCHES, hatches);

        if (structureInfo != null) {
            // 结构失效期间也保留数据，避免退出/重进时吞掉材料或缓存
            tag.put(MatrixStructureInfo.TAG_ROOT, structureInfo.save());
        } else {
            tag.remove(MatrixStructureInfo.TAG_ROOT);
        }
    }

    /** 从存档或客户端同步 NBT 恢复状态；不触发成型判定。 */
    private void readState(CompoundTag data) {
        formed = data.getBoolean(TAG_FORMED);
        frameState = null;
        if (data.contains(TAG_FRAME, Tag.TAG_STRING)) {
            ResourceLocation frameId = ResourceLocation.tryParse(data.getString(TAG_FRAME));
            Block block = frameId == null ? null : ForgeRegistries.BLOCKS.getValue(frameId);
            if (block != null) {
                frameState = block.defaultBlockState();
            }
        }

        List<BlockPos> hatches = new ArrayList<>();
        ListTag list = data.getList(TAG_HATCHES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            hatches.add(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));
        }
        energyHatches = List.copyOf(hatches);

        structureInfo = null;
        infoLoadedFromDisk = false;
        if (data.contains(MatrixStructureInfo.TAG_ROOT, Tag.TAG_COMPOUND)) {
            structureInfo = MatrixStructureInfo.load(data.getCompound(MatrixStructureInfo.TAG_ROOT));
            infoLoadedFromDisk = structureInfo != null;
        }
    }

    /**
     * 预留：结构成型后的逻辑入口。
     *
     * <p>后续阶段可在这里建立任务队列、监控器界面等；该回调在重进世界恢复结构时也会触发，
     * 因此实现必须可重复调用，并且不要在 AE 网络尚未就绪时开始发配方——先用
     * {@link #isAeNetworkOperational()} 和 {@link #isStructureValidationPending()} 判断。</p>
     */
    protected void onStructureFormed(MatrixMultiblockDefinition definition) {
        // no-op by design: interaction logic is a later stage
    }

    /**
     * 预留：结构失效后的逻辑入口。
     *
     * <p>此时 AE 连接已断开，但监控器数据仍在保留期内；后续实现应在这里暂停/中止
     * 运行时任务，不要清空样板槽、材料缓存等持久数据，清理统一走
     * {@link #clearStoredData()}。</p>
     */
    protected void onStructureInvalid() {
        // no-op by design: interaction logic is a later stage
    }

    /**
     * 预留：延迟保留期结束、监控器持久数据真正被删除时调用。
     *
     * <p>后续实现的配方槽、缓存、进度、任务 ID 等必须在这里一并清空，否则重新成型后
     * 会读到已经失效的旧运行状态。</p>
     */
    protected void onStructureDataCleared() {
        // no-op by design: interaction logic is a later stage
    }

    /**
     * 预留：监控器所在区块卸载/方块实体移除时调用。
     *
     * <p>运行时任务应在这里暂停并停止访问世界；可持久化的运行状态仍应写入 NBT，
     * 等重进后由 {@link #onStructureFormed(MatrixMultiblockDefinition)} 重新启动。</p>
     */
    protected void onStructureUnloaded() {
        // no-op by design: interaction logic is a later stage
    }

    /**
     * 预留：把配方运行状态写入存档 NBT。
     *
     * <p>只会在 {@link #saveAdditional(CompoundTag)} 中调用，不会进入客户端同步包；
     * 配方槽、进度、缓存引用等需要跨重进保留的数据应写在这里。</p>
     */
    protected void writeRuntimeData(CompoundTag tag) {
        // no-op by design: interaction logic is a later stage
    }

    /**
     * 预留：从存档 NBT 恢复配方运行状态。
     *
     * <p>在服务端和客户端加载方块实体时调用；实现必须能处理旧版本/缺失标签，
     * 并在随后的 {@link #onStructureFormed(MatrixMultiblockDefinition)} 中校验结构是否仍匹配。</p>
     */
    protected void readRuntimeData(CompoundTag tag) {
        // no-op by design: interaction logic is a later stage
    }
}
