package com.Fynsh.gtai.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.Fynsh.gtai.blockentity.MatrixFrameBlockEntity;

/**
 * 矩阵框架方块。
 *
 * <p>只有框架允许 AE 线缆/总线连接；玻璃、核心、过滤器等方块不提供
 * {@code IInWorldGridNodeHost}，因此 AE 无法把它们当作网络接入点。</p>
 *
 * <p>框架方块实体本身不保存频道，它只是一个“端口”：结构成型后由监控器把该端口
 * 接入结构中真正的频道节点。这样框架可以出现在外壳任意位置，而不会让每块框架
 * 各占用一个频道。</p>
 */
public class MatrixFrameBlock extends MatrixBlock implements EntityBlock {

    public MatrixFrameBlock(MatrixBlockSpec spec, Properties properties) {
        super(spec, properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MatrixFrameBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null
                : (tickLevel, pos, tickState, blockEntity) -> {
                    if (blockEntity instanceof MatrixFrameBlockEntity frame) {
                        frame.serverTick();
                    }
                };
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MatrixFrameBlockEntity frame) {
            // 线缆/总线被拆除时，框架不会收到方块实体生命周期回调，这里清理失效端口
            frame.refreshPorts();
        }
    }
}
