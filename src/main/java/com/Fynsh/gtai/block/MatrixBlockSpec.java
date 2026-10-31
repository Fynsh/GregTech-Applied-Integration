package com.Fynsh.gtai.block;

/**
 * 一个矩阵方块的静态定义。
 *
 * <p>只携带注册、渲染与结构判定必需的信息：注册名、中文名（与 {@code 方块注册表.txt}
 * 一字不差）、渲染类型，以及从注册名解析出来的结构语义。贴图的层叠关系不在这里，
 * 而是写在 {@code assets/gtai/models/block/<id>.json} 的 element 顺序里。</p>
 *
 * <p>保留三参数构造器，让 199 条注册表继续按“id + 中文名 + 渲染类型”书写；
 * {@link MatrixBlockKind} 在构造时统一解析，解析失败会在类初始化阶段直接暴露。</p>
 *
 * @param id         注册名（不含命名空间）
 * @param zhName     中文名，用于 {@code zh_cn.json} 校对
 * @param renderKind 渲染类型
 * @param kind       结构语义（角色/类型/等级/处理类型/电压/扩展）
 */
public record MatrixBlockSpec(String id, String zhName, MatrixRenderKind renderKind, MatrixBlockKind kind) {

    /** 兼容既有注册表的三参数写法；结构语义由 id 自动解析。 */
    public MatrixBlockSpec(String id, String zhName, MatrixRenderKind renderKind) {
        this(id, zhName, renderKind, MatrixBlockKind.parse(id));
    }
}
