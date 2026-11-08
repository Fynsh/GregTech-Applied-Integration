package com.Fynsh.gtai.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import com.Fynsh.gtai.GTAIMod;
import com.Fynsh.gtai.menu.MatrixMenu;
import com.Fynsh.gtai.menu.MatrixPatternSlot;

/**
 * 矩阵样板 GUI。
 *
 * <p>背景来自 {@code assets/gtai/textures/gui/matrix_gui.png}；可见区域为 9×4 个样板槽，
 * 下方是玩家背包。滚动条、槽位高亮与鼠标滚轮/拖拽均在此实现。</p>
 *
 * <p>本阶段只实现样板页；信息页的构建/渲染入口以空方法预留，第二页可以直接在
 * {@link #initInformationPage()} / {@link #renderInformationPage(GuiGraphics, int, int, float)}
 * 中扩展，而不需要改动本页逻辑。</p>
 */
public class MatrixScreen extends AbstractContainerScreen<MatrixMenu> {

    private static final ResourceLocation TEXTURE = GTAIMod.id("textures/gui/matrix_gui.png");

    private static final int PATTERN_COLUMNS = MatrixMenu.PATTERN_COLUMNS;
    private static final int PATTERN_ROWS = MatrixMenu.PATTERN_ROWS;

    private static final int SCROLL_TRACK_X = 174;
    private static final int SCROLL_TRACK_Y = 30;
    private static final int SCROLL_TRACK_WIDTH = 13;
    private static final int SCROLL_TRACK_HEIGHT = 72;
    private static final int SCROLL_THUMB_INSET = 2;

    private boolean draggingScrollbar;

    public MatrixScreen(MatrixMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 195;
        this.imageHeight = 199;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        initInformationPage();
    }

    /**
     * 预留：信息 GUI 页面的初始化入口。
     *
     * <p>本阶段不实现任何内容；第二页可在这里添加按钮/文本/数据组件。</p>
     */
    protected void initInformationPage() {
        // no-op by design: the information page is a later stage
    }

    /**
     * 预留：信息 GUI 页面的渲染入口。
     *
     * <p>本阶段不调用；第二页实现切换后，可在 {@code render} 中按当前页调用本方法。</p>
     */
    protected void renderInformationPage(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // no-op by design: the information page is a later stage
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // AbstractContainerScreen 自身不会调用 renderTooltip；容器界面必须在 super.render
        // 之后显式调用，才能显示悬停物品的信息（与 InventoryScreen 的做法一致）。
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        // 容量之外的可见槽位画一层暗色遮罩，提示“这一格当前不可用”
        for (MatrixPatternSlot slot : menu.getPatternSlots()) {
            if (!slot.isActive()) {
                guiGraphics.fill(leftPos + slot.x, topPos + slot.y,
                        leftPos + slot.x + 16, topPos + slot.y + 16, 0x55000000);
            }
        }

        renderScrollbar(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, Component.translatable("gui.gtai.matrix.patterns"),
                10, 20, 0xFFFFFF, false);

        String count = menu.getUsedPatternCount() + " / " + menu.getCapacity();
        guiGraphics.drawString(this.font, count, this.imageWidth - 12 - this.font.width(count),
                20, 0x404040, false);

        guiGraphics.drawString(this.font, Component.translatable("gui.gtai.matrix.inventory"),
                8, 106, 0x404040, false);
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        int maxScroll = menu.maxScrollRow();
        if (maxScroll <= 0) {
            return;
        }

        int trackX = leftPos + SCROLL_TRACK_X;
        int trackY = topPos + SCROLL_TRACK_Y;
        int thumbHeight = scrollThumbHeight();
        int thumbY = trackY + (int) ((long) (SCROLL_TRACK_HEIGHT - thumbHeight) * menu.getScrollRow() / maxScroll);

        int innerX = trackX + SCROLL_THUMB_INSET;
        int innerRight = trackX + SCROLL_TRACK_WIDTH - SCROLL_THUMB_INSET;
        guiGraphics.fill(innerX, thumbY, innerRight, thumbY + thumbHeight, 0xFF9A9A9A);
        guiGraphics.fill(innerX, thumbY, innerRight, thumbY + 1, 0xFFD8D8D8);
        guiGraphics.fill(innerX, thumbY + thumbHeight - 1, innerRight, thumbY + thumbHeight, 0xFF555555);
    }

    private int scrollThumbHeight() {
        long rows = ((long) menu.getCapacity() + PATTERN_COLUMNS - 1) / PATTERN_COLUMNS;
        long totalRows = Math.max(PATTERN_ROWS, rows);
        long thumb = (long) SCROLL_TRACK_HEIGHT * PATTERN_ROWS / totalRows;
        return Mth.clamp((int) thumb, 12, SCROLL_TRACK_HEIGHT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isHoveringGui(mouseX, mouseY) || delta == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        scrollToRow(menu.getScrollRow() + (delta > 0.0D ? -1 : 1));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverScrollbar(mouseX, mouseY) && menu.maxScrollRow() > 0) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateScrollFromMouse(double mouseY) {
        int trackY = topPos + SCROLL_TRACK_Y;
        int thumbHeight = scrollThumbHeight();
        int span = SCROLL_TRACK_HEIGHT - thumbHeight;
        float progress = span <= 0 ? 0.0F
                : (float) ((mouseY - trackY - thumbHeight / 2.0D) / span);
        int row = Math.round(Mth.clamp(progress, 0.0F, 1.0F) * menu.maxScrollRow());
        scrollToRow(row);
    }

    private void scrollToRow(int row) {
        int target = Mth.clamp(row, 0, menu.maxScrollRow());
        if (target == menu.getScrollRow()) {
            return;
        }
        menu.setScrollRow(target);
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            // 绝对行号通过菜单按钮同步到服务端；服务端会再做一次边界钳制
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                    MatrixMenu.ABSOLUTE_SCROLL_BASE + target);
        }
    }

    private boolean isHoveringGui(double mouseX, double mouseY) {
        return mouseX >= leftPos && mouseX < leftPos + imageWidth
                && mouseY >= topPos && mouseY < topPos + imageHeight;
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        return mouseX >= leftPos + SCROLL_TRACK_X
                && mouseX < leftPos + SCROLL_TRACK_X + SCROLL_TRACK_WIDTH
                && mouseY >= topPos + SCROLL_TRACK_Y
                && mouseY < topPos + SCROLL_TRACK_Y + SCROLL_TRACK_HEIGHT;
    }
}
