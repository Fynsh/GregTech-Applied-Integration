package com.Fynsh.gtai.block;

/**
 * 矩阵方块的渲染类型。
 *
 * <p>它只取决于"这个方块的模型里有没有透明像素、以及要不要挡住相邻方块的面"，
 * 与它属于哪个族无关：</p>
 * <ul>
 *   <li>{@link #OPAQUE}：所有层都不透明，用默认 solid 渲染层，正常遮挡相邻面；</li>
 *   <li>{@link #CUTOUT}：至少有一层带透明像素（类型层、等级层、电压层、覆盖版等），
 *       用 cutout 渲染层；底图不透明，仍然遮挡相邻面；</li>
 *   <li>{@link #CUTOUT_WINDOW}：底图本身就是"有透明孔洞的窗格"（玻璃族），
 *       除了 cutout 之外还必须 {@code noOcclusion()}，否则相邻方块的贴合面会被剔除，
 *       透过透明孔洞会看到空洞。</li>
 * </ul>
 *
 * <p>层叠模型由多个同位置 element 组成（见 {@code assets/gtai/models/block/<id>.json}），
 * 只有 cutout 才能让上层贴图的透明像素正确透出下层。</p>
 */
public enum MatrixRenderKind {

    /** 完全不透明：solid 渲染层，遮挡相邻面。 */
    OPAQUE,

    /** 有透明覆盖层、但底图不透明：cutout 渲染层，遮挡相邻面。 */
    CUTOUT,

    /** 底图带透明孔洞（玻璃）：cutout 渲染层，且不遮挡相邻面。 */
    CUTOUT_WINDOW
}
