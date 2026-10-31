package com.Fynsh.gtai.pattern;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEItemKey;

/**
 * 新样板 NBT 中记录的“配方来源”信息，用于配方失效检测。
 *
 * <p>处理样板本身只保存物品／流体快照，无法判断快照对应的配方是否还存在。这里额外记录
 * 配方类型与配方 id，解码时到 {@link net.minecraft.world.item.crafting.RecipeManager}
 * 里核对；配方不存在时解码返回 {@code null}，AE2 的原生机制会把该样板当作无效样板显示
 * （与合成样板找不到配方时的表现一致）。</p>
 */
public final class GtaiPatternData {

    /** 配方类型 id，例如 {@code gtceu:assembler}。 */
    public static final String TAG_RECIPE_TYPE = "gtai:recipe_type";

    /** 配方 id，例如 {@code gtceu:assembler/circuit_board}。 */
    public static final String TAG_RECIPE_ID = "gtai:recipe_id";

    private GtaiPatternData() {
    }

    /** 把配方来源写进已编码样板的 NBT。 */
    public static void writeRecipeInfo(ItemStack pattern, ResourceLocation recipeType, ResourceLocation recipeId) {
        CompoundTag tag = pattern.getOrCreateTag();
        if (recipeType != null) {
            tag.putString(TAG_RECIPE_TYPE, recipeType.toString());
        }
        if (recipeId != null) {
            tag.putString(TAG_RECIPE_ID, recipeId.toString());
        }
    }

    /**
     * 检查样板记录的配方是否仍然存在。
     *
     * <p>没有记录配方信息（例如旧存档中的样板）或当前没有世界上下文时一律视为有效，
     * 避免把这类样板误判成无效样板。</p>
     */
    public static boolean isRecipeAvailable(CompoundTag tag, Level level) {
        ResourceLocation recipeType = readId(tag, TAG_RECIPE_TYPE);
        ResourceLocation recipeId = readId(tag, TAG_RECIPE_ID);
        if (recipeType == null || recipeId == null || level == null) {
            return true;
        }
        return com.Fynsh.gtai.recipe.GtRecipeLookup.exists(level, recipeType, recipeId);
    }

    private static ResourceLocation readId(CompoundTag tag, String key) {
        if (!tag.contains(key, CompoundTag.TAG_STRING)) {
            return null;
        }
        return ResourceLocation.tryParse(tag.getString(key));
    }

    /** 便捷方法：判断一个已编码样板当前是否仍然有效。 */
    public static boolean isRecipeAvailable(AEItemKey key, Level level) {
        return key != null && key.hasTag() && isRecipeAvailable(key.getTag(), level);
    }
}
