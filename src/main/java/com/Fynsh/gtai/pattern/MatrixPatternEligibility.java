package com.Fynsh.gtai.pattern;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;

import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.recipe.condition.CleanroomCondition;

import com.Fynsh.gtai.block.MatrixProcess;
import com.Fynsh.gtai.config.GtaiConfig;
import com.Fynsh.gtai.multiblock.MatrixMultiblocks;
import com.Fynsh.gtai.multiblock.MatrixStructureInfo;
import com.Fynsh.gtai.multiblock.MatrixVoltageTier;
import com.Fynsh.gtai.recipe.GtRecipeLookup;
import com.Fynsh.gtai.recipe.MatrixProcessMap;

/**
 * 判断“当前矩阵能否执行这条样板对应的配方”。
 *
 * <p>判定顺序刻意从“结构是否成立”开始，最后才落到配方本身：这样 GUI 可以把失败原因
 * 归一成一个枚举，不需要在界面层重复拼装规则。判定只看结构性条件：</p>
 * <ol>
 *   <li>矩阵已成型且有结构信息；</li>
 *   <li>样板是同族编码样板；</li>
 *   <li>样板记录的配方仍然存在，且配方类型能映射到矩阵处理类型；</li>
 *   <li>矩阵至少有一个该处理类型的、电压不高于矩阵等级的处理核心；</li>
 *   <li>配方若要求超净间，矩阵的超净间必须可用，且过滤器类型与配方要求的
 *       {@link CleanroomType} 严格一致；</li>
 *   <li>配方所需电压不高于矩阵有效等级。</li>
 * </ol>
 *
 * <p>临时状态（电量、AE 频道、线缆）不参与判定：它们由执行器在运行时单独判断，
 * 否则断电时整个样板页会整片变红，反而掩盖真正的配方问题。</p>
 */
public final class MatrixPatternEligibility {

    private MatrixPatternEligibility() {
    }

