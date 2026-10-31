package com.Fynsh.gtai.block;

import org.jetbrains.annotations.Nullable;

import com.Fynsh.gtai.util.EnumLookup;
import com.Fynsh.gtai.util.IdEnum;

/**
 * 矩阵外壳等级，对应 F2PL 的 L 维度。
 *
 * <p>矩阵框架（含超净框架）与矩阵玻璃（含超净玻璃）都带有该等级；同一结构中
 * 框架必须同等级，玻璃若存在也必须与框架同等级。</p>
 */
public enum MatrixShellLevel implements IdEnum {

    BASIC("basic"),
    ADVANCED("advanced"),
    ELITE("elite"),
    ULTIMATE("ultimate");

    private final String id;

    MatrixShellLevel(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static MatrixShellLevel byId(String id) {
        MatrixShellLevel level = byIdOrNull(id);
        if (level == null) {
            throw new IllegalArgumentException("Unknown matrix shell level: " + id);
        }
        return level;
    }

    /** 按注册名片段反查；未知片段返回 {@code null}，供 NBT 读取等可容错场景使用。 */
    @Nullable
    public static MatrixShellLevel byIdOrNull(String id) {
        return EnumLookup.byIdOrNull(values(), id);
    }
}
