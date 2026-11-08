package com.Fynsh.gtai.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模组通用配置。
 *
 * <p>多方块尺寸与“扩展变体等价数量”都在 {@code config/gtai-common.toml} 的
 * {@code [multiBlock]} 段中。扩展等价配置决定一个扩展核心在写入多方块信息时
 * 按几个普通核心计数。</p>
 */
public final class GtaiConfig {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue MULTIBLOCK_MIN_SIZE;
    private static final ForgeConfigSpec.IntValue MULTIBLOCK_MAX_SIZE;
    private static final ForgeConfigSpec.IntValue MONITOR_DATA_RETENTION_TICKS;
    private static final ForgeConfigSpec.IntValue PROCESSING_CORE_EXTENDED_EQUIVALENT;
    private static final ForgeConfigSpec.IntValue PATTERN_PROVIDER_EXTENDED_EQUIVALENT;
    private static final ForgeConfigSpec.IntValue SPEED_CORE_EXTENDED_EQUIVALENT;
    private static final ForgeConfigSpec.IntValue CHANNEL_BASE;
    private static final ForgeConfigSpec.IntValue CHANNEL_PER_PATTERN_PROVIDER;
    private static final ForgeConfigSpec.IntValue PATTERN_SLOTS_PER_PROVIDER;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Matrix multiblock structure settings.")
                .push("multiBlock");
        MULTIBLOCK_MIN_SIZE = builder
                .comment("Minimum side length of a matrix multiblock, shell included. Range: 3..16.")
                .defineInRange("minSize", 3, 3, 16);
        MULTIBLOCK_MAX_SIZE = builder
                .comment("Maximum side length of a matrix multiblock, shell included. Range: 3..16.")
                .defineInRange("maxSize", 16, 3, 16);
        MONITOR_DATA_RETENTION_TICKS = builder
                .comment("Ticks to keep monitor data after the structure becomes invalid. "
                        + "Data is never cleared immediately. 600 ticks = 30 seconds. Range: 20..72000.")
                .defineInRange("monitorDataRetentionTicks", 600, 20, 72000);

        // 频道预算：required = base + perPatternProviderCore * 样板供应器等价数量。
        // 默认 1 + 数量与历史实现完全一致；把公式放进配置，避免以后调整槽位容量时
        // 还要同时修改频道计算和存档预期。
        builder.comment("AE2 channel budget of a formed matrix: base + perPatternProviderCore * pattern provider equivalents.")
                .push("channels");
        CHANNEL_BASE = builder
                .comment("Channels always consumed by the matrix controller. Range: 0..8192.")
                .defineInRange("base", 1, 0, 8192);
        CHANNEL_PER_PATTERN_PROVIDER = builder
                .comment("Channels consumed by one pattern provider core equivalent. Range: 0..8192.")
                .defineInRange("perPatternProviderCore", 1, 0, 8192);
        builder.pop();

        // 样板存储容量：总槽位 = 本值 * 监控器中“等价的集成样板供应器数量”。
        // 扩展样板供应器已经通过 patternProvider.extendedEquivalent 折算为等价数量。
        builder.comment("Pattern storage settings for the matrix monitor.")
                .push("patternStorage");
        PATTERN_SLOTS_PER_PROVIDER = builder
                .comment("Pattern slots provided by one equivalent integrated pattern provider core. Default: 9.")
                .defineInRange("slotsPerProvider", 9, 1, 1024);
        builder.pop();

        // 三个扩展等价配置必须使用互不相同的完整键：
        // multiBlock.processingCore.extendedEquivalent
        // multiBlock.patternProvider.extendedEquivalent
        // multiBlock.speedCore.extendedEquivalent
        builder.comment("Equivalent normal processing cores contributed by one extended processing core.")
                .push("processingCore");
        PROCESSING_CORE_EXTENDED_EQUIVALENT = builder
                .comment("Multiplier for extended processing cores. Range: 1..1024.")
                .defineInRange("extendedEquivalent", 2, 1, 1024);
        builder.pop();

        builder.comment("Equivalent normal pattern provider cores contributed by one extended pattern provider core.")
                .push("patternProvider");
        PATTERN_PROVIDER_EXTENDED_EQUIVALENT = builder
                .comment("Multiplier for extended pattern provider cores. Range: 1..1024.")
                .defineInRange("extendedEquivalent", 2, 1, 1024);
        builder.pop();

        builder.comment("Equivalent normal speed cores contributed by one extended speed core.")
                .push("speedCore");
        SPEED_CORE_EXTENDED_EQUIVALENT = builder
                .comment("Multiplier for extended speed cores. Range: 1..1024.")
                .defineInRange("extendedEquivalent", 2, 1, 1024);
        builder.pop();

        builder.pop();

        SPEC = builder.build();
    }

    private GtaiConfig() {
    }

    /** 有效最小边长；若配置写反，取两者较小值，保证判定不会因配置顺序而崩溃。 */
    public static int minSize() {
        return Math.min(MULTIBLOCK_MIN_SIZE.get(), MULTIBLOCK_MAX_SIZE.get());
    }

    /** 有效最大边长；若配置写反，取两者较大值。 */
    public static int maxSize() {
        return Math.max(MULTIBLOCK_MIN_SIZE.get(), MULTIBLOCK_MAX_SIZE.get());
    }

    /** 结构失效后保留监控器数据的 tick 数，避免退出/重进或短暂破坏结构时丢数据。 */
    public static int monitorDataRetentionTicks() {
        return Math.max(20, MONITOR_DATA_RETENTION_TICKS.get());
    }

    /** 一个扩展处理核心按几个普通处理核心计入数量。 */
    public static int processingCoreExtendedEquivalent() {
        return Math.max(1, PROCESSING_CORE_EXTENDED_EQUIVALENT.get());
    }

    /** 一个扩展集成样板供应器按几个普通样板供应器计入数量。 */
    public static int patternProviderExtendedEquivalent() {
        return Math.max(1, PATTERN_PROVIDER_EXTENDED_EQUIVALENT.get());
    }

    /** 一个扩展速度核心按几个普通速度核心计入数量。 */
    public static int speedCoreExtendedEquivalent() {
        return Math.max(1, SPEED_CORE_EXTENDED_EQUIVALENT.get());
    }

    /** 矩阵控制器固定占用的频道基准值。 */
    public static int channelBase() {
        return Math.max(0, CHANNEL_BASE.get());
    }

    /** 一个样板供应器等价数量占用的频道数。 */
    public static int channelPerPatternProvider() {
        return Math.max(0, CHANNEL_PER_PATTERN_PROVIDER.get());
    }

    /** 每份等价的集成样板供应器提供的样板槽数。 */
    public static int patternSlotsPerProvider() {
        return Math.max(1, PATTERN_SLOTS_PER_PROVIDER.get());
    }
}
