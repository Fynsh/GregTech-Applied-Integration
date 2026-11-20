package com.Fynsh.gtai.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import com.Fynsh.gtai.block.MatrixFamily;
import com.Fynsh.gtai.block.MatrixProcess;
import com.Fynsh.gtai.block.MatrixVoltage;
import com.Fynsh.gtai.config.GtaiConfig;
import com.Fynsh.gtai.multiblock.MatrixInfoView;
import com.Fynsh.gtai.multiblock.MatrixRuntimeView;
import com.Fynsh.gtai.multiblock.MatrixStructureInfo;

/**
 * 矩阵信息页的文本布局与渲染。
 *
 * <p>本类只负责“把 {@link MatrixInfoView} 变成可滚动的行”，不处理页面切换、页签或滚动条；
 * 这些交互由 {@link MatrixScreen} 负责。所有界面文本都走翻译键，条目顺序完全沿用
 * {@link MatrixStructureInfo} 中已经排好序的列表。</p>
 *
 * <p>“可用/不可用”判定严格只使用监控器写入的快照与当前可用样板供应器数，不重新扫描结构：
 * 类目为空、变压器缺少同级核心、有源变压器、速度核心缺少同级处理核心、超净间写入判定、
 * 样板供应器过载。同一条目只会放入其中一个分区。</p>
 */
public class MatrixInfoPage {

    private static final String KEY_SECTION_AVAILABLE = "gui.gtai.matrix.info.available";
    private static final String KEY_SECTION_UNAVAILABLE = "gui.gtai.matrix.info.unavailable";
    private static final String KEY_CATEGORY_PROVIDER = "gui.gtai.matrix.info.category.pattern_provider";
    private static final String KEY_CATEGORY_PROCESSING = "gui.gtai.matrix.info.category.processing_core";
    private static final String KEY_CATEGORY_SPEED = "gui.gtai.matrix.info.category.speed_core";
    private static final String KEY_CATEGORY_TRANSFORMER = "gui.gtai.matrix.info.category.transformer";
    private static final String KEY_CATEGORY_CLEANROOM = "gui.gtai.matrix.info.category.cleanroom";
    private static final String KEY_AE_STATUS = "gui.gtai.matrix.info.ae.status";
    private static final String KEY_AE_CONNECTED = "gui.gtai.matrix.info.ae.connected";
    private static final String KEY_AE_DISCONNECTED = "gui.gtai.matrix.info.ae.disconnected";
    private static final String KEY_AE_CHANNELS = "gui.gtai.matrix.info.ae.channels";
    private static final String KEY_AE_NODES = "gui.gtai.matrix.info.ae.nodes";
    private static final String KEY_AE_SHORTAGE = "gui.gtai.matrix.info.ae.shortage";
    private static final String KEY_PROVIDER_COUNT = "gui.gtai.matrix.info.provider.count";
    private static final String KEY_PROVIDER_USAGE = "gui.gtai.matrix.info.provider.usage";
    private static final String KEY_PROVIDER_OVERLOAD = "gui.gtai.matrix.info.provider.overload";
    private static final String KEY_RATE_UNAVAILABLE = "gui.gtai.matrix.info.rate_unavailable";
    private static final String KEY_PROCESSING_ENTRY = "gui.gtai.matrix.info.processing.entry";
    private static final String KEY_SPEED_ENTRY = "gui.gtai.matrix.info.speed.entry";
    private static final String KEY_TRANSFORMER_VOLTAGE = "gui.gtai.matrix.info.transformer.voltage";
    private static final String KEY_TRANSFORMER_ACTIVE = "gui.gtai.matrix.info.transformer.active";
    private static final String KEY_CLEANROOM_ENTRY = "gui.gtai.matrix.info.cleanroom.entry";
    private static final String KEY_EMPTY = "gui.gtai.matrix.info.empty";
    private static final String KEY_CATEGORY_RUNTIME = "gui.gtai.matrix.info.category.runtime";
    private static final String KEY_RUNTIME_STATE = "gui.gtai.matrix.info.runtime.state";
    private static final String KEY_RUNTIME_RUNNING = "gui.gtai.matrix.info.runtime.running";
    private static final String KEY_RUNTIME_IDLE = "gui.gtai.matrix.info.runtime.idle";
    private static final String KEY_RUNTIME_PAUSED = "gui.gtai.matrix.info.runtime.paused";
    private static final String KEY_RUNTIME_DISABLED = "gui.gtai.matrix.info.runtime.disabled";
    private static final String KEY_RUNTIME_TASKS = "gui.gtai.matrix.info.runtime.tasks";
    private static final String KEY_RUNTIME_ENERGY = "gui.gtai.matrix.info.runtime.energy";
    private static final String KEY_RUNTIME_BUFFER = "gui.gtai.matrix.info.runtime.buffer";
    private static final String KEY_RUNTIME_PARALLEL = "gui.gtai.matrix.info.runtime.parallel";
    private static final String KEY_RUNTIME_SPEED = "gui.gtai.matrix.info.runtime.speed";
    private static final String KEY_RUNTIME_TASK = "gui.gtai.matrix.info.runtime.task";
    private static final String KEY_RUNTIME_TASK_PAUSED = "gui.gtai.matrix.info.runtime.task.paused";
    private static final String KEY_RUNTIME_MORE = "gui.gtai.matrix.info.runtime.more";
    private static final String KEY_RUNTIME_CORE = "gui.gtai.matrix.info.runtime.core";
    private static final String KEY_PROCESSING_RUNTIME = "gui.gtai.matrix.info.processing.runtime";
    private static final String KEY_PROCESSING_OVERLOAD = "gui.gtai.matrix.info.processing.overload";
    private static final String KEY_SPEED_ENTRY_OCCUPIED = "gui.gtai.matrix.info.speed.entry.occupied";

