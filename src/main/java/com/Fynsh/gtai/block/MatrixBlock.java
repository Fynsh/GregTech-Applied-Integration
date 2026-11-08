package com.Fynsh.gtai.block;

import appeng.block.AEBaseBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import com.Fynsh.gtai.blockentity.MatrixMonitorBlockEntity;

/**
 * 矩阵方块。
 *
 * <p>继承 AE2 的 {@link AEBaseBlock}，因此 199 个方块都走 AE2 的方块基类，而不是
 * 格雷科技的多方块定义。它本身不携带多方块交互逻辑；保留独立类型是为了让成型判定
 * 通过 {@code instanceof MatrixBlock} 识别"是不是矩阵结构的一部分"，并从
 * {@link #spec()} 取回注册信息与结构语义。</p>
 *
 * <p>方块被放置/移除时通知附近已加载的监控器重新判定一次，避免"最后一块不是监控器
 * 的相邻方块时，监控器永远收不到变化"的问题。</p>
 */
public class MatrixBlock extends AEBaseBlock {

    private final MatrixBlockSpec spec;

    public MatrixBlock(MatrixBlockSpec spec, BlockBehaviour.Properties properties) {
        super(properties);
        this.spec = spec;
    }

    /** 这个方块的静态定义。 */
    public MatrixBlockSpec spec() {
        return spec;
    }

    /** 这个方块在结构中的语义，等价于 {@code spec().kind()}。 */
    public MatrixBlockKind kind() {
        return spec.kind();
    }

    /**
     * 右击矩阵框架或矩阵玻璃打开样板 GUI。
     *
     * <p>玻璃没有方块实体，因此统一从当前结构定位监控器；只有已成型结构才会打开。
     * 其它矩阵方块（核心、监控器、变压器等）保持原有行为。</p>
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        MatrixBlockRole role = kind().role();
        if (role != MatrixBlockRole.FRAME && role != MatrixBlockRole.GLASS) {
            return super.use(state, level, pos, player, hand, hit);
        }

        if (level.isClientSide) {
            // 实际开屏在服务端完成；只有客户端也认为结构成型时才消费右键，
            // 避免未成型时挡住正常的方块放置/使用。
            return MatrixMonitorBlockEntity.findFromStructure(level, pos) != null
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            MatrixMonitorBlockEntity monitor = MatrixMonitorBlockEntity.findFromStructure(level, pos);
            if (monitor != null) {
                NetworkHooks.openScreen(serverPlayer, monitor, buf -> buf.writeBlockPos(monitor.getBlockPos()));
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            MatrixMonitorBlockEntity.refreshNear(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide) {
            MatrixMonitorBlockEntity.refreshNear(level, pos);
        }
    }
}
