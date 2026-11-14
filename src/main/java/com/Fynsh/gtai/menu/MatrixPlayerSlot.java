package com.Fynsh.gtai.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

/**
 * 玩家物品栏槽位。
 *
 * <p>信息页没有物品栏；本槽位通过 {@link #isActive()} 跟随菜单的“槽位隐藏”状态，
 * 让原版容器界面自动跳过渲染、悬停与高亮，而不需要修改父类的私有渲染方法。</p>
 */
public class MatrixPlayerSlot extends Slot {

    private final MatrixMenu menu;

    public MatrixPlayerSlot(MatrixMenu menu, Container container, int index, int x, int y) {
        super(container, index, x, y);
        this.menu = menu;
    }

    @Override
    public boolean isActive() {
        return !menu.areSlotsHidden();
    }
}
