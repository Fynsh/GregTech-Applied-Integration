package com.Fynsh.gtai.multiblock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import com.Fynsh.gtai.block.MatrixBlock;
import com.Fynsh.gtai.block.MatrixBlockKind;
import com.Fynsh.gtai.block.MatrixBlockRole;
import com.Fynsh.gtai.block.MatrixFamily;
import com.Fynsh.gtai.block.MatrixProcess;
import com.Fynsh.gtai.block.MatrixShellLevel;
import com.Fynsh.gtai.block.MatrixVoltage;
import com.Fynsh.gtai.config.GtaiConfig;
import com.Fynsh.gtai.util.EnumLookup;
import com.Fynsh.gtai.util.IdEnum;

/**
 * 一个已成型矩阵的多方块信息快照。
 *
 * <p>本类只负责“成型判定成功之后”的信息收集与 NBT 存取，不参与成型判定。
 * 信息由 {@link #collect(BlockGetter, MatrixStructureMatch)} 扫描结构得到，随后由
 * {@code MatrixMonitorBlockEntity} 写入唯一矩阵监控器的 BlockEntity NBT。</p>
 *
 * <p><b>NBT 结构</b>（根键 {@value #TAG_ROOT}）：</p>
 * <pre>
 * gtai_multiblock: {
 *   version: 1,
 *   shell_type: "assembly",                 // MatrixFamily id
 *   shell_level: "basic",                   // MatrixShellLevel id
 *   energy_voltage: "lv",                   // 能源仓电压等级；无法识别时省略
 *   monitor_extended: 0b,                   // 矩阵监控器是否扩展
 *   pattern_provider_count: 3,              // 样板供应器等价数量
 *   speed_cores: [ {voltage:"lv", count:2}, ... ],       // 按电压等级排序
 *   processing_cores: [ {process:"assembly", voltage:"lv", count:4}, ... ],
 *   transformers: [ "lv", "active" ],       // 只写 K，不写数量
 *   cleanroom: 1b,                          // 内部是否有集成超净间
 *   filter_type: "cleanroom",               // none / cleanroom / sterile
 *   filter_count: 4                         // 过滤器方块数量
 * }
 * </pre>
 *
 * <p>信息收集规则：空核心不写入；样板供应器/速度核心/处理核心按扩展等价配置加权并合并；
 * 变压器只记录类型；过滤器仅在“存在超净间且未混用”时写入有效类型与数量。</p>
 */
