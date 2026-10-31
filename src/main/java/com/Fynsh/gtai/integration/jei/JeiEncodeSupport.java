package com.Fynsh.gtai.integration.jei;

import java.util.ArrayList;
import java.util.List;

import com.google.common.math.LongMath;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;

import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.integration.modules.jei.GenericEntryStackHelper;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;

/**
 * JEI “编写样板”联动用到的纯客户端辅助方法。
 *
 * <p>只负责：判断终端当前是否具备编码条件、把 JEI 配方页上的原料／产物转换成 AE2 的
 * {@link GenericStack} 快照（与 AE2 原生 JEI 编码使用同一套转换工具）。</p>
 *
 * <p>本类只在客户端 JEI 编码路径中使用（由 {@code gtai.mixins.json} 的 client 段加载），
 * 不会出现在服务端类加载路径上。</p>
 */
public final class JeiEncodeSupport {

    /** 与 AE2 编码前一致的判断：输出槽可用 + 空白样板槽里有空白样板。 */
    public static boolean canEncode(PatternEncodingTermMenu menu) {
        List<Slot> blankSlots = menu.getSlots(SlotSemantics.BLANK_PATTERN);
        List<Slot> encodedSlots = menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        if (blankSlots.isEmpty() || encodedSlots.isEmpty()) {
            return false;
        }
        // 输出槽必须为空，避免覆盖已有样板
        if (!encodedSlots.get(0).getItem().isEmpty()) {
            return false;
        }
        ItemStack blank = blankSlots.get(0).getItem();
        return !blank.isEmpty() && AEItems.BLANK_PATTERN.isSameAs(blank);
    }

    /** 收集 JEI 配方页的原料快照（同一物品/流体合并数量）。 */
    public static List<GenericStack> collectInputs(IRecipeSlotsView slotsView) {
        List<GenericStack> merged = new ArrayList<>();
        for (List<GenericStack> alternatives : GenericEntryStackHelper.ofInputs(slotsView)) {
            GenericStack chosen = null;
            for (GenericStack candidate : alternatives) {
                if (candidate != null) {
                    chosen = candidate;
                    break;
                }
            }
            if (chosen != null) {
                merge(merged, chosen);
            }
        }
        return merged;
    }

    /** 收集 JEI 配方页的产物快照。 */
    public static List<GenericStack> collectOutputs(IRecipeSlotsView slotsView) {
        List<GenericStack> result = new ArrayList<>();
        for (GenericStack stack : GenericEntryStackHelper.ofOutputs(slotsView)) {
            if (stack != null) {
                result.add(stack);
            }
        }
        return result;
    }

    private static void merge(List<GenericStack> merged, GenericStack stack) {
        for (int i = 0; i < merged.size(); i++) {
            GenericStack existing = merged.get(i);
            if (existing.what().equals(stack.what())) {
                merged.set(i, new GenericStack(existing.what(),
                        LongMath.saturatedAdd(existing.amount(), stack.amount())));
                return;
            }
        }
        merged.add(stack);
    }
}
