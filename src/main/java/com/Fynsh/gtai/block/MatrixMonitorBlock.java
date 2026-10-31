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

import com.Fynsh.gtai.blockentity.MatrixMonitorBlockEntity;

/**
 * 矩阵监控器核心方块。
 *
 * <p>它是多方块结构的主方块，本阶段承担三件事：</p>
 * <ol>
 *   <li>提供 {@link MatrixMonitorBlockEntity}，作为后续交互/任务/网络逻辑的入口；</li>
 *   <li>在 AE2 让方块实体就绪、或相邻方块变化时触发一次成型判定；</li>
 *   <li>驱动监控器的服务端 tick，用于结构失效后的延迟数据清理。</li>
 * </ol>
 *
 * <p>成型判定本身在 {@link com.Fynsh.gtai.multiblock.MatrixMultiblocks} 中，与监控器无关；
 * 监控器只是调用方之一。</p>
 */
public class MatrixMonitorBlock extends MatrixBlock implements EntityBlock {

    public MatrixMonitorBlock(MatrixBlockSpec spec, Properties properties) {
        super(spec, properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MatrixMonitorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null
                : (tickLevel, pos, tickState, blockEntity) -> {
                    if (blockEntity instanceof MatrixMonitorBlockEntity monitor) {
                        monitor.serverTick();
                    }
                };
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        refreshFormed(level, pos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        // MatrixBlock.onPlace 会调用 refreshNear；这里再直接触发一次，避免方块实体刚创建时漏掉自己
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshFormed(level, pos);
    }

    private static void refreshFormed(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof MatrixMonitorBlockEntity monitor) {
            monitor.refreshFormed();
        }
    }
}