    private static final String KEY_VOLTAGE_PREFIX = "gui.gtai.matrix.voltage.";
    private static final String KEY_PROCESS_PREFIX = "gui.gtai.matrix.process.";
    private static final String KEY_FILTER_PREFIX = "gui.gtai.matrix.filter.";

    private static final int COLOR_AVAILABLE = 0xFF008000;
    private static final int COLOR_UNAVAILABLE = 0xFFAA0000;
    private static final int COLOR_CATEGORY = 0xFF202020;
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_EMPTY = 0xFF808080;

    private static final int SECTION_HEIGHT = 16;
    private static final int CATEGORY_HEIGHT = 12;
    private static final int ENTRY_HEIGHT = 10;
    private static final int CONTENT_BOTTOM_PADDING = 4;

    /** 一行已经翻译好的显示文本。 */
    private record Line(String text, int color, int height, int indent) {
    }

    private final List<Line> lines = new ArrayList<>();

    @Nullable
    private MatrixInfoView cachedView;
    private int cachedWidth = -1;
    private int contentHeight;

    /**
     * 按当前快照重建行；快照相同或宽度不变时直接复用。
     *
     * @param view  信息页快照；{@code null} 表示尚未收到同步数据
     * @param font  当前字体，用于自动换行
     * @param width 内容区宽度（不含页签栏与滚动条）
     */
    public void update(@Nullable MatrixInfoView view, Font font, int width) {
        if (Objects.equals(view, cachedView) && width == cachedWidth) {
            return;
        }
        cachedView = view;
        cachedWidth = width;
        lines.clear();
        if (view == null || !view.monitorExtended() || width <= 0) {
            contentHeight = 0;
            return;
        }

        addRuntime(view, font, width);
        addSection(view, font, width, true);
        addSection(view, font, width, false);

        int total = CONTENT_BOTTOM_PADDING;
        for (Line line : lines) {
            total += line.height();
        }
        contentHeight = total;
    }

    /** 强制下一次 {@link #update} 重建文本；用于界面重新初始化/语言重载。 */
    public void invalidate() {
        cachedView = null;
        cachedWidth = -1;
    }

    public int contentHeight() {
        return contentHeight;
    }

    public int maxScroll(int viewportHeight) {
        return Math.max(0, contentHeight - Math.max(0, viewportHeight));
    }

    /**
     * 在已经设置好 scissor 的内容区内绘制全部行；滚动量由调用方提供。
     */
    public void render(GuiGraphics guiGraphics, Font font, int x, int y, int height, int scroll) {
        int lineY = y - scroll;
        for (Line line : lines) {
            if (lineY + line.height() > y && lineY < y + height) {
                int textY = lineY + Math.max(0, (line.height() - font.lineHeight) / 2);
                guiGraphics.drawString(font, line.text(), x + line.indent(), textY, line.color(), false);
            }
            lineY += line.height();
        }
    }

