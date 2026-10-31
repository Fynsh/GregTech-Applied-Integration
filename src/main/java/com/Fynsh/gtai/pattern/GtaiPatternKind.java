package com.Fynsh.gtai.pattern;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import com.Fynsh.gtai.GTAIMod;

/**
 * 本项目新增的编码样板类型。
 *
 * <p>每一种样板都以 AE2 原生处理样板（processing pattern）的 NBT 格式存储物品／流体快照，
 * 因此读取、显示与样板供应器发送材料的行为完全复用 AE2 原生逻辑；它们的区别只在物品
 * 类型与所接受的格雷科技配方类型。</p>
 *
 * <p><b>扩展约定：</b>新增一种样板只需要改本文件的枚举，并按约定补资源，不需要碰注册代码：</p>
 * <ol>
 *   <li>加一个枚举常量，构造参数是注册名路径，例如 {@code "xxx_processing_pattern"}；</li>
 *   <li>实现 {@link #recipeTypes()}，列出这种样板<b>独占</b>的格雷配方类型；</li>
 *   <li>补三份资源：{@code assets/gtai/textures/item/<path>.png}、
 *       {@code assets/gtai/models/item/<path>.json}、{@code lang} 里的 {@code item.gtai.<path>}。</li>
 * </ol>
 *
 * <p>物品注册（{@code GtaiPatternItems}）与"配方类型 → 样板"的反查索引
 * （{@code PatternTypeMapping}）都会遍历这里的枚举值自动生成，这两处无需改动。</p>
 *
 * <p>一条配方类型只能属于一种样板：若两个常量声明了同一个类型，
 * {@code PatternTypeMapping} 会保留先声明的那个并打印警告。</p>
 *
 * <p>这些样板<b>不会</b>被注册进 AE2 样板编码终端的任何"可编写样板类型"列表，
 * 只能通过 JEI 的"编写样板"联动生成。</p>
 */
public enum GtaiPatternKind {

    /** 组装编码样板：仅"组装机"（assembler）。 */
    ASSEMBLY("assembly_processing_pattern") {

        @Override
        public List<GTRecipeType> recipeTypes() {
            return List.of(GTRecipeTypes.ASSEMBLER_RECIPES);
        }
    },

    /**
     * 材料编码样板：压模器、锻造锤、冲压机床、研磨机、车床、打包机、压缩机、线材轧机、卷板机。
     *
     * <p>"研磨机：回收""研磨机：粉碎回收""研磨机：矿物处理"在 GTM 里不是独立的
     * {@code GTRecipeType}，而是 {@code MACERATOR_RECIPES} 下的子类别
     * （{@code GTRecipeCategories.MACERATOR_RECYCLING} / {@code ORE_CRUSHING} 等），
     * 父类型已声明，这些子类别自动覆盖。</p>
     */
    MATERIAL("material_processing_pattern") {

        @Override
        public List<GTRecipeType> recipeTypes() {
            return List.of(
                    GTRecipeTypes.EXTRUDER_RECIPES,
                    GTRecipeTypes.FORGE_HAMMER_RECIPES,
                    GTRecipeTypes.FORMING_PRESS_RECIPES,
                    GTRecipeTypes.MACERATOR_RECIPES,
                    GTRecipeTypes.LATHE_RECIPES,
                    GTRecipeTypes.PACKER_RECIPES,
                    GTRecipeTypes.COMPRESSOR_RECIPES,
                    GTRecipeTypes.WIREMILL_RECIPES,
                    GTRecipeTypes.BENDER_RECIPES);
        }
    },

    /**
     * 流体材料编码样板：提取机、流体固化器。
     *
     * <p>"提取机：重熔回收"同理属于 {@code EXTRACTOR_RECIPES} 的子类别，父类型已声明。</p>
     */
    FLUID_MATERIAL("fluid_material_processing_pattern") {

        @Override
        public List<GTRecipeType> recipeTypes() {
            return List.of(
                    GTRecipeTypes.EXTRACTOR_RECIPES,
                    // 注意：GTCEu 的常量拼写是 FLUID_SOLIDFICATION_RECIPES（上游拼写如此，非笔误）
                    GTRecipeTypes.FLUID_SOLIDFICATION_RECIPES);
        }
    },

    /**
     * 电路组装编码样板：<b>唯一</b>对应"电路组装机"（circuit assembler）。
     *
     * <p>与 {@link #ASSEMBLY} 是两条互不重叠的线：组装机配方仍然只生成组装编码样板，
     * 电路组装机配方只生成电路组装编码样板。</p>
     */
    CIRCUIT_ASSEMBLY("circuit_assembly_processing_pattern") {

        @Override
        public List<GTRecipeType> recipeTypes() {
            return List.of(GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES);
        }
    };

    private final String path;

    GtaiPatternKind(String path) {
        this.path = path;
    }

    /**
     * 本样板独占的格雷配方类型。
     *
     * <p>这是"样板 ↔ 配方类型"对应关系的<b>唯一</b>声明处：如需新增特殊配方样板，
     * 在这里返回相应类型即可。未在此列出的配方类型一律保持 AE2 原有编码行为
     * （仍生成 AE2 原生处理样板）。</p>
     *
     * <p>返回的列表必须不可变，且不能包含 {@code null}。</p>
     */
    public abstract List<GTRecipeType> recipeTypes();

    /** 注册名中的路径部分，例如 {@code assembly_processing_pattern}。 */
    public String path() {
        return path;
    }

    /** 完整的注册名，例如 {@code gtai:assembly_processing_pattern}。 */
    public ResourceLocation id() {
        return GTAIMod.id(path);
    }
}
