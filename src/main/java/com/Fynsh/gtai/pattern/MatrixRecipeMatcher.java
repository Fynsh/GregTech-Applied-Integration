package com.Fynsh.gtai.pattern;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;

/**
 * 样板产物与格雷配方产物的严格匹配。
 *
 * <p><b>输入侧刻意不校验。</b>JEI 编码样板时会去掉配方中“不消耗”的物品（模具、催化剂、
 * 容器等），所以样板输入天然可能只是配方输入的子集；拿样板输入去覆盖完整配方输入会把
 * 正常样板误判为无效。输入的正确性由 AE 编码流程与配方本身保证，执行器只按样板快照
 * 消耗/产出。</p>
 *
 * <p>产物侧保留白名单校验：样板产物必须是配方产物，且数量不超过配方上限，防止 NBT
 * 被修改的样板凭空生成物品。概率产物按配方最大产出计算。</p>
 */
public final class MatrixRecipeMatcher {

    private MatrixRecipeMatcher() {
    }

    /** 样板产物是否都是配方产物，且数量不超过配方上限。 */
    public static boolean matchesOutputs(GTRecipe recipe, GenericStack[] actual) {
        if (recipe == null || actual == null) {
            return false;
        }
        Map<AEKey, Long> caps = new HashMap<>();
        for (Content content : contents(recipe.outputs.get(ItemRecipeCapability.CAP))) {
            Object raw = content.getContent();
            if (raw instanceof ItemStack stack && !stack.isEmpty()) {
                AEItemKey key = AEItemKey.of(stack);
                if (key != null) {
                    caps.merge(key, (long) stack.getCount(), Long::sum);
                }
                continue;
            }
            if (raw instanceof Ingredient ingredient) {
                ItemStack[] stacks = ingredient.getItems();
                if (stacks.length == 0 || stacks[0].isEmpty()) {
                    continue;
                }
                AEItemKey key = AEItemKey.of(stacks[0]);
                if (key != null) {
                    caps.merge(key, itemAmount(ingredient), Long::sum);
                }
            }
        }
        for (Content content : contents(recipe.outputs.get(FluidRecipeCapability.CAP))) {
            Object raw = content.getContent();
            if (raw instanceof FluidStack stack && !stack.isEmpty()) {
                AEFluidKey key = AEFluidKey.of(stack);
                if (key != null) {
                    caps.merge(key, (long) stack.getAmount(), Long::sum);
                }
                continue;
            }
            if (raw instanceof FluidIngredient ingredient) {
                FluidStack[] stacks = ingredient.getStacks();
                if (stacks.length == 0 || stacks[0].isEmpty()) {
                    continue;
                }
                AEFluidKey key = AEFluidKey.of(stacks[0]);
                if (key != null) {
                    caps.merge(key, (long) Math.max(1, ingredient.getAmount()), Long::sum);
                }
            }
        }

        for (GenericStack stack : actual) {
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            Long cap = caps.get(stack.what());
            if (cap == null || stack.amount() > cap) {
                return false;
            }
        }
        return true;
    }

    private static List<Content> contents(List<Content> list) {
        return list == null ? List.of() : list;
    }

    private static long itemAmount(Ingredient ingredient) {
        if (ingredient instanceof SizedIngredient sized) {
            return Math.max(1, sized.getAmount());
        }
        if (ingredient instanceof IntProviderIngredient ranged) {
            // 配方加载时会采样一个数量，JEI/样板通常显示这个采样值。
            int sampled = ranged.getSampledCount();
            return Math.max(1, sampled > 0 ? sampled : ranged.getCountProvider().getMinValue());
        }
        return 1L;
    }
}