public record MatrixStructureInfo(
        MatrixFamily shellType,
        MatrixShellLevel shellLevel,
        @Nullable String energyVoltage,
        boolean monitorExtended,
        int patternProviderCount,
        List<SpeedCoreEntry> speedCores,
        List<ProcessingCoreEntry> processingCores,
        List<TransformerEntry> transformers,
        boolean cleanroom,
        FilterType filterType,
        int filterCount) {

    /** 监控器 BlockEntity NBT 中的根键。 */
    public static final String TAG_ROOT = "gtai_multiblock";

    private static final int VERSION = 1;
    private static final String TAG_VERSION = "version";
    private static final String TAG_SHELL_TYPE = "shell_type";
    private static final String TAG_SHELL_LEVEL = "shell_level";
    private static final String TAG_ENERGY_VOLTAGE = "energy_voltage";
    private static final String TAG_MONITOR_EXTENDED = "monitor_extended";
    private static final String TAG_PATTERN_PROVIDER_COUNT = "pattern_provider_count";
    private static final String TAG_SPEED_CORES = "speed_cores";
    private static final String TAG_PROCESSING_CORES = "processing_cores";
    private static final String TAG_TRANSFORMERS = "transformers";
    private static final String TAG_CLEANROOM = "cleanroom";
    private static final String TAG_FILTER_TYPE = "filter_type";
    private static final String TAG_FILTER_COUNT = "filter_count";
    private static final String TAG_VOLTAGE = "voltage";
    private static final String TAG_COUNT = "count";
    private static final String TAG_PROCESS = "process";

    private static final Comparator<SpeedCoreEntry> SPEED_CORE_ORDER = Comparator
            .comparing(SpeedCoreEntry::voltage);

    private static final Comparator<ProcessingCoreEntry> PROCESSING_CORE_ORDER = Comparator
            .comparing(ProcessingCoreEntry::process)
            .thenComparing(ProcessingCoreEntry::voltage)
            .thenComparingInt(ProcessingCoreEntry::count);

    private static final Comparator<TransformerEntry> TRANSFORMER_ORDER = Comparator
            .comparingInt((TransformerEntry entry) -> entry.active() ? Integer.MAX_VALUE
                    : entry.voltage().ordinal());

    public MatrixStructureInfo {
        Objects.requireNonNull(shellType, "shellType");
        Objects.requireNonNull(shellLevel, "shellLevel");
        if (energyVoltage != null && energyVoltage.isBlank()) {
            energyVoltage = null;
        }
        if (patternProviderCount < 0) {
            patternProviderCount = 0;
        }
        // 记录类型自身保证排序不变式，避免外部构造出乱序的 NBT
        speedCores = speedCores.stream().sorted(SPEED_CORE_ORDER).toList();
        processingCores = processingCores.stream().sorted(PROCESSING_CORE_ORDER).toList();
        transformers = transformers.stream().distinct().sorted(TRANSFORMER_ORDER).toList();
        Objects.requireNonNull(filterType, "filterType");
        if (filterType == FilterType.NONE || filterCount <= 0) {
            // “无类型”必须配 0 数量；有类型时数量也必须为正，避免写出矛盾组合
            filterType = FilterType.NONE;
            filterCount = 0;
        }
    }

    /**
     * 扫描一个已成型结构，收集本阶段要求写入的全部信息。
     *
     * <p>该方法在成型判定成功之后调用；它不修改方块、不修改结构状态。
     * 结构信息来自 {@link MatrixStructureMatch}（外壳类型/等级、能源仓位置），
     * 内容方块与过滤器从世界里逐格扫描得到。</p>
     *
     * @return 信息快照；若框架状态异常导致无法确定外壳等级，返回 {@code null}
     */
    @Nullable
    public static MatrixStructureInfo collect(BlockGetter level, MatrixStructureMatch match) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(match, "match");

        MatrixShellLevel shellLevel = shellLevelOf(match.frameState());
        if (shellLevel == null) {
            return null;
        }

        MatrixStructureBounds bounds = match.bounds();
        MatrixFamily shellType = match.definition().family();

        String energyVoltage = null;
        if (!match.energyHatches().isEmpty()) {
            BlockState hatchState = level.getBlockState(match.energyHatches().get(0));
            energyVoltage = MatrixEnergyHatch.voltageId(hatchState);
        }

        int patternProviders = 0;
        EnumMap<MatrixVoltage, Integer> speedCounts = new EnumMap<>(MatrixVoltage.class);
        Map<ProcessingKey, Integer> processingCounts = new LinkedHashMap<>();
        Set<TransformerEntry> transformers = new TreeSet<>(TRANSFORMER_ORDER);
        boolean monitorExtended = false;
        boolean cleanroom = false;
        int cleanroomFilters = 0;
        int sterileFilters = 0;

        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int y = bounds.min().getY(); y <= bounds.max().getY(); y++) {
                for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    MatrixBlockKind kind = kindOf(state);
                    if (kind == null) {
                        continue;
                    }

                    if (bounds.boundaryCount(pos) > 0) {
                        // 过滤器只可能位于普通表面，这里对整个外壳扫描以保持与未来规则兼容
                        if (kind.role() == MatrixBlockRole.FILTER) {
                            cleanroomFilters++;
                        } else if (kind.role() == MatrixBlockRole.STERILE_FILTER) {
                            sterileFilters++;
                        }
                        continue;
                    }

                    switch (kind.role()) {
                        case EMPTY_CORE -> {
                            // 空核心不写入
                        }
                        case MONITOR -> monitorExtended = kind.extended();
                        case PATTERN_CORE -> patternProviders += weight(kind.extended(),
                                GtaiConfig.patternProviderExtendedEquivalent());
                        case SPEED_CORE -> {
                            if (kind.voltage() != null) {
                                speedCounts.merge(kind.voltage(),
                                        weight(kind.extended(), GtaiConfig.speedCoreExtendedEquivalent()),
                                        Integer::sum);
                            }
                        }
                        case PROCESSING_CORE -> {
                            if (kind.process() != null && kind.voltage() != null) {
                                processingCounts.merge(new ProcessingKey(kind.process(), kind.voltage()),
                                        weight(kind.extended(), GtaiConfig.processingCoreExtendedEquivalent()),
                                        Integer::sum);
                            }
                        }
                        case TRANSFORMER -> {
                            if (kind.voltage() != null) {
                                transformers.add(TransformerEntry.ofVoltage(kind.voltage()));
                            }
                        }
                        case ACTIVE_TRANSFORMER -> transformers.add(TransformerEntry.ofActive());
                        case CLEANROOM -> cleanroom = true;
                        default -> {
                            // 其它角色不会出现在内部
                        }
                    }
                }
            }
        }

        List<SpeedCoreEntry> speedCores = new ArrayList<>();
        for (MatrixVoltage voltage : MatrixVoltage.values()) {
            Integer count = speedCounts.get(voltage);
            if (count != null && count > 0) {
                speedCores.add(new SpeedCoreEntry(voltage, count));
            }
        }
        speedCores.sort(SPEED_CORE_ORDER);

        List<ProcessingCoreEntry> processingCores = new ArrayList<>();
        processingCounts.forEach((key, count) -> processingCores
                .add(new ProcessingCoreEntry(key.process(), key.voltage(), count)));
        processingCores.sort(PROCESSING_CORE_ORDER);

        FilterType filterType = FilterType.NONE;
        int filterCount = 0;
        if (cleanroom) {
            if (cleanroomFilters > 0 && sterileFilters == 0) {
                filterType = FilterType.CLEANROOM;
                filterCount = cleanroomFilters;
            } else if (sterileFilters > 0 && cleanroomFilters == 0) {
                filterType = FilterType.STERILE;
                filterCount = sterileFilters;
            }
            // 混用或没有过滤器时保持 NONE / 0
        }

        return new MatrixStructureInfo(shellType, shellLevel, energyVoltage, monitorExtended, patternProviders,
                speedCores, processingCores, new ArrayList<>(transformers), cleanroom, filterType, filterCount);
    }

    /** 序列化为 BlockEntity NBT 中的 {@value #TAG_ROOT} 子标签。 */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_VERSION, VERSION);
        tag.putString(TAG_SHELL_TYPE, shellType.id());
        tag.putString(TAG_SHELL_LEVEL, shellLevel.id());
        if (energyVoltage != null) {
            tag.putString(TAG_ENERGY_VOLTAGE, energyVoltage);
        }
        tag.putBoolean(TAG_MONITOR_EXTENDED, monitorExtended);
        tag.putInt(TAG_PATTERN_PROVIDER_COUNT, patternProviderCount);

        ListTag speedList = new ListTag();
        for (SpeedCoreEntry entry : speedCores) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(TAG_VOLTAGE, entry.voltage().id());
            entryTag.putInt(TAG_COUNT, entry.count());
            speedList.add(entryTag);
        }
        tag.put(TAG_SPEED_CORES, speedList);

        ListTag processingList = new ListTag();
        for (ProcessingCoreEntry entry : processingCores) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(TAG_PROCESS, entry.process().id());
            entryTag.putString(TAG_VOLTAGE, entry.voltage().id());
            entryTag.putInt(TAG_COUNT, entry.count());
            processingList.add(entryTag);
        }
        tag.put(TAG_PROCESSING_CORES, processingList);

        ListTag transformerList = new ListTag();
        for (TransformerEntry entry : transformers) {
            transformerList.add(StringTag.valueOf(entry.key()));
        }
        tag.put(TAG_TRANSFORMERS, transformerList);

        tag.putBoolean(TAG_CLEANROOM, cleanroom);
        tag.putString(TAG_FILTER_TYPE, filterType.id());
        tag.putInt(TAG_FILTER_COUNT, filterCount);
        return tag;
    }

    /**
     * 从 {@value #TAG_ROOT} 子标签读取信息。
     *
     * <p>缺少外壳类型/等级等必需字段时返回 {@code null}，调用方应视为没有成型信息；
     * 单条速度/处理核心/变压器数据损坏时只跳过该条，不影响其余信息。</p>
     */
    @Nullable
    public static MatrixStructureInfo load(CompoundTag tag) {
        MatrixFamily shellType = MatrixFamily.byIdOrNull(tag.getString(TAG_SHELL_TYPE));
        MatrixShellLevel shellLevel = MatrixShellLevel.byIdOrNull(tag.getString(TAG_SHELL_LEVEL));
        if (shellType == null || shellLevel == null) {
            return null;
        }

        String energyVoltage = tag.contains(TAG_ENERGY_VOLTAGE, Tag.TAG_STRING)
                ? tag.getString(TAG_ENERGY_VOLTAGE)
                : null;
        boolean monitorExtended = tag.getBoolean(TAG_MONITOR_EXTENDED);
        int patternProviderCount = Math.max(0, tag.getInt(TAG_PATTERN_PROVIDER_COUNT));

        List<SpeedCoreEntry> speedCores = new ArrayList<>();
        ListTag speedList = tag.getList(TAG_SPEED_CORES, Tag.TAG_COMPOUND);
        for (int i = 0; i < speedList.size(); i++) {
            CompoundTag entryTag = speedList.getCompound(i);
            MatrixVoltage voltage = MatrixVoltage.byIdOrNull(entryTag.getString(TAG_VOLTAGE));
            int count = entryTag.getInt(TAG_COUNT);
            if (voltage != null && count > 0) {
                speedCores.add(new SpeedCoreEntry(voltage, count));
            }
        }
        speedCores.sort(SPEED_CORE_ORDER);

        List<ProcessingCoreEntry> processingCores = new ArrayList<>();
        ListTag processingList = tag.getList(TAG_PROCESSING_CORES, Tag.TAG_COMPOUND);
        for (int i = 0; i < processingList.size(); i++) {
            CompoundTag entryTag = processingList.getCompound(i);
            MatrixProcess process = MatrixProcess.byIdOrNull(entryTag.getString(TAG_PROCESS));
            MatrixVoltage voltage = MatrixVoltage.byIdOrNull(entryTag.getString(TAG_VOLTAGE));
            int count = entryTag.getInt(TAG_COUNT);
            if (process != null && voltage != null && count > 0) {
                processingCores.add(new ProcessingCoreEntry(process, voltage, count));
            }
        }
        processingCores.sort(PROCESSING_CORE_ORDER);

        List<TransformerEntry> transformers = new ArrayList<>();
        ListTag transformerList = tag.getList(TAG_TRANSFORMERS, Tag.TAG_STRING);
        for (int i = 0; i < transformerList.size(); i++) {
            TransformerEntry entry = TransformerEntry.parse(transformerList.getString(i));
            if (entry != null && !transformers.contains(entry)) {
                transformers.add(entry);
            }
        }
        transformers.sort(TRANSFORMER_ORDER);

        FilterType filterType = FilterType.byIdOrNull(tag.getString(TAG_FILTER_TYPE));
        if (filterType == null) {
            filterType = FilterType.NONE;
        }
        int filterCount = Math.max(0, tag.getInt(TAG_FILTER_COUNT));

        return new MatrixStructureInfo(shellType, shellLevel, energyVoltage, monitorExtended, patternProviderCount,
                speedCores, processingCores, transformers, tag.getBoolean(TAG_CLEANROOM), filterType, filterCount);
    }

    private static int weight(boolean extended, int extendedEquivalent) {
        return extended ? Math.max(1, extendedEquivalent) : 1;
    }

    @Nullable
    private static MatrixBlockKind kindOf(BlockState state) {
        return state.getBlock() instanceof MatrixBlock block ? block.kind() : null;
    }

    @Nullable
    private static MatrixShellLevel shellLevelOf(BlockState frameState) {
        MatrixBlockKind kind = kindOf(frameState);
        return kind == null ? null : kind.level();
    }

    /** 同处理类型 + 同电压的处理核心合并键。 */
    private record ProcessingKey(MatrixProcess process, MatrixVoltage voltage) {
    }

    /** 一条速度核心信息：电压等级与等价数量。 */
    public record SpeedCoreEntry(MatrixVoltage voltage, int count) {

        public SpeedCoreEntry {
            Objects.requireNonNull(voltage, "voltage");
            if (count <= 0) {
                throw new IllegalArgumentException("Speed core count must be positive");
            }
        }
    }

    /** 一条处理核心信息：处理类型、电压等级与等价数量。 */
    public record ProcessingCoreEntry(MatrixProcess process, MatrixVoltage voltage, int count) {

        public ProcessingCoreEntry {
            Objects.requireNonNull(process, "process");
            Objects.requireNonNull(voltage, "voltage");
            if (count <= 0) {
                throw new IllegalArgumentException("Processing core count must be positive");
            }
        }
    }

    /**
     * 一条集成变压器信息：电压等级或有源标记，不记录数量。
     *
     * <p>{@code active == true} 时 {@code voltage == null}；否则 {@code voltage != null}。</p>
     */
    public record TransformerEntry(@Nullable MatrixVoltage voltage, boolean active) {

        private static final String KEY_ACTIVE = "active";

        public TransformerEntry {
            if (active == (voltage != null)) {
                throw new IllegalArgumentException(
                        "Transformer must be either active or have exactly one voltage tier");
            }
        }

        public static TransformerEntry ofVoltage(MatrixVoltage voltage) {
            return new TransformerEntry(Objects.requireNonNull(voltage, "voltage"), false);
        }

        public static TransformerEntry ofActive() {
            return new TransformerEntry(null, true);
        }

        @Nullable
        public static TransformerEntry parse(String key) {
            if (KEY_ACTIVE.equals(key)) {
                return ofActive();
            }
            MatrixVoltage voltage = MatrixVoltage.byIdOrNull(key);
            return voltage == null ? null : ofVoltage(voltage);
        }

        /** NBT/展示用 K 值：电压等级 id 或 {@code active}。 */
        public String key() {
            return active ? KEY_ACTIVE : voltage.id();
        }
    }

    /** 集成过滤器类型。 */
    public enum FilterType implements IdEnum {

        /** 无过滤器，或规则判定为无效（无超净间 / 混用）。 */
        NONE("none"),

        /** 超净过滤器。 */
        CLEANROOM("cleanroom"),

        /** 无菌过滤器。 */
        STERILE("sterile");

        private final String id;

        FilterType(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        @Nullable
        public static FilterType byIdOrNull(String id) {
            return EnumLookup.byIdOrNull(values(), id);
        }
    }
}
