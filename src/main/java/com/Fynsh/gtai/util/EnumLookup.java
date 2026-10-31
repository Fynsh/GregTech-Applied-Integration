package com.Fynsh.gtai.util;

import org.jetbrains.annotations.Nullable;

/**
 * 按 {@link IdEnum#id()} 反查枚举常量。
 *
 * <p>四个矩阵枚举原本各自写了一份完全相同的线性查找；这里收敛成一份，
 * 语义保持不变：找不到时返回 {@code null}（{@code id} 为 {@code null} 也返回 {@code null}），
 * 严格模式由各枚举自己的 {@code byId} 负责抛异常。</p>
 *
 * <p>枚举常量数量都是个位数，线性查找比 HashMap 更快也更省内存。</p>
 */
public final class EnumLookup {

    private EnumLookup() {
    }

    /**
     * 在给定枚举常量里按 id 查找。
     *
     * @param values 枚举常量数组，调用方直接传 {@code values()}
     * @param id     注册名片段；{@code null} 时返回 {@code null}
     * @return 匹配的常量；没有匹配返回 {@code null}
     */
    @Nullable
    public static <E extends Enum<E> & IdEnum> E byIdOrNull(E[] values, String id) {
        for (E value : values) {
            if (value.id().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
