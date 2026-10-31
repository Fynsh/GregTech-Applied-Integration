package com.Fynsh.gtai.pattern;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.ProcessingPatternItem;

/**
 * 本项目的编码样板物品：{@link GtaiPatternKind} 的每个枚举值对应一个实例。
 *
 * <p>直接继承 AE2 的 {@link ProcessingPatternItem}：
 * <ul>
 *   <li>NBT 格式就是 AE2 原生处理样板格式，所以放进样板编码终端输出槽或 ME 样板供应器时，
 *       AE2 会按处理样板读取快照、显示原料与产物并正常发送材料，无需另写读取路径；</li>
 *   <li>它同时是 {@code EncodedPatternItem} 的子类，AE2 的 {@code AEPatternDecoder}
 *       会自动识别并调用下面的 {@link #decode(AEItemKey, Level)}。</li>
 * </ul>
 *
 * <p>唯一的额外行为是配方有效性校验：样板记录了配方来源，配方不存在时返回 {@code null}，
 * AE2 就会按原生逻辑把它显示为无效样板。</p>
 */
public class GtaiPatternItem extends ProcessingPatternItem {

    private final GtaiPatternKind kind;

    public GtaiPatternItem(GtaiPatternKind kind, Item.Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public GtaiPatternKind kind() {
        return kind;
    }

    @Nullable
    @Override
    public AEProcessingPattern decode(AEItemKey what, Level level) {
        if (what == null || !what.hasTag()) {
            return null;
        }

        // 配方已被删除／重载后不再存在 -> 返回 null，AE2 会把样板当作无效样板处理
        if (!GtaiPatternData.isRecipeAvailable(what, level)) {
            return null;
        }

        try {
            return new AEProcessingPattern(what);
        } catch (RuntimeException e) {
            // 与 AE2 原生处理样板一致：NBT 损坏时视为无法解码
            return null;
        }
    }

    @Nullable
    @Override
    public AEProcessingPattern decode(ItemStack stack, Level level, boolean tryRecovery) {
        return decode(AEItemKey.of(stack), level);
    }

    /** 供编码时复用：把配方来源写入刚生成的样板。 */
    public ItemStack encode(net.minecraft.resources.ResourceLocation recipeType,
            net.minecraft.resources.ResourceLocation recipeId,
            appeng.api.stacks.GenericStack[] inputs,
            appeng.api.stacks.GenericStack[] outputs) {
        ItemStack encoded = super.encode(inputs, outputs);
        GtaiPatternData.writeRecipeInfo(encoded, recipeType, recipeId);
        return encoded;
    }

    /** NBT 里的配方来源，仅用于日志／调试。 */
    @Nullable
    public static CompoundTag recipeTag(ItemStack stack) {
        return stack.getTag();
    }
}