    /**
     * 预留：处理核心运行状态的扩展点。
     *
     * <p>后续接入配方执行器后，可以在这里按处理核心返回额外的一行（例如“运行中：2/4”）。
     * 返回 {@code null} 表示不追加任何内容。</p>
     */
    @Nullable
    protected Component processingRuntimeStatus(MatrixStructureInfo.ProcessingCoreEntry entry) {
        MatrixInfoView view = cachedView;
        if (view == null) {
            return null;
        }
        MatrixRuntimeView.ProcessRuntime runtime = processRuntime(view, entry);
        return runtime == null ? null
                : Component.translatable(KEY_PROCESSING_RUNTIME, runtime.active(), runtime.total());
    }

    /** 信息页顶部的运行状态分区。 */
    private void addRuntime(MatrixInfoView view, Font font, int width) {
        MatrixRuntimeView runtime = view.runtime();
        addCategoryTitle(KEY_CATEGORY_RUNTIME, font, width);
        if (runtime == null || !runtime.enabled()) {
            addEntry(Component.translatable(KEY_RUNTIME_DISABLED), COLOR_EMPTY, font, width);
            return;
        }

        int total = runtime.runningTasks() + runtime.pausedTasks();
        Component state;
        int stateColor;
        if (total == 0) {
            state = Component.translatable(KEY_RUNTIME_IDLE);
            stateColor = COLOR_EMPTY;
        } else if (runtime.runningTasks() > 0) {
            state = Component.translatable(KEY_RUNTIME_RUNNING);
            stateColor = COLOR_AVAILABLE;
        } else {
            state = Component.translatable(KEY_RUNTIME_PAUSED);
            stateColor = COLOR_UNAVAILABLE;
        }
        addEntry(Component.translatable(KEY_RUNTIME_STATE, state), stateColor, font, width);
        addEntry(Component.translatable(KEY_RUNTIME_TASKS, total, runtime.maxTasks(),
                runtime.runningTasks(), runtime.pausedTasks()), COLOR_TEXT, font, width);
        addEntry(Component.translatable(KEY_RUNTIME_ENERGY, runtime.euPerTick(),
                runtime.energyStored(), runtime.energyCapacity()), COLOR_TEXT, font, width);
        addEntry(Component.translatable(KEY_RUNTIME_BUFFER, runtime.bufferEntries(),
                runtime.bufferCapacity(), runtime.bufferItems(), runtime.bufferFluids()), COLOR_TEXT, font, width);

        for (MatrixRuntimeView.ProcessRuntime process : runtime.processes()) {
            if (process.total() <= 0) {
                continue;
            }
            addEntry(Component.translatable(KEY_RUNTIME_PARALLEL, processName(process.process()),
                    voltageName(process.voltage()), process.active(), process.total()), COLOR_TEXT, font, width);
        }
        for (MatrixRuntimeView.SpeedRuntime speed : runtime.speeds()) {
            addEntry(Component.translatable(KEY_RUNTIME_SPEED, voltageName(speed.voltage()),
                    speed.assigned(), speed.total()), COLOR_TEXT, font, width);
        }

        if (!runtime.tasks().isEmpty()) {
            for (MatrixRuntimeView.TaskRuntime task : runtime.tasks()) {
                String recipe = shorten(task.recipeId(), 48);
                if (task.paused()) {
                    addEntry(Component.translatable(KEY_RUNTIME_TASK_PAUSED, task.slot(), recipe,
                            task.euPerTick()), COLOR_UNAVAILABLE, font, width);
                } else {
                    addEntry(Component.translatable(KEY_RUNTIME_TASK, task.slot(), recipe,
                            task.percent() + "%", task.euPerTick(),
                            coreName(task.coreVoltage(), task.ocLevel()), task.speedCores()),
                            COLOR_TEXT, font, width);
                }
            }
            int shown = runtime.tasks().size();
            if (total > shown) {
                addEntry(Component.translatable(KEY_RUNTIME_MORE, total - shown), COLOR_EMPTY, font, width);
            }
        }
    }

