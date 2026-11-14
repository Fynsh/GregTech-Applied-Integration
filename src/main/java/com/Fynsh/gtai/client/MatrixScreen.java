package com.Fynsh.gtai.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import com.Fynsh.gtai.GTAIMod;
import com.Fynsh.gtai.block.MatrixFamily;
import com.Fynsh.gtai.menu.MatrixMenu;
import com.Fynsh.gtai.menu.MatrixPatternSlot;
import com.Fynsh.gtai.multiblock.MatrixInfoView;

/**
 * 矩阵监控器 GUI。
 *
 * <p>左侧页签栏固定显示“信息 / 样板”两个页签，可点击或按 Tab 切换。样板页保留原有的
 * 9×4 样板槽、滚动条与玩家物品栏；信息页使用无物品栏布局，整页用于可滚动的结构化信息，
 * 顶部标题固定不滚动。</p>
 *
 * <p>信息页只对扩展监控器核心开放：非扩展结构切到信息页时仅显示提示语，不渲染分类、
 * 条目或滚动条，左侧页签仍保留。信息页的数据来自 {@link MatrixMenu#getInfoView()}，
 * 该快照由打开包与刷新包同步，客户端不读取方块实体。</p>
 */
public class MatrixScreen extends AbstractContainerScreen<MatrixMenu> {

    private static final ResourceLocation TEXTURE = GTAIMod.id("textures/gui/matrix_gui.png");

    private static final int SIDEBAR_WIDTH = MatrixMenu.SIDEBAR_WIDTH;
    private static final int PANEL_WIDTH = 195;
    private static final int PANEL_HEIGHT = 199;
    private static final int TITLE_Y = 20;

    private static final int TAB_X = 2;
    private static final int TAB_WIDTH = 40;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_INFO_Y = 22;
    private static final int TAB_PATTERN_Y = 44;
    private static final int TAB_LABEL_Y_OFFSET = 5;

    private static final int PATTERN_COLUMNS = MatrixMenu.PATTERN_COLUMNS;
    private static final int PATTERN_ROWS = MatrixMenu.PATTERN_ROWS;

    private static final int SCROLL_TRACK_X = SIDEBAR_WIDTH + 174;
    private static final int SCROLL_TRACK_Y = 30;
    private static final int SCROLL_TRACK_WIDTH = 13;
    private static final int SCROLL_TRACK_HEIGHT = 72;
    private static final int SCROLL_THUMB_INSET = 2;

    private static final int INFO_CONTENT_X = SIDEBAR_WIDTH + 8;
    private static final int INFO_CONTENT_Y = 32;
    private static final int INFO_CONTENT_WIDTH = 168;
    private static final int INFO_CONTENT_HEIGHT = 158;
    private static final int INFO_SCROLL_X = INFO_CONTENT_X + INFO_CONTENT_WIDTH + 2;
    private static final int INFO_SCROLL_Y = INFO_CONTENT_Y;
    private static final int INFO_SCROLL_WIDTH = 10;
    private static final int INFO_SCROLL_HEIGHT = INFO_CONTENT_HEIGHT;
    private static final int INFO_SCROLL_THUMB_MIN = 12;

    private static final String KEY_TAB_INFO = "gui.gtai.matrix.tab.info";
    private static final String KEY_TAB_PATTERNS = "gui.gtai.matrix.tab.patterns";
    private static final String KEY_SIDEBAR_AE = "gui.gtai.matrix.sidebar.ae";
    private static final String KEY_SIDEBAR_AE_CONNECTED = "gui.gtai.matrix.sidebar.ae.connected";
    private static final String KEY_SIDEBAR_AE_DISCONNECTED = "gui.gtai.matrix.sidebar.ae.disconnected";
    private static final String KEY_SIDEBAR_AE_UNKNOWN = "gui.gtai.matrix.sidebar.ae.unknown";
    private static final String KEY_SIDEBAR_TAB_HINT = "gui.gtai.matrix.sidebar.tab_hint";
    private static final String KEY_SIDEBAR_TAB_HINT_ACTION = "gui.gtai.matrix.sidebar.tab_hint_action";
    private static final String KEY_INFO_TITLE = "gui.gtai.matrix.info.title";
    private static final String KEY_REQUIRES_EXTENDED = "gui.gtai.matrix.info.requires_extended";
    private static final String KEY_FAMILY_PREFIX = "gui.gtai.matrix.family.";
    private static final String KEY_FAMILY_UNKNOWN = "gui.gtai.matrix.family.unknown";

