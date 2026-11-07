package com.Fynsh.gtai.block;

import org.jetbrains.annotations.Nullable;

import com.Fynsh.gtai.util.EnumLookup;
import com.Fynsh.gtai.util.IdEnum;

/**
 * 矩阵核心的电压等级。
 *
 * <p>处理核心、速度核心、变压器的注册名里带有该片段。矩阵成型本身只强制能源仓
 * 至少为 HV；核心电压用于监控器过滤与变压器链爆炸检查。</p>
 */
public enum MatrixVoltage implements IdEnum {

    LV("lv"),
    MV("mv"),
    HV("hv"),
    EV("ev"),
    IV("iv"),
    LUV("luv");

    private final String id;

    MatrixVoltage(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static MatrixVoltage byId(String id) {
        MatrixVoltage voltage = byIdOrNull(id);
        if (voltage == null) {
            throw new IllegalArgumentException("Unknown matrix voltage: " + id);
        }
        return voltage;
    }

    /** 按注册名片段反查；未知片段返回 {@code null}，供 NBT 读取等可容错场景使用。 */
    @Nullable
    public static MatrixVoltage byIdOrNull(String id) {
        return EnumLookup.byIdOrNull(values(), id);
    }
}
