package com.Fynsh.gtai.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.Fynsh.gtai.block.MatrixFamily;
import com.Fynsh.gtai.blockentity.MatrixMonitorBlockEntity;
import com.Fynsh.gtai.pattern.MatrixPatternRules;

/**
 * 矩阵监控器的样板 GUI 菜单。
 *
 * <p>样板库存保存在监控器方块实体中，菜单只负责：
 * <ul>
 *   <li>把 36 个可见槽位映射到“滚动行 × 9 + 列”的绝对样板槽位；</li>
 *   <li>通过数据槽同步容量、滚动行与矩阵族；</li>
 *   <li>把玩家背包点击/Shift/JEI 拖入等交互转成对监控器库存的读写。</li>
 * </ul>
 *
 * <p>族限制由 {@link MatrixPatternRules} 在槽位层执行；不匹配时只拒绝目标槽位。</p>
 */
public class MatrixMenu extends AbstractContainerMenu {

    public static final int PATTERN_COLUMNS = 9;
    public static final int PATTERN_ROWS = 4;
    public static final int PATTERN_SLOT_COUNT = PATTERN_COLUMNS * PATTERN_ROWS;

    /** 客户端通过菜单按钮发送“绝对滚动行”时使用的按钮 ID 基址。 */
    public static final int ABSOLUTE_SCROLL_BASE = 100;

    private static final int SCROLL_UP = 0;
    private static final int SCROLL_DOWN = 1;

    private static final int DATA_CAPACITY = 0;
    private static final int DATA_SCROLL = 1;
    private static final int DATA_FAMILY = 2;
    private static final int DATA_USED = 3;
    private static final int DATA_COUNT = 4;

    private final Inventory playerInventory;
    private final BlockPos monitorPos;
    @Nullable
    private final MatrixMonitorBlockEntity monitor;
    private final SimpleContainerData data = new SimpleContainerData(DATA_COUNT);
    private final List<MatrixPatternSlot> patternSlots = new ArrayList<>();

    /** 服务端打开菜单时使用。 */
    public MatrixMenu(int containerId, Inventory playerInventory, MatrixMonitorBlockEntity monitor) {
        super(MatrixMenus.MATRIX.get(), containerId);
        this.playerInventory = playerInventory;
        this.monitor = monitor;
        this.monitorPos = monitor.getBlockPos();
        initData();
        addSlots();
    }

    /** 客户端从网络包构造；方块实体应已由打开界面的流程加载。 */
    public MatrixMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    private MatrixMenu(int containerId, Inventory playerInventory, BlockPos monitorPos) {
        super(MatrixMenus.MATRIX.get(), containerId);
        this.playerInventory = playerInventory;
        this.monitorPos = monitorPos.immutable();
        this.monitor = playerInventory.player.level().getBlockEntity(monitorPos) instanceof MatrixMonitorBlockEntity m
                ? m
                : null;
        initData();
        addSlots();
    }

    private void initData() {
        data.set(DATA_CAPACITY, monitor == null ? 0 : monitor.patternCapacity());
        data.set(DATA_SCROLL, 0);
        data.set(DATA_FAMILY, familyCode(monitor == null ? null : monitor.matrixFamily()));
        data.set(DATA_USED, monitor == null ? 0 : monitor.usedPatternCount());
        addDataSlots(data);
    }