    private final MatrixInfoPage infoPageRenderer = new MatrixInfoPage();

    private boolean infoPage;
    private int infoScroll;
    private boolean draggingInfoScrollbar;
    /** 样板页滚动条拖拽状态。 */
    private boolean draggingScrollbar;

    public MatrixScreen(MatrixMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = SIDEBAR_WIDTH + PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        applySlotVisibility();
        initInformationPage();
    }

    /** 信息页使用菜单同步的快照构建文本；初始化时先建一次，首帧不会空白。 */
    protected void initInformationPage() {
        infoPageRenderer.invalidate();
        ensureInfoCache();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (infoPage) {
            // 信息页不渲染槽位；清掉悬停槽，避免数字键等在切页后误交换物品
            this.hoveredSlot = null;
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        drawSidebar(guiGraphics);
        drawTabs(guiGraphics);

        if (infoPage) {
            drawInfoPanel(guiGraphics);
            renderInformationPage(guiGraphics, mouseX, mouseY, partialTick);
        } else {
            guiGraphics.blit(TEXTURE, leftPos + SIDEBAR_WIDTH, topPos, 0, 0,
                    PANEL_WIDTH, PANEL_HEIGHT, 256, 256);

            // 容量之外的可见槽位画一层暗色遮罩，提示“这一格当前不可用”
            for (MatrixPatternSlot slot : menu.getPatternSlots()) {
                if (!slot.isActive()) {
                    guiGraphics.fill(leftPos + slot.x, topPos + slot.y,
                            leftPos + slot.x + 16, topPos + slot.y + 16, 0x55000000);
                }
            }
            renderScrollbar(guiGraphics);
        }
    }

    /** 信息页内容：非扩展监控器只显示提示语，扩展监控器显示可滚动的分区内容。 */
    protected void renderInformationPage(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        MatrixInfoView view = menu.getInfoView();
        if (view == null) {
            return;
        }
        if (!view.monitorExtended()) {
            renderExtendedRequired(guiGraphics, view);
            return;
        }

        ensureInfoCache();
        infoScroll = Mth.clamp(infoScroll, 0, maxInfoScroll());

        int x = leftPos + INFO_CONTENT_X;
        int y = topPos + INFO_CONTENT_Y;
        guiGraphics.enableScissor(x, y, x + INFO_CONTENT_WIDTH, y + INFO_CONTENT_HEIGHT);
        infoPageRenderer.render(guiGraphics, font, x, y, INFO_CONTENT_HEIGHT, infoScroll);
        guiGraphics.disableScissor();
        renderInfoScrollbar(guiGraphics);
    }

    private void renderExtendedRequired(GuiGraphics guiGraphics, MatrixInfoView view) {
        MatrixFamily family = view.family();
        Component familyName = family == null
                ? Component.translatable(KEY_FAMILY_UNKNOWN)
                : Component.translatable(KEY_FAMILY_PREFIX + family.id());
        Component message = Component.translatable(KEY_REQUIRES_EXTENDED, familyName);

        List<FormattedCharSequence> parts = font.split(message, INFO_CONTENT_WIDTH - 8);
        int totalHeight = parts.size() * 10;
        int y = topPos + INFO_CONTENT_Y + (INFO_CONTENT_HEIGHT - totalHeight) / 2;
        for (FormattedCharSequence part : parts) {
            String text = MatrixInfoPage.plainText(part);
            int x = leftPos + INFO_CONTENT_X + (INFO_CONTENT_WIDTH - font.width(text)) / 2;
            guiGraphics.drawString(font, text, x, y, 0x404040, false);
            y += 10;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        drawTabLabels(guiGraphics);
        drawSidebarHints(guiGraphics);

        if (infoPage) {
            guiGraphics.drawString(font, Component.translatable(KEY_INFO_TITLE),
                    INFO_CONTENT_X, TITLE_Y, 0x404040, false);
            return;
        }

        guiGraphics.drawString(font, Component.translatable("gui.gtai.matrix.patterns"),
                SIDEBAR_WIDTH + 10, TITLE_Y, 0xFFFFFF, false);

        String count = menu.getUsedPatternCount() + " / " + menu.getCapacity();
        guiGraphics.drawString(font, count, imageWidth - 12 - font.width(count), TITLE_Y, 0x404040, false);

        guiGraphics.drawString(font, Component.translatable("gui.gtai.matrix.inventory"),
                SIDEBAR_WIDTH + 8, 106, 0x404040, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (infoPage) {
            return;
        }
        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (infoPage) {
            if (!isHoveringGui(mouseX, mouseY) || delta == 0.0D) {
                return super.mouseScrolled(mouseX, mouseY, delta);
            }
            int max = maxInfoScroll();
            if (max <= 0) {
                return true;
            }
            int step = Math.max(1, (int) Math.round(Math.abs(delta) * 12.0D));
            infoScroll = Mth.clamp(infoScroll + (delta > 0.0D ? -step : step), 0, max);
            return true;
        }

        if (!isHoveringPanel(mouseX, mouseY) || delta == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        scrollToRow(menu.getScrollRow() + (delta > 0.0D ? -1 : 1));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handleTabClick(mouseX, mouseY)) {
            return true;
        }

        if (infoPage) {
            if (button == 0 && isOverInfoScrollbar(mouseX, mouseY) && maxInfoScroll() > 0) {
                draggingInfoScrollbar = true;
                updateInfoScrollFromMouse(mouseY);
                return true;
            }
            // 信息页没有槽位；吞掉面板内的点击，避免落到下方的隐藏槽位
            if (isHoveringGui(mouseX, mouseY)) {
                return true;
            }
        }

        if (button == 0 && isOverScrollbar(mouseX, mouseY) && menu.maxScrollRow() > 0) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (infoPage && draggingInfoScrollbar) {
            updateInfoScrollFromMouse(mouseY);
            return true;
        }
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingInfoScrollbar) {
            draggingInfoScrollbar = false;
            return true;
        }
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            switchPage(!infoPage);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 当前是否处于样板页；JEI 幽灵拖拽只在样板页生效。 */
    public boolean isPatternPage() {
        return !infoPage;
    }

    private void switchPage(boolean showInfo) {
        if (infoPage == showInfo) {
            return;
        }
        infoPage = showInfo;
        draggingInfoScrollbar = false;
        draggingScrollbar = false;
        this.hoveredSlot = null;
        applySlotVisibility();
        if (showInfo) {
            infoScroll = Mth.clamp(infoScroll, 0, maxInfoScroll());
        }
    }

    private void applySlotVisibility() {
        // 信息页隐藏全部槽位；原版容器界面会通过 Slot#isActive 自动跳过渲染/悬停
        menu.setSlotsHidden(infoPage);
    }

    private boolean handleTabClick(double mouseX, double mouseY) {
        int x = (int) mouseX - leftPos;
        int y = (int) mouseY - topPos;
        if (x < TAB_X || x >= TAB_X + TAB_WIDTH) {
            return false;
        }
        if (y >= TAB_INFO_Y && y < TAB_INFO_Y + TAB_HEIGHT) {
            switchPage(true);
            return true;
        }
        if (y >= TAB_PATTERN_Y && y < TAB_PATTERN_Y + TAB_HEIGHT) {
            switchPage(false);
            return true;
        }
        return false;
    }

    private void drawSidebar(GuiGraphics guiGraphics) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x, y, x + SIDEBAR_WIDTH, y + imageHeight, 0xFFB8B8B8);
        guiGraphics.fill(x, y, x + 1, y + imageHeight, 0xFFFFFFFF);
        guiGraphics.fill(x + SIDEBAR_WIDTH - 1, y, x + SIDEBAR_WIDTH, y + imageHeight, 0xFF555555);
        guiGraphics.renderOutline(x, y, SIDEBAR_WIDTH, imageHeight, 0xFF000000);

        // 页签 / AE 状态 / Tab 提示之间的细分隔线
        guiGraphics.fill(x + 4, y + 67, x + SIDEBAR_WIDTH - 4, y + 68, 0xFF8B8B8B);
        guiGraphics.fill(x + 4, y + 100, x + SIDEBAR_WIDTH - 4, y + 101, 0xFF8B8B8B);
    }

    private void drawTabs(GuiGraphics guiGraphics) {
        drawTab(guiGraphics, TAB_INFO_Y, infoPage);
        drawTab(guiGraphics, TAB_PATTERN_Y, !infoPage);
    }

    private void drawTab(GuiGraphics guiGraphics, int tabY, boolean active) {
        int x = leftPos + TAB_X;
        int y = topPos + tabY;
        guiGraphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT, active ? 0xFFC6C6C6 : 0xFFA8A8A8);
        guiGraphics.fill(x, y, x + TAB_WIDTH, y + 1, active ? 0xFFFFFFFF : 0xFFE0E0E0);
        guiGraphics.fill(x, y, x + 1, y + TAB_HEIGHT, active ? 0xFFFFFFFF : 0xFFE0E0E0);
        guiGraphics.fill(x + TAB_WIDTH - 1, y, x + TAB_WIDTH, y + TAB_HEIGHT, 0xFF555555);
        guiGraphics.fill(x, y + TAB_HEIGHT - 1, x + TAB_WIDTH, y + TAB_HEIGHT, 0xFF555555);
        guiGraphics.renderOutline(x, y, TAB_WIDTH, TAB_HEIGHT, 0xFF000000);
    }

