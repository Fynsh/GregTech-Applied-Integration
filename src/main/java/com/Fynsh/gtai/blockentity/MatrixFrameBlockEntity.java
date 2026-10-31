package com.Fynsh.gtai.blockentity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.implementations.parts.ICablePart;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.util.AECableType;
import appeng.blockentity.AEBaseBlockEntity;

import com.Fynsh.gtai.block.MatrixFrameBlock;
import com.Fynsh.gtai.multiblock.MatrixMultiblocks;
import com.Fynsh.gtai.multiblock.MatrixStructureMatch;
import com.Fynsh.gtai.util.TickTaskQueue;

/**
 * 矩阵框架的方块实体，只承担 AE 接入端口职责。
 *
 * <p>每个被线缆/总线访问的框架块会按需创建一个“端口节点”。端口节点本身不消耗
 * 频道，只负责把电缆传进来的频道转发给监控器管理的频道节点；因此一个结构无论
 * 接多少根线缆，结构总占用频道数仍由监控器侧的频道节点数决定。</p>
 *
 * <p>只有框架注册这个方块实体；玻璃等其它矩阵方块没有该能力，AE 无法连接。</p>
 */
public class MatrixFrameBlockEntity extends AEBaseBlockEntity implements IInWorldGridNodeHost {

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

    /** 加载/绑定后需要主动重试端口-线缆连接。 */
    private boolean retryPortsOnNextBind = true;

    private final TickTaskQueue scheduledTasks = new TickTaskQueue();
    private final Set<Direction> retryScheduled = EnumSet.noneOf(Direction.class);

    public MatrixFrameBlockEntity(BlockPos pos, BlockState state) {
        super(MatrixBlockEntities.MATRIX_FRAME.get(), pos, state);
    }

    @Override
    public void onReady() {
        super.onReady();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 区块加载后，若结构已经成型则提示相邻线缆重新检查本框架的端口。
        MatrixMonitorBlockEntity monitor = findMonitor();
        if (monitor != null && monitor.isFormed()) {
            retryPortsOnNextBind = true;
            bindToMonitor(monitor.getBlockPos());
            // 世界重进后主动探测已有线缆，避免线缆先于框架完成加载时错过 getGridNode 查询
            ensurePortsForNeighbors();
            serverLevel.getServer().execute(() -> {
                if (!isRemoved() && level != null) {
                    level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
                }
            });

            // 邻接线缆的 GridNode 可能比框架晚创建，稍后再探测一次并主动建立连接
            scheduleTask(20, () -> {
                if (!isRemoved() && level != null) {
                    ensurePortsForNeighbors();
                    for (Direction side : portNodes.keySet()) {
                        scheduleConnectionRetry(side, 0);
                    }
                }
            });
        }
    }

    /**
     * 由监控器在结构成型时调用；只记录监控器并接管已有端口，不在此处创建端口。
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

        if (!portNodes.isEmpty()) {
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
        }

        if (changed || portNodes.isEmpty()) {
            ensurePortsForNeighbors();
        }
        if (changed || retryPortsOnNextBind) {
            retryPortsOnNextBind = false;
            for (Direction side : portNodes.keySet()) {
                scheduleConnectionRetry(side, 0);
            }
        }
        return changed;
    }

    /**
     * 重建邻接线缆的 part 节点。
     *
     * <p>AE 的 CablePart 不会在邻居变化时自动重扫 GridNode；重载后如果线缆网络仍 0 频道，
     * 用同一种线缆物品替换一次 part，可强制线缆节点重新扫描并接回控制器网络。</p>
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void refreshNeighborCables() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (Direction side : portNodes.keySet()) {
            BlockEntity neighbor = serverLevel.getBlockEntity(worldPosition.relative(side));
            if (!(neighbor instanceof IPartHost host)) {
                continue;
            }
            for (Direction partSide : Direction.values()) {
                IPart part = host.getPart(partSide);
                if (part instanceof ICablePart && part.getPartItem() != null) {
                    // 用同一种线缆物品替换一次，强制该 part 重新扫描并接回控制器网络
                    host.replacePart((IPartItem) part.getPartItem(), partSide, null, null);
                }
            }
        }
        ensurePortsForNeighbors();
    }

    /** 强制重建全部端口；用于重载后端口已连上但 AE 路径没有分配到频道的恢复。 */
    public void recreatePorts() {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        for (Direction side : List.copyOf(portNodes.keySet())) {
            destroyPort(side);
            IManagedGridNode recreated = createPort(side);
            if (recreated != null && recreated.getNode() != null) {
                attachPort(recreated);
                tryConnectNeighbor(side);
                scheduleConnectionRetry(side, 0);
            }
        }
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
        if (level.getBlockState(worldPosition.relative(side)).getBlock() instanceof MatrixFrameBlock) {
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
        if (node != null) {
            attachedMonitor = monitor;
            monitor.attachFramePort(this, node);
        }
        // 先直接连接已有的邻接节点；AE 线缆不会因为方块更新自动重扫节点
        if (!tryConnectNeighbor(side)) {
            scheduleConnectionRetry(side, 0);
        }
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
        node.create(serverLevel, worldPosition);
        return node;
    }

    /**
     * 主动探测六个邻面是否已有 AE 节点，有则提前创建端口。
     *
     * <p>这样无论框架和线缆哪一方先加载，都不依赖线缆主动回调 {@link #getGridNode(Direction)}：
     * 框架先加载时会先把端口准备好，线缆后加载即可直接发现它。</p>
     */
    private void ensurePortsForNeighbors() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()
                || !(getBlockState().getBlock() instanceof MatrixFrameBlock)) {
            return;
        }

