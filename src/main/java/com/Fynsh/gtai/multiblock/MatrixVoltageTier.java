package com.Fynsh.gtai.multiblock;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.Fynsh.gtai.block.MatrixShellLevel;
import com.Fynsh.gtai.block.MatrixVoltage;

/**
 * 矩阵电压等级的整数序关系。
 *
 * <p>{@link MatrixVoltage} 只覆盖本模组注册的核心/变压器（LV..LuV），而格雷科技的
 * 能源仓还可以是 ZPM、UV、UHV 等更高等级，因此这里单独提供一套从 1 开始的
 * 单调等级，用于比较能源仓、核心和变压器的等级。</p>
 *
 * <p>矩阵等级到最高可用电压的映射按任务要求固定为：
 * 基础=HV、高级=EV、精英=IV、终极=LuV。</p>
 */
public final class MatrixVoltageTier {

    public static final int LV = 1;
    public static final int MV = 2;
    public static final int HV = 3;
    public static final int EV = 4;
    public static final int IV = 5;
    public static final int LUV = 6;
    public static final int ZPM = 7;
    public static final int UV = 8;
    public static final int UHV = 9;
    public static final int UEV = 10;
    public static final int UIV = 11;
    public static final int UXV = 12;
    public static final int MAX = 13;

    private MatrixVoltageTier() {
    }

    /** 解析能源仓注册名中的电压片段；未知片段返回 -1。 */
    public static int byId(@Nullable String id) {
        if (id == null) {
            return -1;
        }
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "lv" -> LV;
            case "mv" -> MV;
            case "hv" -> HV;
            case "ev" -> EV;
            case "iv" -> IV;
            case "luv" -> LUV;
            case "zpm" -> ZPM;
            case "uv" -> UV;
            case "uhv" -> UHV;
            case "uev" -> UEV;
            case "uiv" -> UIV;
            case "uxv" -> UXV;
            case "max" -> MAX;
            default -> -1;
        };
    }

    public static int of(@Nullable MatrixVoltage voltage) {
        return voltage == null ? -1 : voltage.ordinal() + LV;
    }

    @Nullable
    public static MatrixVoltage voltage(int tier) {
        if (tier < LV || tier > LUV) {
            return null;
        }
        return MatrixVoltage.values()[tier - LV];
    }

    /** 矩阵结构自身等级对应的最高可用电压等级。 */
    public static int of(@Nullable MatrixShellLevel level) {
        if (level == null) {
            return -1;
        }
        return switch (level) {
            case BASIC -> HV;
            case ADVANCED -> EV;
            case ELITE -> IV;
            case ULTIMATE -> LUV;
        };
    }

    /**
     * 能源仓电压反映射得到的矩阵等级。
     *
     * <p>高于 LuV 的能源仓仍视为最高矩阵等级；低于 HV 的能源仓返回 {@code null}
     * （这种结构不允许成型）。</p>
     */
    @Nullable
    public static MatrixShellLevel shellLevel(int tier) {
        if (tier < HV) {
            return null;
        }
        if (tier >= LUV) {
            return MatrixShellLevel.ULTIMATE;
        }
        return switch (tier) {
            case HV -> MatrixShellLevel.BASIC;
            case EV -> MatrixShellLevel.ADVANCED;
            case IV -> MatrixShellLevel.ELITE;
            default -> null;
        };
    }
}