    private static Component coreName(@Nullable MatrixVoltage voltage, int ocLevel) {
        Component base = voltage == null ? Component.literal("—") : voltageName(voltage);
        if (ocLevel <= 0) {
            return base;
        }
        return Component.translatable(KEY_RUNTIME_CORE, base, "+" + ocLevel);
    }

    private static String shorten(String value, int max) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private void addSection(MatrixInfoView view, Font font, int width, boolean available) {
        addText(Component.translatable(available ? KEY_SECTION_AVAILABLE : KEY_SECTION_UNAVAILABLE),
                available ? COLOR_AVAILABLE : COLOR_UNAVAILABLE, font, width, 0, SECTION_HEIGHT);
        if (available) {
            addAeStatus(view, font, width);
        }
        addProvider(view, font, width, available);
        addProcessing(view, font, width, available);
        addSpeed(view, font, width, available);
        addTransformer(view, font, width, available);
        if (isCleanroomFamily(view.family())) {
            addCleanroom(view, font, width, available);
        }
    }

    /** “可用”分区顶部：AE 接入状态与频道使用情况。 */
    private void addAeStatus(MatrixInfoView view, Font font, int width) {
        boolean connected = view.aeOperational();
        Component status = Component.translatable(connected ? KEY_AE_CONNECTED : KEY_AE_DISCONNECTED);
        addEntry(Component.translatable(KEY_AE_STATUS, status),
                connected ? COLOR_AVAILABLE : COLOR_UNAVAILABLE, font, width);
        addEntry(Component.translatable(KEY_AE_CHANNELS, view.linkedChannels(), view.requiredChannels()),
                COLOR_TEXT, font, width);
        addEntry(Component.translatable(KEY_AE_NODES, view.channelNodeCount(), view.framePortCount()),
                COLOR_TEXT, font, width);
        if (view.requiredChannels() > 0 && view.linkedChannels() < view.requiredChannels()) {
            addEntry(Component.translatable(KEY_AE_SHORTAGE), COLOR_UNAVAILABLE, font, width);
        }
        addGap(6);
    }

    private void addGap(int height) {
        lines.add(new Line("", 0, height, 0));
    }

    private void addProvider(MatrixInfoView view, Font font, int width, boolean available) {
        int total = view.totalPatternProviders();
        int availableCount = view.availablePatternProviders();
        int overloaded = Math.max(0, total - availableCount);

        if (available) {
            if (total <= 0) {
                return;
            }
            addCategoryTitle(KEY_CATEGORY_PROVIDER, font, width);
            int capacity = view.availablePatternCapacity();
            int used = Math.min(view.usedPatternCount(), capacity);
            String rate = capacity <= 0 ? null
                    : String.format(Locale.ROOT, "%.1f%%", used * 100.0D / capacity);
            Component rateText = rate == null
                    ? Component.translatable(KEY_RATE_UNAVAILABLE)
                    : Component.literal(rate);
            // 等价数量按监控器结构总数显示；使用情况的容量只计算未被过载的供应器
            addEntry(Component.translatable(KEY_PROVIDER_COUNT, total), COLOR_TEXT, font, width);
            addEntry(Component.translatable(KEY_PROVIDER_USAGE, used, capacity, rateText), COLOR_TEXT, font, width);
            return;
        }

        if (total <= 0) {
            addEmptyCategory(KEY_CATEGORY_PROVIDER, font, width);
            return;
        }
        if (overloaded <= 0) {
            return;
        }
        addCategoryTitle(KEY_CATEGORY_PROVIDER, font, width);
        addEntry(Component.translatable(KEY_PROVIDER_OVERLOAD, overloaded), COLOR_TEXT, font, width);
    }

