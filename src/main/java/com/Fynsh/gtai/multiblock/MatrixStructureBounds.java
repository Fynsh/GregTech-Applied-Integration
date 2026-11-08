package com.Fynsh.gtai.multiblock;

import net.minecraft.core.BlockPos;

/**
 * 一个轴对齐长方体的六个边界。
 *
 * <p>{@code min} 与 {@code max} 都包含在结构内，因此尺寸为
 * {@code max - min + 1}，而不是差值。结构尺寸包含外壳。</p>
 */
public record MatrixStructureBounds(BlockPos min, BlockPos max) {

    public MatrixStructureBounds {
        min = min.immutable();
        max = max.immutable();
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) {
            throw new IllegalArgumentException("Matrix bounds min must not be greater than max");
        }
    }

    public int sizeX() {
        return max.getX() - min.getX() + 1;
    }

    public int sizeY() {
        return max.getY() - min.getY() + 1;
    }

    public int sizeZ() {
        return max.getZ() - min.getZ() + 1;
    }

    /** 该位置是否在当前结构边界内（含边界）。 */
    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /**
     * 该位置是否落在“边界向外扩张 {@code margin} 格”后的范围内。
     *
     * <p>用于刷新判定：比扩张范围更远的方块不可能与当前外壳相邻，也就不可能改变扫描
     * 边界或结构内部，可以安全地跳过本次重新校验。</p>
     */
    public boolean isWithinExpanded(BlockPos pos, int margin) {
        int m = Math.max(0, margin);
        return pos.getX() >= min.getX() - m && pos.getX() <= max.getX() + m
                && pos.getY() >= min.getY() - m && pos.getY() <= max.getY() + m
                && pos.getZ() >= min.getZ() - m && pos.getZ() <= max.getZ() + m;
    }

    /** 该格位于几个外表面上：0=内部，1=普通表面，2=棱的非顶点段，3=顶点。 */
    public int boundaryCount(BlockPos pos) {
        int count = 0;
        if (pos.getX() == min.getX() || pos.getX() == max.getX()) {
            count++;
        }
        if (pos.getY() == min.getY() || pos.getY() == max.getY()) {
            count++;
        }
        if (pos.getZ() == min.getZ() || pos.getZ() == max.getZ()) {
            count++;
        }
        return count;
    }
}
