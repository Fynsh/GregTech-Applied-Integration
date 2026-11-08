package com.Fynsh.gtai.blockentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
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

import com.Fynsh.gtai.block.MatrixBlockRole;
import com.Fynsh.gtai.block.MatrixFamily;
import com.Fynsh.gtai.block.MatrixFrameBlock;
import com.Fynsh.gtai.config.GtaiConfig;
import com.Fynsh.gtai.menu.MatrixMenu;
import com.Fynsh.gtai.multiblock.MatrixEnergyHatch;
import com.Fynsh.gtai.multiblock.MatrixFormationExplosion;
import com.Fynsh.gtai.multiblock.MatrixMultiblockDefinition;
import com.Fynsh.gtai.multiblock.MatrixMultiblocks;
import com.Fynsh.gtai.multiblock.MatrixStructureInfo;
import com.Fynsh.gtai.multiblock.MatrixStructureLayout;
import com.Fynsh.gtai.multiblock.MatrixStructureMatch;
import com.Fynsh.gtai.pattern.MatrixPatternRules;
import com.Fynsh.gtai.util.TickTaskQueue;

/**
 * 矩阵监控器核心的方块实体。
 *
 * <p>保存成型状态、渲染信息、多方块详细信息与样板库存，并负责把结构接入 AE 网络
 * （监控器侧频道节点 + 框架侧端口节点）。本阶段提供样板 GUI 菜单与样板存储；
 * 配方执行、能量消耗仍在后续阶段。结构从"未成型"变为"成型"时播放一次玩家获得
 * 经验球的声音；成型结果中的框架外观与能源仓位置会同步到客户端，供 BER 绘制覆盖层。</p>
 *
 * <p><b>频道策略：</b>需求 = {@code channels.base + perPatternProviderCore × 样板供应器等价数量}；
 * 频道节点按端口容量<b>分块</b>连接，多根线缆分别承担一部分频道。矩阵不会因容量不足
 * 拒绝 AE 连接：AE 实际分配到多少频道，就按多少频道计算可用的样板供应器核心
 * （{@code max(0, linkedChannels - base) / perPatternProviderCore}）。超出部分的核心
 * 视为被过载，对应样板槽暂时不可用，矩阵本身仍保持连接。</p>
 *
 * <p><b>状态分层：</b>AE2 侧只使用 {@link #isFormed()} 这个“是否成型”布尔状态；
 * 外壳类型/等级、能源仓电压、内容方块与过滤器等详细信息保存在
 * {@link MatrixStructureInfo} 中，并以 {@value MatrixStructureInfo#TAG_ROOT} 子标签写入
 * 本方块实体的 NBT。</p>
 */
