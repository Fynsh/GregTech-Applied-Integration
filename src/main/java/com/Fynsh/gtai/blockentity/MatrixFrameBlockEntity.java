package com.Fynsh.gtai.blockentity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import appeng.blockentity.AEBaseBlockEntity;

import com.Fynsh.gtai.block.MatrixFrameBlock;
import com.Fynsh.gtai.multiblock.MatrixMultiblocks;
import com.Fynsh.gtai.multiblock.MatrixStructureMatch;
import com.Fynsh.gtai.util.TickTaskQueue;

/**
 * 矩阵框架的方块实体，只承担 AE 接入端口职责。
 *
 * <p>每个被线缆/总线访问的框架块会按面创建一个“端口节点”。端口节点本身不消耗
 * 频道，只负责把线缆传进来的频道转发给监控器管理的频道节点；因此一个结构无论
 * 接多少根线缆，结构总占用频道数仍由监控器侧的频道节点数决定。</p>
 *
 * <p><b>连接由 AE 自己建立，这里绝不手动建连。</b>端口是真正的 in-world 节点
 * （{@code setInWorldNode(true)} + {@code setExposedOnSides}），AE 的
 * {@code InWorldGridNode#findInWorldConnections} 会在节点创建/状态变化时主动扫描邻居并
 * 建立带方向的连接。旧实现额外调用 {@code GridHelper.createConnection(port, cable)}，
 * 而该 API 没有方向参数，建立的是非 in-world 连接；AE 的下一次扫描看不到它，于是对
 * 同一对节点再建一次连接，{@code GridConnection.create} 直接抛
 * “Connection between node [...] already exists”（历史服务端崩溃）。</p>
 *
 * <p>监控器侧的频道节点是非 in-world 节点，由监控器用 {@code GridHelper.createConnection}
 * 逻辑连接到端口；AE 的 in-world 扫描不会碰这一对节点，因此那条路径保持原样。</p>
 *
 * <p>只有框架注册这个方块实体；玻璃等其它矩阵方块没有该能力，AE 无法连接。</p>
 */
public class MatrixFrameBlockEntity extends AEBaseBlockEntity implements IInWorldGridNodeHost {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String TAG_MONITOR = "gtai_monitor";

    private static final IGridNodeListener<MatrixFrameBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(MatrixFrameBlockEntity owner, IGridNode node) {
            // 端口节点没有需要额外持久化的状态
        }

        @Override
        public void onStateChanged(MatrixFrameBlockEntity owner, IGridNode node, State state) {
            // 端口节点不消耗频道，状态变化无需处理
        }