    private void drawTabLabels(GuiGraphics guiGraphics) {
        drawTabLabel(guiGraphics, KEY_TAB_INFO, TAB_INFO_Y, infoPage);
        drawTabLabel(guiGraphics, KEY_TAB_PATTERNS, TAB_PATTERN_Y, !infoPage);
    }

    /** 侧边栏下方的 AE 接入状态与 Tab 切换提示。 */
    private void drawSidebarHints(GuiGraphics guiGraphics) {
        drawSidebarLine(guiGraphics, Component.translatable(KEY_SIDEBAR_AE), 72, 0xFF404040);

        MatrixInfoView view = menu.getInfoView();
        Component status;
        int statusColor;
        if (view == null) {
            status = Component.translatable(KEY_SIDEBAR_AE_UNKNOWN);
            statusColor = 0xFF808080;
        } else if (view.aeOperational()) {
            status = Component.translatable(KEY_SIDEBAR_AE_CONNECTED);
            statusColor = 0xFF008000;
        } else {
            status = Component.translatable(KEY_SIDEBAR_AE_DISCONNECTED);
            statusColor = 0xFFAA0000;
        }
        drawSidebarLine(guiGraphics, status, 84, statusColor);

        drawSidebarLine(guiGraphics, Component.translatable(KEY_SIDEBAR_TAB_HINT), 110, 0xFF404040);
        drawSidebarLine(guiGraphics, Component.translatable(KEY_SIDEBAR_TAB_HINT_ACTION), 122, 0xFF404040);
    }

