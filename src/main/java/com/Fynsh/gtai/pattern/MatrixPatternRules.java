package com.Fynsh.gtai.pattern;

import net.minecraft.world.item.ItemStack;

import com.Fynsh.gtai.block.MatrixFamily;

/**
 * 矩阵族与编码样板种类之间的唯一映射。
 *
 * <p>方块族（{@link MatrixFamily}）与样板种类（{@link GtaiPatternKind}）在枚举上是一一对应的；
 * 这里把这层关系集中起来，避免 GUI、监控器和后续执行器各自写一份 switch。</p>
 *
 * <p>族限制只由目标槽位执行：{@link #accepts(MatrixFamily, ItemStack)} 返回 {@code false} 时，
 * 槽位拒绝放入即可，不需要额外取消玩家操作。</p>
 */
public final class MatrixPatternRules {

    private MatrixPatternRules() {
    }

    /**
     * 给定矩阵族，返回允许存入的样板种类；未知族返回 {@code null}。
     */
    public static GtaiPatternKind kindFor(MatrixFamily family) {
        if (family == null) {
            return null;
        }
        return switch (family) {
            case ASSEMBLY -> GtaiPatternKind.ASSEMBLY;
            case CIRCUIT_ASSEMBLY -> GtaiPatternKind.CIRCUIT_ASSEMBLY;
            case MATERIAL -> GtaiPatternKind.MATERIAL;
            case FLUID_MATERIAL -> GtaiPatternKind.FLUID_MATERIAL;
        };
    }

    /**
     * 该样板是否可以放入指定族的矩阵。
     *
     * <p>只接受本模组的四种编码样板；AE2 原生处理样板/合成样板不在此阶段的支持范围内。</p>
     */
    public static boolean accepts(MatrixFamily family, ItemStack stack) {
        if (family == null || stack == null || stack.isEmpty()) {
            return false;
        }
        if (!(stack.getItem() instanceof GtaiPatternItem patternItem)) {
            return false;
        }
        return patternItem.kind() == kindFor(family);
    }
}
