package com.Fynsh.gtai.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 样板菜单中的可见槽位。
 *
 * <p>槽位号是固定的 0..35（用于菜单点击包），真正的样板索引由
 * {@link MatrixMenu#getPatternIndex(int)} 根据滚动行动态计算。这样滚动时不需要
 * 重建菜单槽位，只需要交换每个槽位当前映射到的绝对索引。</p>
 */
public class MatrixPatternSlot extends Slot {

    /** Slot 需要一个 Container 实例；实际读写全部委托给菜单，容器只用于满足父类签名。 */
    private static final Container DUMMY_CONTAINER = new SimpleContainer(1);

    private final MatrixMenu menu;
    private final int visibleIndex;

    public MatrixPatternSlot(MatrixMenu menu, int visibleIndex, int x, int y) {
        super(DUMMY_CONTAINER, visibleIndex, x, y);
        this.menu = menu;
        this.visibleIndex = visibleIndex;
    }

    /** 当前映射到的绝对样板索引。 */
    public int getPatternIndex() {
        return menu.getPatternIndex(visibleIndex);
    }

    @Override
    public ItemStack getItem() {
        if (!isActive()) {
            // 容量外或滚动窗口外的条目不应渲染；底层库存仍然保留，容量恢复后会重新出现
            return ItemStack.EMPTY;
        }
        return menu.getPatternStack(getPatternIndex());
    }

    @Override
    public void set(ItemStack stack) {
        menu.setPatternStack(getPatternIndex(), stack);
        // 不依赖调用方随后是否调用 setChanged；服务端需要在任何写入后标记存档
        menu.onSlotChanged();
    }

    @Override
    public void setChanged() {
        menu.onSlotChanged();
    }

    @Override
    public ItemStack remove(int amount) {
        if (!isActive()) {
            return ItemStack.EMPTY;
        }
        // 复制一份再拆分，避免直接修改监控器库存里被引用的 ItemStack
        ItemStack current = menu.getPatternStack(getPatternIndex()).copy();
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = current.split(amount);
        if (current.isEmpty()) {
            menu.setPatternStack(getPatternIndex(), ItemStack.EMPTY);
        } else {
            menu.setPatternStack(getPatternIndex(), current);
        }
        return split;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (!isActive() || !getItem().isEmpty()) {
            return false;
        }
        return menu.mayPlacePattern(getPatternIndex(), stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return isActive() && !getItem().isEmpty();
    }

    @Override
    public boolean isActive() {
        return menu.isPatternSlotActive(visibleIndex);
    }

    @Override
    public int getMaxStackSize() {
        // 编码样板是单件物品；槽位也按单件处理，避免 Shift/光标操作合并
        return 1;
    }

    /** 供快捷移动/调试使用的绝对槽位号。 */
    @Override
    public int getContainerSlot() {
        return getPatternIndex();
    }

    @Override
    public boolean isSameInventory(Slot other) {
        return this == other;
    }
}
