package com.Fynsh.gtai.recipe;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import com.Fynsh.gtai.block.MatrixProcess;
import com.Fynsh.gtai.pattern.GtaiPatternKind;

/**
 * 格雷配方类型 → 矩阵处理类型的唯一映射。
 *
 * <p>样板种类只负责“族”，具体要消耗哪一种处理核心由配方类型决定。这张表必须和
 * {@link com.Fynsh.gtai.pattern.GtaiPatternKind} 里声明的配方类型保持一致；两者不一致
 * 时，会出现“样板能编码、但矩阵永远缺处理核心”的情况，因此这里集中维护、并在
 * 注释里写清分组依据。</p>
 *
 * <p><b>来源说明：</b>项目原有代码与文档只分别声明了“材料配方类型列表”
 * （{@code GtaiPatternKind.MATERIAL}）和“3 种材料处理类型”（{@link MatrixProcess}），
 * <b>没有</b>给出两者之间的映射。本表是本次实现新增的约定，按机器加工方式分组：
 * 切割机、车床归“切削”，研磨机归“粉碎”，其余压模/锻造/冲压/打包/压缩/轧线/卷板归“成型”。
 * 若后续确认了其他分组，只改本类的静态表即可，判定与执行器会同时生效。</p>
 */
public final class MatrixProcessMap {

    private static final Map<GTRecipeType, MatrixProcess> BY_TYPE = new IdentityHashMap<>();

    static {
        put(MatrixProcess.ASSEMBLY, GTRecipeTypes.ASSEMBLER_RECIPES);
        put(MatrixProcess.CIRCUIT_ASSEMBLY, GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES);

        put(MatrixProcess.CUTTING, GTRecipeTypes.CUTTER_RECIPES);
        put(MatrixProcess.CUTTING, GTRecipeTypes.LATHE_RECIPES);
        put(MatrixProcess.PULVERIZING, GTRecipeTypes.MACERATOR_RECIPES);

        put(MatrixProcess.FORMING, List.of(
                GTRecipeTypes.EXTRUDER_RECIPES,
                GTRecipeTypes.FORGE_HAMMER_RECIPES,
                GTRecipeTypes.FORMING_PRESS_RECIPES,
                GTRecipeTypes.PACKER_RECIPES,
                GTRecipeTypes.COMPRESSOR_RECIPES,
                GTRecipeTypes.WIREMILL_RECIPES,
                GTRecipeTypes.BENDER_RECIPES));

        put(MatrixProcess.EXTRACTING, GTRecipeTypes.EXTRACTOR_RECIPES);
        put(MatrixProcess.SOLIDIFYING, GTRecipeTypes.FLUID_SOLIDFICATION_RECIPES);

        // 样板种类是配方类型的另一份声明；两边漂移会导致“能编码但永远缺核心”，
        // 因此在类初始化时直接失败，而不是等到玩家放样板时才显示红框。
        for (GtaiPatternKind kind : GtaiPatternKind.values()) {
            for (GTRecipeType type : kind.recipeTypes()) {
                if (!BY_TYPE.containsKey(type)) {
                    throw new IllegalStateException("Pattern " + kind.path()
                            + " declares GT recipe type " + type.registryName
                            + " but MatrixProcessMap has no matching process");
                }
            }
        }
    }

    private MatrixProcessMap() {
    }

    private static void put(MatrixProcess process, @Nullable GTRecipeType type) {
        if (type != null) {
            BY_TYPE.put(type, process);
        }
    }

    private static void put(MatrixProcess process, List<GTRecipeType> types) {
        for (GTRecipeType type : types) {
            put(process, type);
        }
    }

    /**
     * 配方类型对应的矩阵处理类型；未知类型返回 {@code null}。
     */
    @Nullable
    public static MatrixProcess of(@Nullable GTRecipeType type) {
        return type == null ? null : BY_TYPE.get(type);
    }
}