    /**
     * 判定一个样板槽在当前矩阵中的状态。
     *
     * @param level 服务端世界，用于查询配方表；客户端传 {@code null} 也可以，但会得到
     *              {@link MatrixPatternStatus#RECIPE_MISSING}
     * @param info  当前矩阵的结构信息；未成型时传 {@code null}
     * @param stack 样板槽里的物品
     */
    public static MatrixPatternStatus check(@Nullable Level level, @Nullable MatrixStructureInfo info,
            ItemStack stack) {
        if (info == null) {
            return MatrixPatternStatus.STRUCTURE_UNAVAILABLE;
        }
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof GtaiPatternItem item)) {
            return MatrixPatternStatus.NOT_PATTERN;
        }

        GtaiPatternKind expectedKind = MatrixPatternRules.kindFor(info.shellType());
        if (expectedKind == null || item.kind() != expectedKind) {
            return MatrixPatternStatus.FAMILY_MISMATCH;
        }

        ResourceLocation recipeTypeId = GtaiPatternData.recipeType(stack);
        ResourceLocation recipeId = GtaiPatternData.recipeId(stack);
        if (recipeTypeId == null || recipeId == null || level == null) {
            return MatrixPatternStatus.RECIPE_MISSING;
        }

        GTRecipe recipe = GtRecipeLookup.find(level, recipeTypeId, recipeId);
        if (recipe == null) {
            return MatrixPatternStatus.RECIPE_MISSING;
        }

        MatrixProcess process = MatrixProcessMap.of(recipe.getType());
        if (process == null || process.family() != info.shellType()) {
            return MatrixPatternStatus.PROCESS_UNSUPPORTED;
        }
        boolean accepted = MatrixMultiblocks.forFamily(info.shellType())
                .map(definition -> definition.acceptsProcess(process))
                .orElse(false);
        if (!accepted) {
            return MatrixPatternStatus.PROCESS_UNSUPPORTED;
        }

        // 结构信息里的处理核心已经按矩阵等级过滤过；这里只需要确认存在对应处理类型。
        boolean hasCore = info.processingCores().stream()
                .anyMatch(entry -> entry.process() == process && entry.count() > 0);
        if (!hasCore) {
            return MatrixPatternStatus.PROCESSING_CORE_MISSING;
        }

        List<CleanroomType> requiredCleanrooms = requiredCleanroomTypes(recipe);
        if (!requiredCleanrooms.isEmpty()) {
            if (!info.cleanroomUsable()) {
                return MatrixPatternStatus.CLEANROOM_UNAVAILABLE;
            }
            // 严格区分普通过滤器与无菌过滤器：两者不能互相替代。
            for (CleanroomType required : requiredCleanrooms) {
                if (!matchesCleanroomType(info.filterType(), required)) {
                    return MatrixPatternStatus.CLEANROOM_TYPE_MISMATCH;
                }
            }
        }

        // EU/t 等级只作为静态条件检查；运行时的电量不足由执行器暂停处理。
        try {
            int recipeTier = RecipeHelper.getRecipeEUtTier(recipe);
            if (recipeTier > MatrixVoltageTier.of(info.matrixLevel())) {
                return MatrixPatternStatus.VOLTAGE_TOO_LOW;
            }
        } catch (RuntimeException ignored) {
            // 配方 EU 数据异常时不额外阻塞，交给执行器在运行时报错。
        }

        if (!GtaiConfig.strictRecipeValidation()) {
            return MatrixPatternStatus.OK;
        }

        IPatternDetails details;
        try {
            details = PatternDetailsHelper.decodePattern(stack, level);
        } catch (RuntimeException e) {
            return MatrixPatternStatus.RECIPE_MISSING;
        }
        if (details == null) {
            return MatrixPatternStatus.RECIPE_MISSING;
        }

        // 输入侧不做严格匹配：JEI 编码样板时会去掉“不消耗”的物品（模具、催化剂等），
        // 因此样板输入天然可能是配方输入的子集；只保留产物白名单校验。
        if (!MatrixRecipeMatcher.matchesOutputs(recipe, details.getOutputs())) {
            return MatrixPatternStatus.RECIPE_MISMATCH;
        }

        return MatrixPatternStatus.OK;
    }

    /**
     * 配方要求的超净间类型；没有超净间条件时返回空列表。
     *
     * <p>GT 的 {@link CleanroomCondition} 允许 {@code cleanroom == null}，此时
     * {@code testCondition} 会直接通过（不要求超净间），因此这里也把它排除。</p>
     */
    public static List<CleanroomType> requiredCleanroomTypes(GTRecipe recipe) {
        if (recipe == null || recipe.conditions == null) {
            return List.of();
        }
        List<CleanroomType> types = new ArrayList<>();
        for (Object condition : recipe.conditions) {
            if (condition instanceof CleanroomCondition cleanroom) {
                CleanroomType type = cleanroom.getCleanroom();
                if (type != null) {
                    types.add(type);
                }
            }
        }
        return types;
    }

    /**
     * 矩阵的过滤器类型是否正好满足配方要求的超净间类型。
     *
     * <p>项目只有“普通过滤器”和“无菌过滤器”两种可用配置，因此这里按严格相等处理：
     * 普通过滤器只对应 {@link CleanroomType#CLEANROOM}，无菌过滤器只对应
     * {@link CleanroomType#STERILE_CLEANROOM}。混用/没有过滤器在
     * {@code cleanroom_usable} 阶段已经被判定为不可用，不会走到这里。</p>
     */
    public static boolean matchesCleanroomType(MatrixStructureInfo.FilterType filterType, CleanroomType required) {
        if (filterType == null || required == null) {
            return false;
        }
        if (required == CleanroomType.CLEANROOM) {
            return filterType == MatrixStructureInfo.FilterType.CLEANROOM;
        }
        if (required == CleanroomType.STERILE_CLEANROOM) {
            return filterType == MatrixStructureInfo.FilterType.STERILE;
        }
        // 自定义/未知 CleanroomType：矩阵没有对应的过滤器配置，按不满足处理。
        return false;
    }
}
