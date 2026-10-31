package com.Fynsh.gtai.recipe;

import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import com.Fynsh.gtai.pattern.GtaiPatternKind;

/**
 * "格雷配方类型 ↔ 本项目样板"的双向索引。
 *
 * <p><b>对应关系本身不在这里声明</b>：每种样板在自己的
 * {@link GtaiPatternKind#recipeTypes()} 里列出它独占的配方类型。本类只做两件事：</p>
 * <ol>
 *   <li>遍历 {@link GtaiPatternKind#values()}，把"配方类型 → 样板"的反查表建出来；</li>
 *   <li>把"样板 → 配方类型列表"整理成只读视图，供调试与后续多方块结构判断使用。</li>
 * </ol>
 *
 * <p>因此新增样板时本类无需改动；索引在第一次查询时才构建，避免在模组构造阶段
 * 过早触碰 GT 的静态配方类型表。</p>
 *
 * <p>约束：一条配方类型只能属于一种样板。若两个枚举常量声明了同一个类型，
 * 这里保留<b>先声明</b>的那个并打印警告（枚举声明顺序即优先级）。</p>
 */
public final class PatternTypeMapping {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 惰性构建的索引；内容不可变，构建后整体替换引用。 */
    private static volatile Index index;

    private PatternTypeMapping() {
    }

    /**
     * 给定格雷配方类型，返回应当使用的样板种类。
     *
     * <p>未命中映射时返回 {@code null}，调用方应完全保持 AE2 原有行为。</p>
     */
    @Nullable
    public static GtaiPatternKind kindFor(@Nullable GTRecipeType type) {
        if (type == null) {
            return null;
        }
        return index().byType.get(type);
    }

    /** 某种样板声明了哪些配方类型；未声明的样板返回空列表（不是 {@code null}）。 */
    public static List<GTRecipeType> typesOf(@Nullable GtaiPatternKind kind) {
        if (kind == null) {
            return List.of();
        }
        return index().byKind.getOrDefault(kind, List.of());
    }

    private static Index index() {
        Index local = index;
        if (local == null) {
            synchronized (PatternTypeMapping.class) {
                local = index;
                if (local == null) {
                    local = build();
                    index = local;
                }
            }
        }
        return local;
    }

    private static Index build() {
        // GTRecipeType 是注册表单例，用 IdentityHashMap 避免依赖 equals/hashCode 的实现
        Map<GTRecipeType, GtaiPatternKind> byType = new IdentityHashMap<>();
        Map<GtaiPatternKind, List<GTRecipeType>> byKind = new EnumMap<>(GtaiPatternKind.class);

        for (GtaiPatternKind kind : GtaiPatternKind.values()) {
            List<GTRecipeType> types = List.copyOf(kind.recipeTypes());
            if (types.isEmpty()) {
                LOGGER.warn("Pattern {} declares no GT recipe type; it can never be encoded", kind.path());
            }
            for (GTRecipeType type : types) {
                GtaiPatternKind previous = byType.putIfAbsent(type, kind);
                if (previous != null && previous != kind) {
                    LOGGER.warn("GT recipe type {} is claimed by both {} and {}; keeping {}",
                            type.registryName, previous.path(), kind.path(), previous.path());
                }
            }
            byKind.put(kind, types);
        }

        return new Index(byType, byKind);
    }

    /** 一次性构建出来的两张只读表。 */
    private static final class Index {

        private final Map<GTRecipeType, GtaiPatternKind> byType;
        private final Map<GtaiPatternKind, List<GTRecipeType>> byKind;

        private Index(Map<GTRecipeType, GtaiPatternKind> byType, Map<GtaiPatternKind, List<GTRecipeType>> byKind) {
            this.byType = Collections.unmodifiableMap(byType);
            this.byKind = Collections.unmodifiableMap(byKind);
        }
    }
}
