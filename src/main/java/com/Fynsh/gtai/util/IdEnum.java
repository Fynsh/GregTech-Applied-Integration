package com.Fynsh.gtai.util;

/**
 * 可以用 {@link #id()}（注册名片段）唯一标识一个常量的枚举。
 *
 * <p>矩阵方块的四套维度枚举（外壳类型、外壳等级、电压、处理类型）都遵循同一个约定：
 * 注册名里的片段就是枚举常量的稳定标识，NBT 也只保存这个片段。把它们统一到这个接口后，
 * “按片段反查”只需要一份实现。</p>
 */
public interface IdEnum {

    /** 注册名中的片段，例如 {@code circuit_assembly}、{@code lv}。 */
    String id();
}