        MatrixMonitorBlockEntity monitor = findMonitor();
        if (monitor == null || !monitor.isFormed()) {
            return;
        }

        for (Direction side : Direction.values()) {
            if (portNodes.containsKey(side)) {
                continue;
            }
            BlockPos neighborPos = worldPosition.relative(side);
            if (serverLevel.getBlockState(neighborPos).getBlock() instanceof MatrixFrameBlock) {
                continue;
            }
            IGridNode neighbor = neighborNode(serverLevel, side);
            if (neighbor != null) {
                getGridNode(side);
                scheduleConnectionRetry(side, 0);
            }
        }
    }

    /**
     * 端口创建时邻接线缆的节点可能还没加载；短时间重试，等节点出现后重建端口，
     * 强制新的 InWorldGridNode 重新扫描并建立线缆连接。
     */
    private void scheduleTask(int delay, Runnable action) {
        if (level instanceof ServerLevel serverLevel) {
            scheduledTasks.schedule(serverLevel.getGameTime(), delay, action);
        }
    }

    /** 由 {@code MatrixFrameBlock} 的服务端 ticker 调用。 */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel) || scheduledTasks.isEmpty()) {
            return;
        }
        scheduledTasks.runDue(serverLevel.getGameTime());
    }

    private void scheduleConnectionRetry(Direction side, int attempt) {
        if (!(level instanceof ServerLevel) || attempt > 20 || !retryScheduled.add(side)) {
            return;
        }
        scheduleTask(5, () -> {
            retryScheduled.remove(side);
            retryConnection(side, attempt);
        });
    }

    private void retryConnection(Direction side, int attempt) {
        if (isRemoved() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // 这次探测本身会让邻接线缆/总线准备好自己的 GridNode（AE 的加载时序依赖它），
        // 结果不参与后续判断，因此只调用不保存返回值
        neighborNode(serverLevel, side);

        IManagedGridNode port = portNodes.get(side);
        if (port == null) {
            return;
        }

        if (tryConnectNeighbor(side)) {
            return;
        }

        if (neighborNode(serverLevel, side) != null) {
            // 仍无法连接时重建端口，让 InWorldGridNode 再扫描一次（重载时常见）
            destroyPort(side);
            IManagedGridNode recreated = createPort(side);
            if (recreated != null && recreated.getNode() != null) {
                attachPort(recreated);
            }
            if (attempt < 6) {
                scheduleConnectionRetry(side, attempt + 1);
            }
            return;
        }

        if (attempt < 20) {
            scheduleConnectionRetry(side, attempt + 1);
        }
    }

    /** 直接取邻接方块暴露的节点；比 getExposedNode 宽松，避免线缆加载时序导致取不到节点。 */
    @Nullable
    private IGridNode neighborNode(ServerLevel serverLevel, Direction side) {
        BlockEntity neighbor = serverLevel.getBlockEntity(worldPosition.relative(side));
        if (neighbor instanceof IInWorldGridNodeHost host) {
            return host.getGridNode(side.getOpposite());
        }
        return null;
    }

    /** 直接把端口和邻接节点连起来；逻辑连接同样能被 AE 频道寻路识别。 */
    private boolean tryConnectNeighbor(Direction side) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        IManagedGridNode port = portNodes.get(side);
        if (port == null || port.getNode() == null) {
            return false;
        }
        IGridNode node = port.getNode();
        IGridNode neighbor = neighborNode(serverLevel, side);
        if (neighbor == null || neighbor == node) {
            return false;
        }
        if (isConnected(node, neighbor)) {
            return true;
        }
        try {
            GridHelper.createConnection(node, neighbor);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isConnected(IGridNode node, IGridNode other) {
        for (IGridConnection connection : node.getConnections()) {
            if (connection.getOtherSide(node) == other) {
                return true;
            }
        }
        return false;
    }

    private void attachPort(IManagedGridNode port) {
        if (port == null || port.getNode() == null) {
            return;
        }
        MatrixMonitorBlockEntity monitor = findMonitor();
        if (monitor != null && monitor.isFormed()) {
            attachedMonitor = monitor;
            monitor.attachFramePort(this, port.getNode());
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
        if (!(level instanceof ServerLevel serverLevel) || portNodes.isEmpty()) {
            return;
        }

        List<Direction> stale = new ArrayList<>();
        for (Direction side : portNodes.keySet()) {
            BlockPos neighbor = worldPosition.relative(side);
            if (serverLevel.getBlockState(neighbor).getBlock() instanceof MatrixFrameBlock
                    || GridHelper.getExposedNode(serverLevel, neighbor, side.getOpposite()) == null) {
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
        node.destroy();
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
        retryScheduled.clear();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        destroyPorts();
        scheduledTasks.clear();
        retryScheduled.clear();
        super.onChunkUnloaded();
    }

}