public class MatrixMonitorBlockEntity extends AEBaseBlockEntity implements MenuProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String TAG_FORMED = "gtai_formed";
    private static final String TAG_FRAME = "gtai_frame";
    private static final String TAG_HATCHES = "gtai_hatches";
    private static final String TAG_OVERVOLTAGE_HATCH = "gtai_overvoltage_hatch";
    private static final String TAG_PATTERNS = "gtai_patterns";
    private static final String TAG_PATTERN_SLOT = "slot";
    private static final String TAG_PATTERN_ITEM = "item";

    /** 单个监控器最多持久化的样板条目数，防止损坏/恶意 NBT 造成过大内存占用。 */
    private static final int MAX_SAVED_PATTERNS = 65536;

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

    /** 成型时电压过高但电量为 0 的能源仓；接电后立即爆炸。 */
    @Nullable
    private BlockPos pendingOvervoltageHatch;

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

    /**
     * AE 实际分配到的频道数（= 拿到频道的频道节点数量）。
     *
     * <p>矩阵不再因容量不足拒绝接入；链接成功多少频道就按多少频道计算可用的样板供应器：
     * 可用核心数 = max(0, linkedChannels - channels.base) / channels.perPatternProviderCore。
     * 超出部分的核心视为“被过载”，其样板槽暂不可用。</p>
     */
    private int linkedChannels;

    /** 频道节点与端口之间的连接分配已排队，避免成型时每块框架各重建一次。 */
    private boolean channelAssignmentScheduled;

    /** 实际链接数刷新已排队，把同一 tick 内大量 CHANNEL 状态回调合并成一次统计。 */
    private boolean linkedRefreshQueued;

    /** 延迟刷新已排队（20/60/120 tick 的稳定期刷新合并为一次；周期兜底仍在 serverTick）。 */
    private boolean linkedRefreshScheduled;

    /** 区块/方块实体刚加载时，强制让相邻线缆重新查询一次框架端口。 */
    private boolean revalidateFrames;

    /** 防止同一 tick 内重复调度整壳更新。 */
    private boolean shellRevalidationScheduled;

    /**
     * 从存档/区块恢复时的首次判定不播放声音，避免每次加载世界都响一次。
     * 玩家实际完成结构（方块变化触发判定）时不抑制。
     */
    private boolean suppressFormationSound;

    /** 读档后的首次有效成型不触发爆炸/音效；等结构判定稳定后清除。 */
    private boolean suppressFormationEffects;

    /**
     * 监控器中的样板库存。
     *
     * <p>用“绝对槽位 -> 样板”的稀疏映射保存：容量由结构中的等价样板供应器数量动态计算，
     * 容量缩小时已存样板不会被丢弃，只是暂时不可见/不可操作；容量恢复后重新出现。</p>
     */
    private final Map<Integer, ItemStack> patternStacks = new TreeMap<>();

    public MatrixMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(MatrixBlockEntities.MATRIX_MONITOR.get(), pos, state);
    }

    @Override
    public void onReady() {
        super.onReady();
        initialRefreshDone = true;
        // 加载后线缆可能先于/后于本监控器加载，强制重新检查一次框架端口与容量
        reconnectAttempted = false;
        revalidateFrames = true;
        if ((formed || structureInfo != null) && !initialValidationPending) {
            initialValidationPending = true;
            initialValidationRetries = 0;
            initialValidationRetryScheduled = false;
        }
        if (formed || structureInfo != null) {
            suppressFormationEffects = true;
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
        readSyncState(data);
        readPersistentState(data);
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
            suppressFormationEffects = formed || structureInfo != null;
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
        writeSyncState(tag);
        writePersistentState(tag);
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
                            monitor.refreshFormed(changed);
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
        return refreshFormed(null);
    }

    /**
     * 重新执行成型判定。
     *
     * @param changedPos 触发本次刷新的方块位置；{@code null} 表示强制重扫（读档、延迟重试等）。
     *                   已知位置时，远离当前结构边界的改动可以直接跳过：它不可能与外壳相邻，
     *                   也就不可能改变扫描边界或内部方块。
     */
    public boolean refreshFormed(@Nullable BlockPos changedPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return formed;
        }

        if (changedPos != null && formed && match != null && !initialValidationPending
                && !match.bounds().isWithinExpanded(changedPos, 1)) {
            return formed;
        }

        boolean wasFormed = formed;
        boolean forceRevalidate = revalidateFrames;
        boolean suppressEffects = suppressFormationEffects;
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
            updateOvervoltagePending(serverLevel, next, nextInfo);

            MatrixStructureInfo effectiveInfo = nextInfo != null ? nextInfo : structureInfo;
            int required = effectiveInfo == null ? GtaiConfig.channelBase() : effectiveInfo.requiredChannels();
            // 不再因容量不足拒绝接入：按结构需求建好全部频道节点，由 AE 决定实际链接多少。
            updateChannels(required);
            syncFrames(next);
            if (changed || forceRevalidate) {
                scheduleLinkedChannelRefresh(20);
            }

            // 成型/重载后，让整个外壳向周围发送方块更新：
            // 已存在的 AE 线缆会重新查询框架端口，避免“先放线缆后建矩阵”或重进世界后频道为 0。
            if (changed || forceRevalidate) {
                scheduleShellNeighborUpdates(next.layout());
                scheduleChannelRecovery();
                // syncFrames 可能在本 tick 内才建立端口连接；只在结构状态变化时再 repath
                repathChannelNetworks();
            }

            if (!wasFormed && !suppressFormationSound && !suppressEffects && nextInfo != null) {
                List<BlockPos> explosionTargets = MatrixFormationExplosion.findTargets(serverLevel, next, nextInfo);
                if (!explosionTargets.isEmpty()) {
                    scheduleTask(1, () -> {
                        if (!isRemoved() && level instanceof ServerLevel currentLevel) {
                            MatrixFormationExplosion.destroy(currentLevel, explosionTargets);
                            refreshFormed();
                        }
                    });
                }
            }
        } else {
            // 结构失效只断开 AE 和端口，数据保留到延迟清理时间
            recoveryScheduled = false;
            reconnectAttempted = false;
            recoveryToken++;
            setPendingOvervoltageHatch(null);
            clearChannels();
            clearFrames();
            scheduleDataClear();
        }

        if (next != null || !initialValidationPending) {
            suppressFormationEffects = false;
        }

        if (changed) {
            if (formed) {
                // 只在“从未成型到成型”时播放；结构类型切换（已经成型）不重复播放
                if (!wasFormed && !suppressFormationSound && !suppressEffects) {
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
     * 把频道节点按端口容量分块连接到端口。
     *
     * <p>第一轮按每个端口的容量填充，使多根线缆的容量分别承担一部分频道，避免旧实现
     * “所有节点连所有端口”时 AE 生成树把频道全压到一个入口。</p>
     *
     * <p>若容量估算不足以放下全部节点，剩余节点继续轮流挂到端口上，由 AE 自行决定
     * 哪些节点最终拿到频道——矩阵不拒绝接入，实际链接数由
     * {@link #refreshLinkedChannels()} 统计，超出的核心视为过载、样板槽不可用。</p>
     */
    private void assignChannelsToPorts() {
        disconnectAllPorts();
        if (!formed || channelNodes.isEmpty() || framePorts.isEmpty()) {
            return;
        }

        List<IGridNode> ports = new ArrayList<>(framePorts.keySet());
        ports.sort(Comparator.comparingLong(this::portOrder)
                .thenComparingInt(port -> port.getConnectedSides().stream()
                        .mapToInt(Direction::ordinal).min().orElse(Integer.MAX_VALUE)));

        Set<IGridNode> usedNeighbors = Collections.newSetFromMap(new IdentityHashMap<>());
        int[] capacities = new int[ports.size()];
        for (int i = 0; i < ports.size(); i++) {
            IGridNode neighbor = inWorldNeighbor(ports.get(i));
            if (neighbor == null || !usedNeighbors.add(neighbor)) {
                continue;
            }
            capacities[i] = Math.min(portChannelCapacity(ports.get(i)), nodeChannelCapacity(neighbor));
        }

        int next = 0;
        // 第一轮：按容量分块，尽量让每根线缆刚好承担自己的份额。
        for (int i = 0; i < ports.size() && next < channelNodes.size(); i++) {
            for (int slot = 0; slot < capacities[i] && next < channelNodes.size(); slot++) {
                connectChannel(ports.get(i), next++);
            }
        }
        // 第二轮：容量估算可能偏低/线缆尚未完成入网，剩余节点也挂上去让 AE 自己裁决。
        int cursor = 0;
        while (next < channelNodes.size()) {
            connectChannel(ports.get(cursor % ports.size()), next++);
            cursor++;
        }

        repathChannelNetworks();
        // AE 的实际分配要等路径计算完成；多次刷新覆盖控制器启动、分频等延迟。
        scheduleLinkedChannelRefresh(20);
        scheduleLinkedChannelRefresh(60);
        scheduleLinkedChannelRefresh(120);
    }

    private void connectChannel(IGridNode port, int index) {
        if (index < 0 || index >= channelNodes.size()) {
            return;
        }
        IGridNode channel = channelNodes.get(index).getNode();
        if (channel == null) {
            return;
        }
        try {
            channelConnections.add(GridHelper.createConnection(port, channel));
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to connect a matrix channel node at {} to its frame port", worldPosition, e);
        }
    }

    /** 合并同一 tick 内的多次端口变化，只重建一次频道分配。 */
    private void scheduleChannelAssignment() {
        if (channelAssignmentScheduled || !(level instanceof ServerLevel)) {
            return;
        }
        channelAssignmentScheduled = true;
        scheduleTask(1, () -> {
            channelAssignmentScheduled = false;
            if (!isRemoved() && formed) {
                assignChannelsToPorts();
            }
        });
    }

    /** 由框架在邻居变化时调用：线缆可能增删/更换，重排频道连接并刷新实际链接数。 */
    void requestChannelRecheck() {
        if (!formed) {
            return;
        }
        scheduleChannelAssignment();
        scheduleLinkedChannelRefresh(20);
    }

    private void scheduleLinkedChannelRefresh(int delay) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        if (delay <= 0) {
            refreshLinkedChannels();
            return;
        }
        if (linkedRefreshScheduled) {
            return;
        }
        linkedRefreshScheduled = true;
        scheduleTask(delay, () -> {
            linkedRefreshScheduled = false;
            refreshLinkedChannels();
        });
    }

    /**
     * 统计 AE 实际分配给矩阵的频道数。
     *
     * <p>监控器用它计算“可用的样板供应器核心”：
     * {@code (linkedChannels - channels.base) / channels.perPatternProviderCore}。
     * 超出实际链接数的核心不会让矩阵断开，只是对应数量的样板槽暂时不可用。</p>
     */
    private void refreshLinkedChannels() {
        if (isRemoved() || !formed) {
            return;
        }
        int linked = 0;
        for (IManagedGridNode managed : channelNodes) {
            IGridNode node = managed.getNode();
            if (node != null && node.getUsedChannels() > 0) {
                linked++;
            }
        }
        if (linked != linkedChannels) {
            linkedChannels = linked;
            setChanged();
        }
    }

    /**
     * 一个外部 AE 节点可向矩阵提供的频道数。
     *
     * <p>节点已入网时直接使用 {@code getMaxChannels()}，它会自动反映密集线缆、频道模式
     * （X2/X3/X4/无限）等规则；节点尚未入网时退回 8/32 的默认值。</p>
     */
    private static int nodeChannelCapacity(IGridNode node) {
        if (node.hasFlag(GridFlags.CANNOT_CARRY)) {
            return 0;
        }
        try {
            int max = node.getMaxChannels();
            if (max > 0) {
                return Math.min(MAX_CHANNEL_NODES, max);
            }
        } catch (RuntimeException ignored) {
            // 节点尚未入网：getMaxChannels 需要 grid，退回端口默认容量
        }
        return node.hasFlag(GridFlags.DENSE_CAPACITY) ? FRAME_PORT_CHANNEL_CAPACITY : 8;
    }

    /** 单个框架端口自身可承载的频道数（密集端口 32，频道模式放大时更高）。 */
    private static int portChannelCapacity(IGridNode port) {
        try {
            int max = port.getMaxChannels();
            if (max > 0) {
                return Math.min(MAX_CHANNEL_NODES, max);
            }
        } catch (RuntimeException ignored) {
            // 端口尚未入网时按密集端口默认值处理
        }
        return FRAME_PORT_CHANNEL_CAPACITY;
    }

    private long portOrder(IGridNode port) {
        Object owner = port.getOwner();
        return owner instanceof MatrixFrameBlockEntity frame ? frame.getBlockPos().asLong() : Long.MAX_VALUE;
    }

    @Nullable
    private static IGridNode inWorldNeighbor(IGridNode port) {
        for (IGridConnection connection : port.getInWorldConnections().values()) {
            IGridNode other = connection.getOtherSide(port);
            if (other != null) {
                return other;
            }
        }
        return null;
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
        // 样板属于玩家物品：清理前先掉落到监控器位置，避免数据保留期结束后凭空消失
        dropStoredPatterns();
        if (structureInfo != null) {
            setStructureInfo(null);
        }
        // 后续若增加材料缓存等持久数据，也必须在这里一起清理
        dataClearAt = -1L;
        infoLoadedFromDisk = false;
        setPendingOvervoltageHatch(null);
        onStructureDataCleared();
    }

    private void updateOvervoltagePending(ServerLevel serverLevel, MatrixStructureMatch match,
            @Nullable MatrixStructureInfo info) {
        if (info == null) {
            return;
        }
        if (MatrixFormationExplosion.isOvervoltage(match, info)) {
            BlockPos hatch = match.energyHatches().get(0);
            if (MatrixFormationExplosion.storedEnergy(serverLevel, hatch) <= 0L) {
                // 成型时无电：先打标记，后续接电由 checkPendingOvervoltage 处理
                setPendingOvervoltageHatch(hatch);
            }
            // 已有电量时不在这里清除旧标记：重进世界后可能由 checkPendingOvervoltage 引爆
        } else {
            setPendingOvervoltageHatch(null);
        }
    }

    private void setPendingOvervoltageHatch(@Nullable BlockPos pos) {
        BlockPos next = pos == null ? null : pos.immutable();
        if (Objects.equals(pendingOvervoltageHatch, next)) {
            return;
        }
        pendingOvervoltageHatch = next;
        setChanged();
        // 过压标记只影响服务端逻辑，不再触发方块更新包
    }

    /** 成型时因电量为 0 未爆炸的过压能源仓，一旦接电就立即引爆。 */
    private void checkPendingOvervoltage(ServerLevel serverLevel) {
        if (pendingOvervoltageHatch == null || !formed || match == null) {
            return;
        }
        BlockPos hatchPos = pendingOvervoltageHatch;
        if (!serverLevel.isLoaded(hatchPos)) {
            return;
        }
        if (!MatrixEnergyHatch.isEnergyHatch(serverLevel.getBlockState(hatchPos))) {
            setPendingOvervoltageHatch(null);
            return;
        }
        if (!match.energyHatches().contains(hatchPos)) {
            setPendingOvervoltageHatch(null);
            return;
        }
        if (MatrixFormationExplosion.storedEnergy(serverLevel, hatchPos) <= 0L) {
            return;
        }
        setPendingOvervoltageHatch(null);
        MatrixFormationExplosion.destroy(serverLevel, List.of(hatchPos));
        refreshFormed();
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
        // 每秒兜底刷新一次实际链接数：频道被其他机器抢占、路径重算等未必每次都触发节点回调。
        if (formed && serverLevel.getGameTime() % 20L == 0L) {
            refreshLinkedChannels();
        }
        checkPendingOvervoltage(serverLevel);
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
        // 结构详情只保存在存档中，客户端渲染不依赖它；后续 GUI 应使用菜单数据同步
    }

    /**
     * 按当前结构重建监控器侧的频道节点。
     *
     * <p>频道节点是普通的、非世界内节点，每个都带 {@code REQUIRE_CHANNEL}，因此
     * AE 会把它们各算作一个独立频道。框架端口节点本身不占频道，只负责把线缆的
     * 频道转发到这些节点。结构总频道数因此严格等于节点数量。</p>
     *
     * <p>节点本身不在这里绑定端口；实际连接由 {@link #assignChannelsToPorts()} 按
     * 每个端口的 8/32 容量分块建立。</p>
     */
    private void updateChannels(int required) {
        int requested = Math.max(0, required);
        int count = Math.min(requested, MAX_CHANNEL_NODES);
        if (requested == requiredChannels && channelNodes.size() == count) {
            return;
        }

        clearChannels();
        requiredChannels = requested;

        if (!(level instanceof ServerLevel serverLevel) || count == 0) {
            return;
        }

        for (int index = 0; index < count; index++) {
            createChannelNode(serverLevel, index);
        }

        // 具体连到哪些端口由 assignChannelsToPorts 决定；多出来的节点保持未连接。
        scheduleChannelAssignment();
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
        linkedChannels = 0;
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
        // 频道节点本身也可能落在旧的断开 grid 中；连同端口一起重建。
        // recreatePorts 会重新创建 in-world 端口，AE 随之重新扫描并建连；
        // 与旧实现“替换同种线缆 part”效果等价，但不会破坏线缆总线中的其它 part。
        clearChannels();
        updateChannels(required);
        for (MatrixFrameBlockEntity frame : frames) {
            frame.recreatePorts();
        }
        repathChannelNetworks();
        scheduleLinkedChannelRefresh(20);
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

        // 新端口出现后统一重排一次频道分配；同一 tick 的多块框架只排一次。
        scheduleChannelAssignment();
        repathChannelNetworks();
    }

    /** 框架端口的线缆连接发生变化后重新测量容量并重排频道。 */
    void onFramePortConnectionChanged() {
        if (!(level instanceof ServerLevel) || framePorts.isEmpty()) {
            return;
        }

        scheduleChannelAssignment();
        scheduleLinkedChannelRefresh(20);
    }

    /** 由框架方块实体在拆除/解绑时调用。 */
    void detachFramePort(IGridNode port) {
        if (port == null || framePorts.remove(port) == null) {
            return;
        }

        disconnectPort(port);
        scheduleChannelAssignment();
        scheduleLinkedChannelRefresh(20);
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
    private void syncFrames(MatrixStructureMatch match) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean forceRevalidate = revalidateFrames;
        revalidateFrames = false;
        List<BlockPos> previous = framePositions;

        // 验证阶段已经记录了框架位置；这里只需回读这些位置，不再遍历整个包围盒。
        List<BlockPos> current = new ArrayList<>();
        for (BlockPos pos : match.layout().positions(MatrixBlockRole.FRAME)) {
            BlockState state = serverLevel.getBlockState(pos);
            if (state.getBlock() instanceof MatrixFrameBlock) {
                current.add(pos);
            }
        }
        Set<BlockPos> currentSet = new HashSet<>(current);

        for (BlockPos pos : current) {
            BlockState state = serverLevel.getBlockState(pos);
            if (serverLevel.getBlockEntity(pos) instanceof MatrixFrameBlockEntity frame) {
                boolean changed = frame.bindToMonitor(worldPosition);
                if (changed || forceRevalidate) {
                    // 延后到下一 tick，避免在成型判定过程中触发监控器/线缆的递归刷新
                    serverLevel.getServer().execute(() -> {
                        if (!isRemoved() && level != null) {
                            level.updateNeighborsAt(pos, state.getBlock());
                        }
                    });
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
    private void scheduleShellNeighborUpdates(MatrixStructureLayout layout) {
        if (shellRevalidationScheduled || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        shellRevalidationScheduled = true;

        serverLevel.getServer().execute(() -> {
            shellRevalidationScheduled = false;
            if (isRemoved() || !(level instanceof ServerLevel currentLevel)) {
                return;
            }

            // 外壳位置同样来自验证快照，不再重新遍历整个包围盒。
            for (BlockPos pos : layout.shell()) {
                BlockState state = currentLevel.getBlockState(pos);
                currentLevel.updateNeighborsAt(pos, state.getBlock());
            }
        });
    }

    private void clearFrames() {
        if (level instanceof ServerLevel serverLevel) {
            // 端口可能来自尚未写入 framePositions 的框架（例如线缆先于同步查询），
            // 因此以 framePorts 的 owner 为准，避免留下无法回收的端口节点。
            Set<MatrixFrameBlockEntity> frames = new HashSet<>(framePorts.values());
            for (BlockPos pos : framePositions) {
                if (serverLevel.getBlockEntity(pos) instanceof MatrixFrameBlockEntity frame) {
                    frames.add(frame);
                }
            }
            for (MatrixFrameBlockEntity frame : frames) {
                frame.unbindFromMonitor();
            }
        }
        framePositions = List.of();
        framePorts.clear();
    }

    /** 当前结构要求的频道数；未成型时为 0。 */
    public int requiredChannels() {
        return requiredChannels;
    }

    /**
     * 是否存在“因频道不足而不可用”的样板供应器核心。
     *
     * <p>矩阵本身仍保持 AE 连接；只是实际链接的频道数少于结构需求，超出部分的运行能力
     * 降级（见 {@link #effectivePatternProviderCount()}）。样板存储容量不随此缩减。</p>
     */
    public boolean isChannelOverloaded() {
        // 完全没有链接时属于“未接入”，不算过载；部分链接但不足才是过载。
        return formed && linkedChannels > 0 && linkedChannels < requiredChannels;
    }

    /** 当前 AE 实际分配给矩阵的频道数。 */
    public int linkedChannels() {
        return linkedChannels;
    }

    private void onChannelNodeStateChanged() {
        // AE 可能在同一 tick 为大量节点回调 CHANNEL 状态；合并到下一 tick 统计一次，
        // 避免 O(N²) 的全量遍历。
        if (linkedRefreshQueued) {
            return;
        }
        linkedRefreshQueued = true;
        scheduleTask(1, () -> {
            linkedRefreshQueued = false;
            refreshLinkedChannels();
        });
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
        // 不再要求“全部频道节点都拿到频道”：部分链接时矩阵依然可用，只是可用核心数减少。
        if (!formed || initialValidationPending) {
            return false;
        }
        return requiredChannels <= 0 || linkedChannels > 0;
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
     * <p>这是服务端交互逻辑（样板槽、并行数、速度、能量等）的只读入口。
     * 结构详情不进入 {@link #getUpdateTag()}；客户端需要展示时应通过菜单数据同步，
     * 不要假设这里在客户端也有值。</p>
     */
    @Nullable
    public MatrixStructureInfo structureInfo() {
        return structureInfo;
    }


    /**
     * 从结构中的任意矩阵方块定位到唯一监控器。
     *
     * <p>框架与玻璃都可能被右击；玻璃没有方块实体，因此统一从成型判定结果里取
     * {@code monitorPos}，再回读监控器方块实体。只有结构已成型时才返回。</p>
     */
    @Nullable
    public static MatrixMonitorBlockEntity findFromStructure(Level level, BlockPos pos) {
        return MatrixMultiblocks.findMatchAt(level, pos)
                .map(match -> level.getBlockEntity(match.monitorPos()))
                .filter(MatrixMonitorBlockEntity.class::isInstance)
                .map(MatrixMonitorBlockEntity.class::cast)
                .filter(MatrixMonitorBlockEntity::isFormed)
                .orElse(null);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.gtai.matrix");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MatrixMenu(containerId, playerInventory, this);
    }

    /**
     * 当前矩阵族；服务端优先使用成型详情，其次使用结构定义。
     *
     * <p>客户端不接收成型详情，因此这里在客户端通常返回 {@code null}；GUI 的族限制
     * 使用菜单数据槽同步的族值，不依赖本方法。</p>
     */
    @Nullable
    public MatrixFamily matrixFamily() {
        if (structureInfo != null) {
            return structureInfo.shellType();
        }
        return match == null ? null : match.definition().family();
    }

    /**
     * 当前实际可用的样板供应器核心数（供后续配方执行器/状态展示使用）。
     *
     * <p>由 AE 实际分配到的频道数反推：
     * {@code max(0, linkedChannels - channels.base) / channels.perPatternProviderCore}。
     * 超出实际链接数的核心视为被过载，不计入可用核心；结果不会超过结构里真实的
     * 等价核心数。</p>
     *
     * <p>注意：样板 GUI 的存储容量按结构中“已存储的等价样板供应器数量”计算，
     * 不随频道链接数缩减；频道不足只影响后续运行/状态，不会让已有样板槽消失。</p>
     */
    public int effectivePatternProviderCount() {
        MatrixStructureInfo info = structureInfo;
        if (!formed || info == null) {
            return 0;
        }
        int structureProviders = info.patternProviderCount();
        int perProvider = GtaiConfig.channelPerPatternProvider();
        int base = GtaiConfig.channelBase();
        if (perProvider <= 0) {
            // 核心不占频道时，只要基础频道链接成功就全部可用。
            return linkedChannels >= base ? structureProviders : 0;
        }
        int providerChannels = Math.max(0, linkedChannels - base);
        return Math.min(structureProviders, providerChannels / perProvider);
    }

    /**
     * 样板总容量：{@code 每份等价样板供应器的槽数 × 结构中已存储的等价样板供应器数量}。
     *
     * <p>这是任务要求的存储容量定义，与 AE 当前是否链接到足够频道无关；频道不足只影响
     * 后续配方运行能力（见 {@link #effectivePatternProviderCount()}），不会缩减样板槽。</p>
     */
    public int patternCapacity() {
        MatrixStructureInfo info = structureInfo;
        if (!formed || info == null) {
            return 0;
        }
        long capacity = (long) info.patternProviderCount() * GtaiConfig.patternSlotsPerProvider();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, capacity));
    }

    /** 读取绝对槽位上的样板；没有时返回 {@link ItemStack#EMPTY}。 */
    public ItemStack getPatternStack(int index) {
        if (index < 0) {
            return ItemStack.EMPTY;
        }
        return patternStacks.getOrDefault(index, ItemStack.EMPTY);
    }

    /**
     * 写入绝对槽位的样板。
     *
     * <p>这是底层存储，不校验容量/族；槽位交互与网络包在写入前分别通过
     * {@link #mayPlacePattern(int, ItemStack)} 校验。样板固定只保存 1 个。</p>
     */
    public void setPatternStack(int index, ItemStack stack) {
        if (index < 0) {
            return;
        }
        if (stack == null || stack.isEmpty()) {
            if (patternStacks.remove(index) != null) {
                setChanged();
            }
            return;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        ItemStack previous = patternStacks.put(index, copy);
        if (previous == null || !ItemStack.matches(previous, copy)) {
            setChanged();
        }
    }

    /** 该绝对槽位当前是否允许放入这份样板。 */
    public boolean mayPlacePattern(int index, ItemStack stack) {
        if (index < 0 || index >= patternCapacity()) {
            return false;
        }
        MatrixFamily family = matrixFamily();
        return family != null && MatrixPatternRules.accepts(family, stack);
    }

    /** 当前库存中的样板条目数（包含容量缩小后暂时隐藏的条目）。 */
    public int usedPatternCount() {
        return patternStacks.size();
    }

    /**
     * 把库存中的全部样板掉落到监控器位置并清空库存。
     *
     * <p>监控器被破坏或结构数据保留期结束时调用，避免样板随方块实体消失而丢失。</p>
     */
    public void dropStoredPatterns() {
        if (patternStacks.isEmpty()) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            for (ItemStack stack : patternStacks.values()) {
                if (!stack.isEmpty()) {
                    Block.popResource(serverLevel, worldPosition, stack.copy());
                }
            }
        }
        patternStacks.clear();
        setChanged();
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
        suppressFormationEffects = false;
        channelAssignmentScheduled = false;
        linkedRefreshQueued = false;
        linkedRefreshScheduled = false;
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
        // 客户端只需要渲染信息；结构详情与过压标记不进入方块更新包，避免同步体积
        // 随结构内容增长（后续样板库存/运行状态也应走菜单数据同步，而不是这里）。
        writeSyncState(tag);
        return tag;
    }

    /**
     * 写入客户端同步需要的状态：是否成型、框架外观、能源仓位置。
     *
     * <p>存档与同步共用这些键；但结构详情、过压标记等只走 {@link #saveAdditional}。</p>
     */
    private void writeSyncState(CompoundTag tag) {
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
    }

    /** 写入只应存在于存档中的持久状态：结构详情与待引爆的过压能源仓。 */
    private void writePersistentState(CompoundTag tag) {
        if (pendingOvervoltageHatch != null) {
            tag.putLong(TAG_OVERVOLTAGE_HATCH, pendingOvervoltageHatch.asLong());
        } else {
            tag.remove(TAG_OVERVOLTAGE_HATCH);
        }

        if (structureInfo != null) {
            // 结构失效期间也保留数据，避免退出/重进时吞掉材料或缓存
            tag.put(MatrixStructureInfo.TAG_ROOT, structureInfo.save());
        } else {
            tag.remove(MatrixStructureInfo.TAG_ROOT);
        }

        ListTag patterns = new ListTag();
        for (Map.Entry<Integer, ItemStack> entry : patternStacks.entrySet()) {
            ItemStack stack = entry.getValue();
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag patternTag = new CompoundTag();
            patternTag.putInt(TAG_PATTERN_SLOT, entry.getKey());
            patternTag.put(TAG_PATTERN_ITEM, stack.save(new CompoundTag()));
            patterns.add(patternTag);
        }
        tag.put(TAG_PATTERNS, patterns);
    }

    /** 从存档或客户端同步 NBT 恢复渲染状态；不触发成型判定。 */
    private void readSyncState(CompoundTag data) {
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
    }

    /**
     * 从存档恢复持久状态；客户端同步包里没有这些键，因此客户端会得到空值。
     * 结构失效期间数据仍然保留，真正的清理由 {@link #clearStoredData()} 负责。
     */
    private void readPersistentState(CompoundTag data) {
        boolean clientSide = level != null && level.isClientSide;

        structureInfo = null;
        infoLoadedFromDisk = false;
        if (!clientSide) {
            if (data.contains(MatrixStructureInfo.TAG_ROOT, Tag.TAG_COMPOUND)) {
                structureInfo = MatrixStructureInfo.load(data.getCompound(MatrixStructureInfo.TAG_ROOT));
                infoLoadedFromDisk = structureInfo != null;
            }
            pendingOvervoltageHatch = data.contains(TAG_OVERVOLTAGE_HATCH, Tag.TAG_LONG)
                    ? BlockPos.of(data.getLong(TAG_OVERVOLTAGE_HATCH))
                    : null;
        } else {
            // 客户端同步包不包含持久数据；结构详情在客户端保持为空，过压标记也没有意义
            pendingOvervoltageHatch = null;
        }

        // 只有确实携带样板库存的标签才清空/重建；客户端同步包没有该键，不能借此清空
        // 客户端用于 GUI 显示的样板镜像。
        if (data.contains(TAG_PATTERNS, Tag.TAG_LIST)) {
            patternStacks.clear();
            ListTag patterns = data.getList(TAG_PATTERNS, Tag.TAG_COMPOUND);
            int count = Math.min(patterns.size(), MAX_SAVED_PATTERNS);
            for (int i = 0; i < count; i++) {
                CompoundTag patternTag = patterns.getCompound(i);
                int slot = patternTag.getInt(TAG_PATTERN_SLOT);
                if (slot < 0 || !patternTag.contains(TAG_PATTERN_ITEM, Tag.TAG_COMPOUND)) {
                    continue;
                }
                ItemStack stack = ItemStack.of(patternTag.getCompound(TAG_PATTERN_ITEM));
                if (!stack.isEmpty()) {
                    stack.setCount(1);
                    patternStacks.put(slot, stack);
                }
            }
            if (patterns.size() > MAX_SAVED_PATTERNS) {
                LOGGER.warn(
                        "Matrix monitor at {} has {} saved patterns, above the supported limit {}; the rest were ignored",
                        worldPosition, patterns.size(), MAX_SAVED_PATTERNS);
            }
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
