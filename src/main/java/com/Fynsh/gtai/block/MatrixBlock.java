package com.Fynsh.gtai.block;

import appeng.block.AEBaseBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

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
