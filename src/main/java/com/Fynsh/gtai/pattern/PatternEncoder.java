package com.Fynsh.gtai.pattern;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;

/**
 * 服务端执行的“原子编码”逻辑。
 *
 * <p>流程严格按需求顺序执行，任何一步失败都直接返回 {@code false}，此时既不会消费空白样板，
 * 也不会生成样板，更不会覆盖输出槽里已有的物品：</p>
 * <ol>
 *   <li>输出槽必须为空（避免覆盖已有样板造成物品损失）；</li>
 *   <li>空白样板槽里必须有空白样板；</li>
 *   <li>快照必须能构成合法的处理样板（AE2 原生 {@code ProcessingPatternItem#encode}）；</li>
 *   <li>生成结果必须能被正常解码，即对应配方当前确实存在；</li>
 *   <li>通过后才扣 1 个空白样板并把新样板放进输出槽。</li>
 * </ol>
 */
public final class PatternEncoder {

    private static final Logger LOGGER = LogUtils.getLogger();

    private PatternEncoder() {
    }

    public static boolean encode(PatternEncodingTermMenu menu, GtaiPatternKind kind, ResourceLocation recipeType,
            @Nullable ResourceLocation recipeId, List<GenericStack> inputs, List<GenericStack> outputs) {
        if (menu.isClientSide() || kind == null || recipeType == null || recipeId == null) {
            return false;
        }

        List<Slot> blankSlots = menu.getSlots(SlotSemantics.BLANK_PATTERN);
        List<Slot> encodedSlots = menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        if (blankSlots.isEmpty() || encodedSlots.isEmpty()) {
            return false;
        }

        Slot outputSlot = encodedSlots.get(0);
        // 输出槽必须是空的：宁可什么都不做，也不覆盖玩家已有的样板
        if (!outputSlot.getItem().isEmpty()) {
            return false;
        }

        Slot blankSlot = blankSlots.get(0);
        ItemStack blank = blankSlot.getItem();
        if (blank.isEmpty() || !AEItems.BLANK_PATTERN.isSameAs(blank)) {
            return false;
        }

        if (inputs.isEmpty() || inputs.size() > AEProcessingPattern.MAX_INPUT_SLOTS) {
            return false;
        }
        if (outputs.isEmpty() || outputs.size() > AEProcessingPattern.MAX_OUTPUT_SLOTS) {
            return false;
        }
        if (inputs.stream().anyMatch(java.util.Objects::isNull)) {
            return false;
        }
        // 第一个输出是主输出，AE2 的处理样板要求它必须存在
        if (outputs.get(0) == null) {
            return false;
        }

        GtaiPatternItem item = GtaiPatternItems.item(kind);
        if (item == null) {
            return false;
        }

        ItemStack encoded;
        try {
            encoded = item.encode(recipeType, recipeId,
                    inputs.toArray(new GenericStack[0]),
                    outputs.toArray(new GenericStack[0]));
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to build GT pattern {} for recipe {}", kind.path(), recipeId, e);
            return false;
        }

        // 最后一道校验：刚生成的样板必须可以正常解码（其中包含配方存在性检查），
        // 否则说明配方已失效或快照非法，直接放弃，不消费任何物品。
        Level level = menu.getPlayer().level();
        if (encoded.isEmpty() || PatternDetailsHelper.decodePattern(encoded, level) == null) {
            LOGGER.warn("Refusing to encode pattern {} for {}: the recipe could not be validated",
                    kind.path(), recipeId);
            return false;
        }

        // 原子提交
        blank.shrink(1);
        if (blank.getCount() <= 0) {
            blankSlot.set(ItemStack.EMPTY);
        }
        outputSlot.set(encoded);
        menu.broadcastChanges();
        return true;
    }
}