    private void addProcessing(MatrixInfoView view, Font font, int width, boolean available) {
        List<MatrixStructureInfo.ProcessingCoreEntry> cores = view.processingCores();
        if (cores.isEmpty()) {
            if (!available) {
                addEmptyCategory(KEY_CATEGORY_PROCESSING, font, width);
            }
            return;
        }

        if (available) {
            List<Component> entries = new ArrayList<>();
            for (MatrixStructureInfo.ProcessingCoreEntry entry : cores) {
                MatrixRuntimeView.ProcessRuntime runtime = processRuntime(view, entry);
                int usable = runtime == null
                        ? entry.count()
                        : Math.max(0, runtime.total() / Math.max(1, GtaiConfig.parallelPerProcessingCore()));
                if (usable <= 0) {
                    continue;
                }
                entries.add(Component.translatable(KEY_PROCESSING_ENTRY,
                        voltageName(entry.voltage()), processName(entry.process()), usable));
                if (runtime != null) {
                    entries.add(Component.translatable(KEY_PROCESSING_RUNTIME,
                            runtime.active(), runtime.total()));
                }
            }
            if (!entries.isEmpty()) {
                addCategory(KEY_CATEGORY_PROCESSING, entries, font, width);
            }
            return;
        }

        List<Component> overloaded = new ArrayList<>();
        for (MatrixStructureInfo.ProcessingCoreEntry entry : cores) {
            MatrixRuntimeView.ProcessRuntime runtime = processRuntime(view, entry);
            if (runtime != null && runtime.overloaded() > 0) {
                overloaded.add(Component.translatable(KEY_PROCESSING_OVERLOAD,
                        voltageName(entry.voltage()), processName(entry.process()), runtime.overloaded()));
            }
        }
        if (!overloaded.isEmpty()) {
            addCategory(KEY_CATEGORY_PROCESSING, overloaded, font, width);
        }
    }

    @Nullable
    private static MatrixRuntimeView.ProcessRuntime processRuntime(MatrixInfoView view,
            MatrixStructureInfo.ProcessingCoreEntry entry) {
        MatrixRuntimeView runtime = view.runtime();
        if (runtime == null) {
            return null;
        }
        for (MatrixRuntimeView.ProcessRuntime process : runtime.processes()) {
            if (process.process() == entry.process() && process.voltage() == entry.voltage()) {
                return process;
            }
        }
        return null;
    }

    @Nullable
    private static MatrixRuntimeView.SpeedRuntime speedRuntime(MatrixInfoView view, MatrixVoltage voltage) {
        MatrixRuntimeView runtime = view.runtime();
        if (runtime == null) {
            return null;
        }
        for (MatrixRuntimeView.SpeedRuntime speed : runtime.speeds()) {
            if (speed.voltage() == voltage) {
                return speed;
            }
        }
        return null;
    }

    private void addSpeed(MatrixInfoView view, Font font, int width, boolean available) {
        List<MatrixStructureInfo.SpeedCoreEntry> cores = view.speedCores();
        if (cores.isEmpty()) {
            if (!available) {
                addEmptyCategory(KEY_CATEGORY_SPEED, font, width);
            }
            return;
        }

        Set<MatrixVoltage> processingVoltages = processingVoltages(view);
        List<Component> entries = new ArrayList<>();
        for (MatrixStructureInfo.SpeedCoreEntry entry : cores) {
            boolean usable = processingVoltages.contains(entry.voltage());
            if (usable != available) {
                continue;
            }
            MatrixRuntimeView.SpeedRuntime runtime = usable ? speedRuntime(view, entry.voltage()) : null;
            if (runtime != null) {
                entries.add(Component.translatable(KEY_SPEED_ENTRY_OCCUPIED, voltageName(entry.voltage()),
                        entry.count(), runtime.assigned(), runtime.total()));
            } else {
                entries.add(Component.translatable(KEY_SPEED_ENTRY, voltageName(entry.voltage()), entry.count()));
            }
        }
        if (!entries.isEmpty()) {
            addCategory(KEY_CATEGORY_SPEED, entries, font, width);
        }
    }

    private void addTransformer(MatrixInfoView view, Font font, int width, boolean available) {
        List<MatrixStructureInfo.TransformerEntry> transformers = view.transformers();
        if (transformers.isEmpty()) {
            if (!available) {
                addEmptyCategory(KEY_CATEGORY_TRANSFORMER, font, width);
            }
            return;
        }

        boolean hasActive = transformers.stream().anyMatch(MatrixStructureInfo.TransformerEntry::active);
        Set<MatrixVoltage> processingVoltages = processingVoltages(view);
        Set<MatrixVoltage> speedVoltages = new HashSet<>();
        for (MatrixStructureInfo.SpeedCoreEntry entry : view.speedCores()) {
            speedVoltages.add(entry.voltage());
        }

        List<Component> entries = new ArrayList<>();
        for (MatrixStructureInfo.TransformerEntry entry : transformers) {
            boolean usable;
            if (entry.active()) {
                usable = true;
            } else if (hasActive) {
                usable = false;
            } else {
                usable = processingVoltages.contains(entry.voltage()) || speedVoltages.contains(entry.voltage());
            }
            if (usable == available) {
                entries.add(transformerName(entry));
            }
        }
        if (!entries.isEmpty()) {
            addCategory(KEY_CATEGORY_TRANSFORMER, entries, font, width);
        }
    }

