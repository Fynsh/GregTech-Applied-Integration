package com.Fynsh.gtai.integration.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import com.Fynsh.gtai.client.MatrixScreen;
import com.Fynsh.gtai.menu.MatrixPatternSlot;
import com.Fynsh.gtai.network.GtaiNetwork;
import com.Fynsh.gtai.network.SetPatternSlotPacket;

/**
 * JEI 拖入联动：把 JEI 的幽灵物品拖到矩阵样板槽中。
 *
 * <p>只对空槽位且通过容量/族校验的目标生成高亮；真正的写入由
 * {@link SetPatternSlotPacket} 交给服务端校验后完成。</p>
 */
public class MatrixGhostIngredientHandler implements IGhostIngredientHandler<MatrixScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(MatrixScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        if (ingredient.getType() != VanillaTypes.ITEM_STACK) {
            return List.of();
        }
        Object raw = ingredient.getIngredient();
        if (!(raw instanceof ItemStack stack) || stack.isEmpty()) {
            return List.of();
        }

        List<Target<I>> targets = new ArrayList<>();
        for (MatrixPatternSlot slot : screen.getMenu().getPatternSlots()) {
            if (!slot.isActive() || !slot.getItem().isEmpty()) {
                continue;
            }
            if (!screen.getMenu().mayPlacePattern(slot.getPatternIndex(), stack)) {
                continue;
            }
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    // JEI 的目标区域使用屏幕绝对坐标，需要叠加 GUI 左上角
                    return new Rect2i(screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y, 16, 16);
                }

                @Override
                public void accept(I value) {
                    if (value instanceof ItemStack item && !item.isEmpty()) {
                        GtaiNetwork.sendToServer(new SetPatternSlotPacket(screen.getMenu().containerId,
                                slot.getPatternIndex(), item));
                    }
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {
        // 无需在拖拽结束后做额外处理；写入由服务端确认
    }

    @Override
    public boolean shouldHighlightTargets() {
        return true;
    }
}