    private void addSlots() {
        for (int row = 0; row < PATTERN_ROWS; row++) {
            for (int col = 0; col < PATTERN_COLUMNS; col++) {
                MatrixPatternSlot slot = new MatrixPatternSlot(this, row * PATTERN_COLUMNS + col,
                        8 + col * 18, 31 + row * 18);
                patternSlots.add(slot);
                addSlot(slot);
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 117 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 175));
        }
    }

    private static int familyCode(@Nullable MatrixFamily family) {
        return family == null ? 0 : family.ordinal() + 1;
    }

    /** 当前矩阵族；客户端由菜单数据槽同步，服务端由监控器结构提供。 */
    @Nullable
    public MatrixFamily getFamily() {
        int code = data.get(DATA_FAMILY);
        MatrixFamily[] values = MatrixFamily.values();
        return code > 0 && code <= values.length ? values[code - 1] : null;
    }

    /** 当前样板总容量。 */
    public int getCapacity() {
        return Math.max(0, data.get(DATA_CAPACITY));
    }

    /** 当前滚动行（0 表示从第一行开始）。 */
    public int getScrollRow() {
        return Math.max(0, data.get(DATA_SCROLL));
    }

    /** 设置滚动行；客户端与服务器都使用同一套边界。 */
    public void setScrollRow(int row) {
        data.set(DATA_SCROLL, Mth.clamp(row, 0, maxScrollRow()));
    }

    /** 最大滚动行；可见 4 行，容量不足 4 行时为 0。 */
    public int maxScrollRow() {
        long rows = ((long) getCapacity() + PATTERN_COLUMNS - 1) / PATTERN_COLUMNS;
        long max = Math.max(0L, rows - PATTERN_ROWS);
        return (int) Math.min(Integer.MAX_VALUE, max);
    }

    /** 可见槽位对应的绝对样板槽位。 */
    public int getPatternIndex(int visibleIndex) {
        long index = (long) getScrollRow() * PATTERN_COLUMNS + visibleIndex;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, index));
    }

    /** 可见槽位当前是否落在容量内。 */
    public boolean isPatternSlotActive(int visibleIndex) {
        return visibleIndex >= 0 && visibleIndex < PATTERN_SLOT_COUNT
                && getPatternIndex(visibleIndex) < getCapacity();
    }

    public ItemStack getPatternStack(int patternIndex) {
        return monitor == null ? ItemStack.EMPTY : monitor.getPatternStack(patternIndex);
    }

    public void setPatternStack(int patternIndex, ItemStack stack) {
        if (monitor != null) {
            monitor.setPatternStack(patternIndex, stack);
        }
    }

    /** 该绝对槽位是否允许放入这份样板（族 + 容量）。 */
    public boolean mayPlacePattern(int patternIndex, ItemStack stack) {
        if (patternIndex < 0 || patternIndex >= getCapacity()) {
            return false;
        }
        MatrixFamily family = getFamily();
        return family != null && MatrixPatternRules.accepts(family, stack);
    }

    /**
     * 服务端处理 JEI 拖入：只允许放入空槽位，且必须通过族/容量校验。
     */
    public boolean setPatternFromClient(int patternIndex, ItemStack stack) {
        if (monitor == null || stack == null || stack.isEmpty()) {
            return false;
        }
        if (!getPatternStack(patternIndex).isEmpty() || !monitor.mayPlacePattern(patternIndex, stack)) {
            return false;
        }
        monitor.setPatternStack(patternIndex, stack);
        return true;
    }

    /** 当前库存中的样板数量（由服务端同步，包含容量缩小后暂时隐藏的条目）。 */
    public int getUsedPatternCount() {
        return Math.max(0, data.get(DATA_USED));
    }

    public List<MatrixPatternSlot> getPatternSlots() {
        return Collections.unmodifiableList(patternSlots);
    }

    @Override
    public void broadcastChanges() {
        if (monitor != null && !playerInventory.player.level().isClientSide) {
            data.set(DATA_CAPACITY, monitor.patternCapacity());
            data.set(DATA_FAMILY, familyCode(monitor.matrixFamily()));
            data.set(DATA_USED, monitor.usedPatternCount());
            int max = maxScrollRow();
            if (getScrollRow() > max) {
                data.set(DATA_SCROLL, max);
            }
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        int next;
        if (id >= ABSOLUTE_SCROLL_BASE) {
            next = id - ABSOLUTE_SCROLL_BASE;
        } else if (id == SCROLL_UP) {
            next = getScrollRow() - 1;
        } else if (id == SCROLL_DOWN) {
            next = getScrollRow() + 1;
        } else {
            return false;
        }
        setScrollRow(next);
        broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < PATTERN_SLOT_COUNT) {
            if (!this.moveItemStackTo(stack, PATTERN_SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, PATTERN_SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level().getBlockEntity(monitorPos) instanceof MatrixMonitorBlockEntity m)) {
            return false;
        }
        // 客户端可能还没收到本次成型同步；结构失效由服务端关闭菜单，避免客户端误关
        if (!player.level().isClientSide && !m.isFormed()) {
            return false;
        }
        return player.distanceToSqr(monitorPos.getX() + 0.5D, monitorPos.getY() + 0.5D, monitorPos.getZ() + 0.5D) <= 64.0D;
    }

    /** 槽位内容变化时通知监控器写入存档。 */
    public void onSlotChanged() {
        if (monitor != null) {
            monitor.setChanged();
        }
    }
}