    private void drawSidebarLine(GuiGraphics guiGraphics, Component text, int y, int color) {
        int width = font.width(text.getString());
        int x = TAB_X + (TAB_WIDTH - width) / 2;
        guiGraphics.drawString(font, text, x, y, color, false);
    }

    private void drawTabLabel(GuiGraphics guiGraphics, String key, int tabY, boolean active) {
        Component label = Component.translatable(key);
        int width = font.width(label.getString());
        int x = TAB_X + (TAB_WIDTH - width) / 2;
        guiGraphics.drawString(font, label, x, tabY + TAB_LABEL_Y_OFFSET,
                active ? 0x404040 : 0xFFFFFF, false);
    }

    private void drawInfoPanel(GuiGraphics guiGraphics) {
        int x = leftPos + SIDEBAR_WIDTH;
        int y = topPos;
        guiGraphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFFC6C6C6);
        guiGraphics.fill(x, y, x + PANEL_WIDTH, y + 1, 0xFFFFFFFF);
        guiGraphics.fill(x, y, x + 1, y + PANEL_HEIGHT, 0xFFFFFFFF);
        guiGraphics.fill(x, y + PANEL_HEIGHT - 1, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFF555555);
        guiGraphics.fill(x + PANEL_WIDTH - 1, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFF555555);
        guiGraphics.renderOutline(x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xFF000000);

