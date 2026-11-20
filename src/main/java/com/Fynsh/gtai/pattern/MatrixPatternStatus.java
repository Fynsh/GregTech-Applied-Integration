package com.Fynsh.gtai.pattern;

/**
 * 一个样板槽在当前矩阵中的可执行性判定结果。
 *
 * <p>这是“矩阵能否真正执行这条样板对应的配方”的唯一判定结果：GUI 用它决定是否在
 * 样板槽上叠加浅红色滤镜，后续 AE 样板供应器用它决定是否把样板暴露给 AE。
 * 判定只依赖结构信息与配方本身，不依赖临时的电量/频道状态，因此不会因为断电动画
 * 闪烁。</p>
 */
public enum MatrixPatternStatus {

    /** 配方与结构条件都满足，可以执行。 */
    OK("ok", true),

    /** 矩阵尚未成型，或没有结构信息。 */
    STRUCTURE_UNAVAILABLE("structure", false),

    /** 槽位里不是本模组的编码样板。 */
    NOT_PATTERN("not_pattern", false),

    /** 样板种类与矩阵外壳族不一致。 */
    FAMILY_MISMATCH("family", false),

    /** 样板没有记录配方来源，或记录的配方已不存在/无法解析。 */
    RECIPE_MISSING("recipe", false),

    /** 配方类型不受该矩阵族支持，或无法映射到矩阵处理类型。 */
    PROCESS_UNSUPPORTED("process", false),

    /** 矩阵内没有该处理类型的处理核心（或核心电压高于矩阵等级被过滤）。 */
    PROCESSING_CORE_MISSING("core", false),

    /** 配方要求超净间，但矩阵没有可用超净间。 */
    CLEANROOM_UNAVAILABLE("cleanroom", false),

    /** 配方所需电压高于矩阵有效等级。 */
    VOLTAGE_TOO_LOW("voltage", false),

    /** 矩阵有可用超净间，但过滤器类型与配方要求的 CleanroomType 不一致。 */
    CLEANROOM_TYPE_MISMATCH("cleanroom_type", false),

    /** 样板产物与配方产物不一致（产物白名单校验失败）。 */
    RECIPE_MISMATCH("mismatch", false);

    private final String id;
    private final boolean executable;

    MatrixPatternStatus(String id, boolean executable) {
        this.id = id;
        this.executable = executable;
    }

    /** 注册名/语言键片段，例如 {@code cleanroom}。 */
    public String id() {
        return id;
    }

    /** 该样板是否可以被矩阵执行，也即是否可以对 AE 暴露。 */
    public boolean executable() {
        return executable;
    }
}
