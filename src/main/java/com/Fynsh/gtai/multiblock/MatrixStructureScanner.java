package com.Fynsh.gtai.multiblock;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.Fynsh.gtai.block.MatrixBlock;
import com.Fynsh.gtai.config.GtaiConfig;

/**
 * 从一个结构方块出发，找出轴对齐长方体的六个边界。
 *
 * <p>合法结构是实心长方体，因此沿三个轴从锚点向两侧扩张，直到遇到非结构方块，
 * 就能得到包含外壳的完整边界。扩张最多走 {@link GtaiConfig#maxSize()} 格，避免在
 * 大片相连的矩阵方块上做无界扫描。</p>
 *
 * <p>锚点可以是外壳方块、内部核心，也可以是 GT 能源仓；成型判定本身不要求锚点
 * 是矩阵监控器。</p>
 */
public final class MatrixStructureScanner {

    private MatrixStructureScanner() {
    }

    public static Optional<MatrixStructureBounds> findBounds(Level level, BlockPos anchor) {
        if (!isStructureBlock(level, anchor)) {
            return Optional.empty();
        }

        final int limit = GtaiConfig.maxSize();
        final int x = anchor.getX();
        final int y = anchor.getY();
        final int z = anchor.getZ();

        int minX = x;
        while (minX > x - limit && isStructureBlock(level, new BlockPos(minX - 1, y, z))) {
            minX--;
        }
        int maxX = x;
        while (maxX < x + limit && isStructureBlock(level, new BlockPos(maxX + 1, y, z))) {
            maxX++;
        }

        int minY = y;
        while (minY > y - limit && isStructureBlock(level, new BlockPos(x, minY - 1, z))) {
            minY--;
        }
        int maxY = y;
        while (maxY < y + limit && isStructureBlock(level, new BlockPos(x, maxY + 1, z))) {
            maxY++;
        }

        int minZ = z;
        while (minZ > z - limit && isStructureBlock(level, new BlockPos(x, y, minZ - 1))) {
            minZ--;
        }
        int maxZ = z;
        while (maxZ < z + limit && isStructureBlock(level, new BlockPos(x, y, maxZ + 1))) {
            maxZ++;
        }

        return Optional.of(new MatrixStructureBounds(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ)));
    }

    /**
     * 结构方块：任意矩阵方块，或外部的 GT 能源仓。
     *
     * <p>内部核心、外壳框架/玻璃/过滤器都是 {@link MatrixBlock} 的子类；
     * 能源仓是 GT 方块，单独识别。</p>
     */
    public static boolean isStructureBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof MatrixBlock || MatrixEnergyHatch.isEnergyHatch(state);
    }
}