        @Override
        public void onInWorldConnectionChanged(MatrixFrameBlockEntity owner, IGridNode node) {
            owner.onPortConnectionChanged();
        }
    };

    @Nullable
    private BlockPos monitorPos;

    /** 每个有电缆/总线的面各自一个端口，因此同一框架的多根线缆可以分别贡献频道容量。 */
    private final Map<Direction, IManagedGridNode> portNodes = new EnumMap<>(Direction.class);

    @Nullable
    private MatrixMonitorBlockEntity attachedMonitor;

    private final TickTaskQueue scheduledTasks = new TickTaskQueue();

    /** 同一 tick 内只安排一次邻居更新，避免成型/绑定阶段重复广播。 */
    private boolean revalidationScheduled;

    public MatrixFrameBlockEntity(BlockPos pos, BlockState state) {
        super(MatrixBlockEntities.MATRIX_FRAME.get(), pos, state);
    }

    @Override
    public void onReady() {
        super.onReady();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 区块加载后，若结构已经成型则接管已有端口，并让邻居重新查询本框架。
        MatrixMonitorBlockEntity monitor = findMonitor();
        if (monitor == null || !monitor.isFormed()) {
            return;
        }

        bindToMonitor(monitor.getBlockPos());

        // 线缆可能比框架晚加载：本 tick 结束和 20 tick 后再探测一次邻接节点。
        serverLevel.getServer().execute(() -> {
            if (!isRemoved() && level != null && ensurePortsForNeighbors()) {
                scheduleNeighborRevalidation();
            }
        });
        scheduleTask(20, () -> {
            if (!isRemoved() && level != null && ensurePortsForNeighbors()) {
                scheduleNeighborRevalidation();
            }
        });
    }

    /**
     * 由监控器在结构成型时调用：记录监控器、接管已有端口，并在必要时为已有线缆准备端口。
     *
     * @return 监控器位置是否发生变化（调用方据此决定是否触发相邻线缆重新扫描）
     */
    public boolean bindToMonitor(BlockPos monitorPos) {
        boolean changed = !monitorPos.equals(this.monitorPos);
        if (changed) {
            destroyPorts();
            this.monitorPos = monitorPos;
            setChanged();
        }

        MatrixMonitorBlockEntity monitor = findMonitor();
        if (monitor != null && monitor.isFormed()) {
            attachedMonitor = monitor;
            for (IManagedGridNode port : portNodes.values()) {
                IGridNode node = port.getNode();
                if (node != null) {
                    monitor.attachFramePort(this, node);
                }
            }
        }

        if (changed || portNodes.isEmpty()) {
            if (ensurePortsForNeighbors()) {
                scheduleNeighborRevalidation();
            }
        }
        return changed;
    }

    /** 结构失效时由监控器调用：清空监控器位置并断开端口。 */
    public void unbindFromMonitor() {
        if (monitorPos != null) {
            monitorPos = null;
            setChanged();
        }
        destroyPorts();
    }

    /**
     * 强制重建全部端口；用于重载后端口已连上但 AE 路径没有分配到频道的恢复。
     *
     * <p>重建端口会让 AE 重新执行一次 in-world 扫描并重新建立线缆连接，效果等同于
     * 旧实现“替换同种线缆 part”，但不会破坏线缆总线里的其它 part。</p>
     */
    public void recreatePorts() {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        for (Direction side : List.copyOf(portNodes.keySet())) {
            destroyPort(side);
            IManagedGridNode recreated = createPort(side);
            if (recreated != null) {
                attachPort(recreated);
            }
        }
        scheduleNeighborRevalidation();
    }

    /**
     * AE 线缆/总线询问本方块暴露的节点。
     *
     * <p>只有结构已成型且本块仍是框架时才创建端口；玻璃等方块没有本方块实体，
     * 因此永远不会被 AE 查询到这个方法。</p>
     */
    @Override
    public IGridNode getGridNode(Direction side) {
        if (isRemoved() || !(level instanceof ServerLevel)) {
            return null;
        }

        if (side == null || !(getBlockState().getBlock() instanceof MatrixFrameBlock)) {
            return null;
        }

        // 框架之间不互相连接：否则每块框架都会递归创建端口，且外壳内部会形成无意义的节点网。
        // 线缆/总线贴在框架上时，该方向上的邻居不是框架，因此仍能正常拿到节点。
        if (isFrameNeighbor(side)) {
            return null;
        }

        MatrixMonitorBlockEntity monitor = findMonitor();
        if (monitor == null || !monitor.isFormed()) {
            destroyPorts();
            return null;
        }

        // 频道数超过实现上限时结构永远无法接入，直接拒绝创建端口（真正的“禁止连接”）
        if (monitor.isChannelOverloaded() && monitor.channelNodeCount() == 0) {
            destroyPorts();
            return null;
        }

        IManagedGridNode port = portNodes.get(side);
        if (port == null) {
            port = createPort(side);
        }
        if (port == null) {
            return null;
        }

        IGridNode node = port.getNode();
        if (node == null) {
            return null;
        }

        attachedMonitor = monitor;
        monitor.attachFramePort(this, node);
        return node;
    }

    @Override
    public AECableType getCableConnectionType(Direction side) {
        // 与普通 AE 机器一致；实际可用频道上限由线缆类型与结构端口容量共同决定
        return AECableType.SMART;
    }

    @Nullable
    private IManagedGridNode createPort(Direction side) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        IManagedGridNode node = GridHelper.createManagedNode(this, NODE_LISTENER)
                .setInWorldNode(true)
                .setFlags(GridFlags.DENSE_CAPACITY)
                .setExposedOnSides(EnumSet.of(side))
                .setIdlePowerUsage(0)
                .setTagName("gtai_frame_port_" + side.getSerializedName())
                .setVisualRepresentation(getBlockState().getBlock());
        // 先放入 map，避免 create 期间线缆回调 getGridNode 时重复创建同一面的端口
        portNodes.put(side, node);
        // create() 内部会 markReady -> findInWorldConnections，由 AE 自己连接邻接线缆。
        // AE 的建连仍可能因节点竞争失败；这里兜底，避免单个端口把服务端 tick 打断。
        try {
            node.create(serverLevel, worldPosition);
        } catch (RuntimeException e) {
            portNodes.remove(side);
            try {
                node.destroy();
            } catch (RuntimeException ignored) {
                // 节点可能已在失败路径上被清理
            }
            LOGGER.warn("Failed to create matrix frame port at {} on {}", worldPosition, side, e);
            return null;
        }
        return node;
    }

    /**
     * 主动为已有邻接 AE 节点准备端口。
     *
     * <p>这样无论框架和线缆哪一方先加载，都不依赖线缆主动回调 {@link #getGridNode(Direction)}：
     * 端口创建时 AE 会扫描并连接邻居；线缆后加载时则会在自己的扫描里找到这个端口。</p>
     */
    private boolean ensurePortsForNeighbors() {
        if (!(level instanceof ServerLevel) || isRemoved()
                || !(getBlockState().getBlock() instanceof MatrixFrameBlock)) {
            return false;
        }

        MatrixMonitorBlockEntity monitor = findMonitor();
        if (monitor == null || !monitor.isFormed()) {
            return false;
        }

        boolean created = false;
        for (Direction side : Direction.values()) {
            if (portNodes.containsKey(side) || isFrameNeighbor(side)) {
                continue;
            }
            if (exposedNode(side) != null && getGridNode(side) != null) {
                created = true;
            }
        }
        return created;
    }

    /** 读取邻接方块在该面暴露的 AE 节点；只用于探测，不建立连接。 */
    @Nullable
    private IGridNode exposedNode(Direction side) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        // 邻居面向本框架的那一面是 side.getOpposite()
        return GridHelper.getExposedNode(serverLevel, worldPosition.relative(side), side.getOpposite());
    }

    private boolean isFrameNeighbor(Direction side) {
        return level != null && level.getBlockState(worldPosition.relative(side)).getBlock() instanceof MatrixFrameBlock;
    }

    /**
     * 端口创建/绑定后，让邻居在下一 tick 重新查询一次本框架。
     *
     * <p>端口创建本身已经让 AE 建立了连接；这里只保证“先有线缆、后成型”的时序下，
     * 线缆也会重新查询框架，而不是一直保留“无连接”的缓存。</p>
     */
    private void scheduleNeighborRevalidation() {
        if (revalidationScheduled) {
            return;
        }
        revalidationScheduled = true;
        scheduleTask(1, () -> {
            revalidationScheduled = false;
            if (!isRemoved() && level instanceof ServerLevel) {
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
        });
    }

    /** 由 {@code MatrixFrameBlock} 的服务端 ticker 调用。 */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel) || scheduledTasks.isEmpty()) {
            return;
        }
        scheduledTasks.runDue(serverLevel.getGameTime());
    }

    private void scheduleTask(int delay, Runnable action) {
        if (level instanceof ServerLevel serverLevel) {
            scheduledTasks.schedule(serverLevel.getGameTime(), delay, action);
        }
    }

    /** 由框架方块实体在端口建立后调用。 */
    private void attachPort(IManagedGridNode port) {
        IGridNode node = port.getNode();
        if (node == null) {
            return;
        }
        MatrixMonitorBlockEntity monitor = findMonitor();
        if (monitor != null && monitor.isFormed()) {
            attachedMonitor = monitor;
            monitor.attachFramePort(this, node);
        }
    }

    private void onPortConnectionChanged() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // 延后到本 tick 结束，等 AE 完成连接/断开后再重新评估容量
        serverLevel.getServer().execute(() -> {
            if (!isRemoved() && attachedMonitor != null) {
                attachedMonitor.onFramePortConnectionChanged();
            }
        });
    }

    /** 相邻方块变化时清理已经失去线缆/总线邻居的端口。 */
    public void refreshPorts() {
        if (!(level instanceof ServerLevel) || portNodes.isEmpty()) {
            return;
        }

        List<Direction> stale = new ArrayList<>();
        for (Direction side : portNodes.keySet()) {
            if (isFrameNeighbor(side) || exposedNode(side) == null) {
                stale.add(side);
            }
        }
        for (Direction side : stale) {
            destroyPort(side);
        }
    }

    private void destroyPort(Direction side) {
        IManagedGridNode node = portNodes.remove(side);
        if (node == null) {
            return;
        }
        IGridNode gridNode = node.getNode();
        if (attachedMonitor != null && gridNode != null) {
            attachedMonitor.detachFramePort(gridNode);
        }
        try {
            node.destroy();
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to destroy matrix frame port at {} on {}", worldPosition, side, e);
        }
    }

    private void destroyPorts() {
        for (Direction side : List.copyOf(portNodes.keySet())) {
            destroyPort(side);
        }
        attachedMonitor = null;
    }

    @Nullable
    private MatrixMonitorBlockEntity findMonitor() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        if (monitorPos != null) {
            BlockEntity blockEntity = serverLevel.getBlockEntity(monitorPos);
            if (blockEntity instanceof MatrixMonitorBlockEntity monitor) {
                return monitor;
            }
            if (serverLevel.isLoaded(monitorPos)) {
                // 监控器方块已不存在，允许下面重新扫描
                monitorPos = null;
                setChanged();
            } else {
                return null;
            }
        }

        MatrixStructureMatch match = MatrixMultiblocks.findMatchAt(serverLevel, worldPosition).orElse(null);
        if (match == null) {
            return null;
        }

        monitorPos = match.monitorPos();
        setChanged();
        BlockEntity blockEntity = serverLevel.getBlockEntity(monitorPos);
        return blockEntity instanceof MatrixMonitorBlockEntity monitor ? monitor : null;
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        if (data.contains(TAG_MONITOR, Tag.TAG_LONG)) {
            monitorPos = BlockPos.of(data.getLong(TAG_MONITOR));
        }
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        if (monitorPos != null) {
            data.putLong(TAG_MONITOR, monitorPos.asLong());
        }
    }

    @Override
    public void setRemoved() {
        destroyPorts();
        scheduledTasks.clear();
        revalidationScheduled = false;
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        destroyPorts();
        scheduledTasks.clear();
        revalidationScheduled = false;
        super.onChunkUnloaded();
    }
}