        int contentX = leftPos + INFO_CONTENT_X;
        int contentY = topPos + INFO_CONTENT_Y;
        guiGraphics.fill(contentX - 1, contentY - 1,
                contentX + INFO_CONTENT_WIDTH + 1, contentY + INFO_CONTENT_HEIGHT + 1, 0xFF8B8B8B);
        guiGraphics.fill(contentX, contentY,
                contentX + INFO_CONTENT_WIDTH, contentY + INFO_CONTENT_HEIGHT, 0xFFC6C6C6);
    }

    private void renderInfoScrollbar(GuiGraphics guiGraphics) {
        int max = maxInfoScroll();
        if (max <= 0) {
            return;
        }

        int trackX = leftPos + INFO_SCROLL_X;
        int trackY = topPos + INFO_SCROLL_Y;
        int thumbHeight = infoThumbHeight();
        int thumbY = trackY + (int) ((long) (INFO_SCROLL_HEIGHT - thumbHeight) * infoScroll / max);

        guiGraphics.fill(trackX, trackY, trackX + INFO_SCROLL_WIDTH, trackY + INFO_SCROLL_HEIGHT, 0xFF8B8B8B);
        guiGraphics.fill(trackX + 1, thumbY, trackX + INFO_SCROLL_WIDTH - 1, thumbY + thumbHeight, 0xFFC6C6C6);
        guiGraphics.renderOutline(trackX, trackY, INFO_SCROLL_WIDTH, INFO_SCROLL_HEIGHT, 0xFF000000);
        guiGraphics.renderOutline(trackX + 1, thumbY, INFO_SCROLL_WIDTH - 2, thumbHeight, 0xFF555555);
    }

    private int infoThumbHeight() {
        int content = Math.max(INFO_CONTENT_HEIGHT, infoPageRenderer.contentHeight());
        long thumb = (long) INFO_SCROLL_HEIGHT * INFO_CONTENT_HEIGHT / content;
        return Mth.clamp((int) thumb, INFO_SCROLL_THUMB_MIN, INFO_SCROLL_HEIGHT);
    }

    private int maxInfoScroll() {
        ensureInfoCache();
        return infoPageRenderer.maxScroll(INFO_CONTENT_HEIGHT);
    }

    private void ensureInfoCache() {
        infoPageRenderer.update(menu.getInfoView(), font, INFO_CONTENT_WIDTH);
    }

    private boolean isOverInfoScrollbar(double mouseX, double mouseY) {
        return mouseX >= leftPos + INFO_SCROLL_X
                && mouseX < leftPos + INFO_SCROLL_X + INFO_SCROLL_WIDTH
                && mouseY >= topPos + INFO_SCROLL_Y
                && mouseY < topPos + INFO_SCROLL_Y + INFO_SCROLL_HEIGHT;
    }

    private void updateInfoScrollFromMouse(double mouseY) {
        int trackY = topPos + INFO_SCROLL_Y;
        int thumbHeight = infoThumbHeight();
        int span = INFO_SCROLL_HEIGHT - thumbHeight;
        float progress = span <= 0 ? 0.0F : (float) ((mouseY - trackY - thumbHeight / 2.0D) / span);
        infoScroll = Math.round(Mth.clamp(progress, 0.0F, 1.0F) * maxInfoScroll());
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

    private boolean isHoveringPanel(double mouseX, double mouseY) {
        return mouseX >= leftPos + SIDEBAR_WIDTH && mouseX < leftPos + imageWidth
                && mouseY >= topPos && mouseY < topPos + imageHeight;
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        return mouseX >= leftPos + SCROLL_TRACK_X
                && mouseX < leftPos + SCROLL_TRACK_X + SCROLL_TRACK_WIDTH
                && mouseY >= topPos + SCROLL_TRACK_Y
                && mouseY < topPos + SCROLL_TRACK_Y + SCROLL_TRACK_HEIGHT;
    }
}
