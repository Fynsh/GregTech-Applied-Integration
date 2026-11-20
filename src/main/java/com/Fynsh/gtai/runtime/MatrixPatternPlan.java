package com.Fynsh.gtai.runtime;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.api.stacks.GenericStack;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;

import com.Fynsh.gtai.block.MatrixProcess;
import com.Fynsh.gtai.block.MatrixVoltage;
import com.Fynsh.gtai.config.GtaiConfig;
import com.Fynsh.gtai.multiblock.MatrixStructureInfo;
import com.Fynsh.gtai.multiblock.MatrixVoltageTier;
import com.Fynsh.gtai.pattern.MatrixPatternEligibility;
import com.Fynsh.gtai.pattern.MatrixPatternStatus;
import com.Fynsh.gtai.recipe.GtRecipeLookup;
import com.Fynsh.gtai.recipe.MatrixProcessMap;

/**
 * 一个样板槽在当前矩阵中的执行计划。
 *
 * <p>它是“可执行性判定”与“任务快照”之间的桥梁：状态来自
 * {@link MatrixPatternEligibility}（含产物白名单校验），配方、时长、EU/t、
 * 处理类型与电压等级在这里一次性取出，任务运行期间不再回读配方。</p>
 *
 * @param slot          绝对样板槽位
 * @param patternStack  样板物品（只保存 1 个）
 * @param details       解码后的 AE 处理样板；无效时为 {@code null}
 * @param recipe        当前配方表中的 GT 配方；无效时为 {@code null}
 * @param process       配方映射到的矩阵处理类型；无效时为 {@code null}
 * @param recipeTier    配方 EU/t 等级（GT 的 0=ULV、1=LV ...）
 * @param duration      未加速的配方时长（tick）
 * @param euPerTick     每 tick 消耗的 EU
 * @param expectedInputs 样板自身声明的输入快照
 * @param outputs       样板自身声明的产物快照
 * @param status        可执行性状态
 */
public record MatrixPatternPlan(
        int slot,
        ItemStack patternStack,
        @Nullable IPatternDetails details,
        @Nullable GTRecipe recipe,
        @Nullable MatrixProcess process,
        int recipeTier,
        int duration,
        long euPerTick,
        GenericStack[] expectedInputs,
        GenericStack[] outputs,
        MatrixPatternStatus status) {

    public MatrixPatternPlan {
        patternStack = patternStack == null ? ItemStack.EMPTY : patternStack.copy();
        expectedInputs = expectedInputs == null ? new GenericStack[0] : expectedInputs.clone();
        outputs = outputs == null ? new GenericStack[0] : outputs.clone();
        status = Objects.requireNonNull(status, "status");
    }

    public boolean executable() {
        return status.executable() && details != null && recipe != null && process != null;
    }

    /**
     * 在指定核心电压上运行的超频等级（0 = 不超频）。
     */
    public int overclockLevel(MatrixVoltage coreVoltage) {
        return Math.max(0, MatrixVoltageTier.of(coreVoltage) - recipeTier);
    }

    /**
     * 在指定核心电压上运行的每 tick EU；按 GT 标准超频，每级电压 ×4。
     */
    public long euFor(MatrixVoltage coreVoltage) {
        int oc = overclockLevel(coreVoltage);
        if (oc <= 0 || euPerTick <= 0L) {
            return euPerTick;
        }
        double scaled = euPerTick * Math.pow(OverclockingLogic.STD_VOLTAGE_FACTOR, oc);
        return scaled >= (double) Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1L, Math.round(scaled));
    }

    /**
     * 在指定核心电压上运行、并分配指定数量速度核心后的最终时长。
     *
     * <p>先按 GT 标准超频（每级时长 ×0.5），再按速度核心减少时长。</p>
     */
    public int durationFor(MatrixVoltage coreVoltage, int speedCores) {
        int oc = overclockLevel(coreVoltage);
        double scaled = duration;
        if (oc > 0) {
            scaled *= Math.pow(OverclockingLogic.STD_DURATION_FACTOR, oc);
        }
        if (speedCores > 0) {
            scaled *= Math.max(0.05D, 1.0D - GtaiConfig.speedCoreReduction() * speedCores);
        }
        long rounded = Math.max(1L, Math.round(scaled));
        return (int) Math.max(GtaiConfig.minDurationTicks(), Math.min(Integer.MAX_VALUE, rounded));
    }

    /**
     * 构建一个执行计划；任何一步失败都会返回带失败状态的计划，而不是抛异常。
     */
    public static MatrixPatternPlan create(Level level, @Nullable MatrixStructureInfo info, int slot,
            ItemStack stack) {
        MatrixPatternStatus status = MatrixPatternEligibility.check(level, info, stack);
        if (stack == null || stack.isEmpty() || !status.executable()) {
            return new MatrixPatternPlan(slot, stack, null, null, null, 0, 0, 0L,
                    new GenericStack[0], new GenericStack[0], status);
        }

        IPatternDetails details = null;
        try {
            details = appeng.api.crafting.PatternDetailsHelper.decodePattern(stack, level);
        } catch (RuntimeException ignored) {
            // 解码失败按无效样板处理
        }
        if (details == null) {
            return new MatrixPatternPlan(slot, stack, null, null, null, 0, 0, 0L,
                    new GenericStack[0], new GenericStack[0], MatrixPatternStatus.RECIPE_MISSING);
        }

        GTRecipe recipe = null;
        var recipeType = com.Fynsh.gtai.pattern.GtaiPatternData.recipeType(stack);
        var recipeId = com.Fynsh.gtai.pattern.GtaiPatternData.recipeId(stack);
        if (recipeType != null && recipeId != null) {
            recipe = GtRecipeLookup.find(level, recipeType, recipeId);
        }
        if (recipe == null) {
            return new MatrixPatternPlan(slot, stack, details, null, null, 0, 0, 0L,
                    new GenericStack[0], details.getOutputs(), MatrixPatternStatus.RECIPE_MISSING);
        }

        MatrixProcess process = MatrixProcessMap.of(recipe.getType());
        int recipeTier;
        try {
            recipeTier = RecipeHelper.getRecipeEUtTier(recipe);
        } catch (RuntimeException e) {
            recipeTier = 0;
        }
        long euPerTick = 0L;
        try {
            if (recipe.getInputEUt() != null) {
                euPerTick = Math.max(0L, recipe.getInputEUt().getTotalEU());
            }
        } catch (RuntimeException ignored) {
            // 配方 EU 数据异常按 0 处理
        }

        GenericStack[] expectedInputs = new GenericStack[0];
        if (details instanceof AEProcessingPattern processing && processing.getSparseInputs() != null) {
            expectedInputs = processing.getSparseInputs();
        }
        GenericStack[] outputs = details.getOutputs() == null ? new GenericStack[0] : details.getOutputs();

        return new MatrixPatternPlan(slot, stack, details, recipe, process, recipeTier,
                Math.max(1, recipe.duration), euPerTick, expectedInputs, outputs, status);
    }

    /** 配方 id，用于任务/信息页显示；没有配方时返回空串。 */
    public String recipeId() {
        if (recipe == null || recipe.getId() == null) {
            return "";
        }
        return recipe.getId().toString();
    }

    /** 供调试/日志使用的输入快照（过滤 null）。 */
    public List<GenericStack> inputList() {
        return java.util.Arrays.stream(expectedInputs).filter(java.util.Objects::nonNull).toList();
    }
}
