package com.Fynsh.gtai.block;

import org.jetbrains.annotations.Nullable;

import com.Fynsh.gtai.util.EnumLookup;
import com.Fynsh.gtai.util.IdEnum;

/**
 * 矩阵外壳类型。
 *
 * <p>该枚举与矩阵样板核心、速度核心共享同一套类型。外壳类型只由矩阵框架决定；
 * 矩阵玻璃没有类型，能源仓与过滤器也不参与类型判定。</p>
 */
public enum MatrixFamily implements IdEnum {

    /** 组装。 */
    ASSEMBLY("assembly"),

    /** 电路组装。 */
    CIRCUIT_ASSEMBLY("circuit_assembly"),

    /** 材料。 */
    MATERIAL("material"),

    /** 流体材料。 */
    FLUID_MATERIAL("fluid_material");

    private final String id;

    MatrixFamily(String id) {
        this.id = id;
    }

    /** 注册名中的类型片段，例如 {@code circuit_assembly}。 */
    public String id() {
        return id;
    }

    /** 按注册名片段反查；未知片段直接抛异常，避免静默生成错误结构。 */
    public static MatrixFamily byId(String id) {
        MatrixFamily family = byIdOrNull(id);
        if (family == null) {
            throw new IllegalArgumentException("Unknown matrix family: " + id);
        }
        return family;
    }

    /** 按注册名片段反查；未知片段返回 {@code null}，供 NBT 读取等可容错场景使用。 */
    @Nullable
    public static MatrixFamily byIdOrNull(String id) {
        return EnumLookup.byIdOrNull(values(), id);
    }
}