    private void addCleanroom(MatrixInfoView view, Font font, int width, boolean available) {
        if (!view.cleanroom()) {
            if (!available) {
                addEmptyCategory(KEY_CATEGORY_CLEANROOM, font, width);
            }
            return;
        }
        boolean usable = view.cleanroomUsable();
        if (usable != available) {
            return;
        }
        addCategory(KEY_CATEGORY_CLEANROOM,
                List.of(Component.translatable(KEY_CLEANROOM_ENTRY,
                        filterName(view.filterType()), view.filterCount())),
                font, width);
    }

    private void addCategory(String titleKey, List<Component> entries, Font font, int width) {
        addCategoryTitle(titleKey, font, width);
        for (Component entry : entries) {
            addEntry(entry, COLOR_TEXT, font, width);
        }
    }

    private void addCategoryTitle(String titleKey, Font font, int width) {
        addText(Component.translatable(titleKey), COLOR_CATEGORY, font, width, 4, CATEGORY_HEIGHT);
    }

    private void addEmptyCategory(String titleKey, Font font, int width) {
        addCategoryTitle(titleKey, font, width);
        addEntry(Component.translatable(KEY_EMPTY), COLOR_EMPTY, font, width);
    }

    private void addText(Component text, int color, Font font, int width, int indent, int height) {
        List<FormattedCharSequence> parts = font.split(text, Math.max(1, width - indent));
        if (parts.isEmpty()) {
            lines.add(new Line("", color, height, indent));
            return;
        }
        for (FormattedCharSequence part : parts) {
            lines.add(new Line(plainText(part), color, height, indent));
        }
    }

    private void addEntry(Component text, int color, Font font, int width) {
        List<FormattedCharSequence> parts = font.split(text, Math.max(1, width - 8));
        if (parts.isEmpty()) {
            lines.add(new Line("", color, ENTRY_HEIGHT, 8));
            return;
        }
        for (FormattedCharSequence part : parts) {
            lines.add(new Line(plainText(part), color, ENTRY_HEIGHT, 8));
        }
    }

    private static Set<MatrixVoltage> processingVoltages(MatrixInfoView view) {
        Set<MatrixVoltage> voltages = new HashSet<>();
        for (MatrixStructureInfo.ProcessingCoreEntry entry : view.processingCores()) {
            voltages.add(entry.voltage());
        }
        return voltages;
    }

    private static boolean isCleanroomFamily(@Nullable MatrixFamily family) {
        // 材料矩阵也开放超净间：切割机等配方会带 CleanroomCondition，信息页需要展示
        // 超净间状态，否则玩家只能看到样板被标红却不知道原因。
        return family == MatrixFamily.ASSEMBLY
                || family == MatrixFamily.CIRCUIT_ASSEMBLY
                || family == MatrixFamily.MATERIAL;
    }

    private static Component voltageName(MatrixVoltage voltage) {
        return Component.translatable(KEY_VOLTAGE_PREFIX + voltage.id());
    }

    private static Component processName(MatrixProcess process) {
        return Component.translatable(KEY_PROCESS_PREFIX + process.id());
    }

    private static Component filterName(MatrixStructureInfo.FilterType filterType) {
        return Component.translatable(KEY_FILTER_PREFIX + filterType.id());
    }

    private static Component transformerName(MatrixStructureInfo.TransformerEntry entry) {
        if (entry.active()) {
            return Component.translatable(KEY_TRANSFORMER_ACTIVE);
        }
        return Component.translatable(KEY_TRANSFORMER_VOLTAGE, voltageName(entry.voltage()));
    }

    /** 把带样式的字符序列还原成纯文本；颜色由整行统一决定。 */
    public static String plainText(FormattedCharSequence sequence) {
        StringBuilder builder = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }
}
