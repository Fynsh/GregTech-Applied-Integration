package com.Fynsh.gtai.block;

import org.jetbrains.annotations.Nullable;

import com.Fynsh.gtai.util.EnumLookup;
import com.Fynsh.gtai.util.IdEnum;

/**
 * 矩阵处理核心的处理类型，以及它允许出现的外壳类型。
 *
 * <p>处理核心没有等级，成型判定只按这里的映射检查它是否与外壳类型匹配。</p>
 */
public enum MatrixProcess implements IdEnum {

    ASSEMBLY("assembly", MatrixFamily.ASSEMBLY),
    CIRCUIT_ASSEMBLY("circuit_assembly", MatrixFamily.CIRCUIT_ASSEMBLY),
    CUTTING("cutting", MatrixFamily.MATERIAL),
    PULVERIZING("pulverizing", MatrixFamily.MATERIAL),
    FORMING("forming", MatrixFamily.MATERIAL),
    SOLIDIFYING("solidifying", MatrixFamily.FLUID_MATERIAL),
    EXTRACTING("extracting", MatrixFamily.FLUID_MATERIAL);

    private final String id;
    private final MatrixFamily family;

    MatrixProcess(String id, MatrixFamily family) {
        this.id = id;
        this.family = family;
    }

    public String id() {
        return id;
    }

    /** 该处理类型要求的外壳类型。 */
    public MatrixFamily family() {
        return family;
    }

    public static MatrixProcess byId(String id) {
        MatrixProcess process = byIdOrNull(id);
        if (process == null) {
            throw new IllegalArgumentException("Unknown matrix process: " + id);
        }
        return process;
    }

    /** 按注册名片段反查；未知片段返回 {@code null}，供 NBT 读取等可容错场景使用。 */
    @Nullable
    public static MatrixProcess byIdOrNull(String id) {
        return EnumLookup.byIdOrNull(values(), id);
    }
}
