package com.Fynsh.gtai.recipe;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import net.minecraftforge.registries.ForgeRegistries;

/**
 * 格雷科技配方的“是否仍然存在”查询。
 *
 * <p>GTM 的配方并不走原生 {@code RecipeManager#byKey}，它通过
 * {@code RecipeManagerMixin} 把 {@link GTRecipe} 直接注入 {@code RecipeManager}
 * 的按配方类型索引中，所以这里用 {@code getAllRecipesFor(recipeType)} 按 id 核对，
 * 这是最能反映“当前配方表里还有没有这条配方”的方式。</p>
 *
 * <p>解码会被频繁调用（工具提示、样板供应器等），因此按 {@link RecipeManager} 实例缓存结果：
 * 数据包重载会新建一个 {@code RecipeManager}，缓存随之失效，正好满足“重载后重新校验”
 * 的需求。</p>
 */
public final class GtRecipeLookup {

    private static final Map<ResourceLocation, GTRecipeType> TYPE_CACHE = new ConcurrentHashMap<>();

    private static final Map<RecipeManager, Map<RecipeKey, Boolean>> RESULT_CACHE = Collections
            .synchronizedMap(new WeakHashMap<>());

    /**
     * 配方对象缓存：可执行性判定需要读取配方的类型、EU 与条件，不能只缓存“存在”布尔值。
     * 键同样按 {@link RecipeManager} 实例隔离，数据包重载后旧缓存自然失效。
     */
    private static final Map<RecipeManager, RecipeLookupCache> RECIPE_CACHE = Collections
            .synchronizedMap(new WeakHashMap<>());

    private GtRecipeLookup() {
    }

    @Nullable
    public static GTRecipeType findType(ResourceLocation typeId) {
        // GTRecipeType 实例在注册表里是单例且不会注销，命中后可以永久缓存；
        // 注册表尚未就绪时 computeIfAbsent 不会写入 null，下次会重试。
        return TYPE_CACHE.computeIfAbsent(typeId, id -> {
            for (RecipeType<?> type : ForgeRegistries.RECIPE_TYPES) {
                if (type instanceof GTRecipeType gtType && gtType.registryName.equals(id)) {
                    return gtType;
                }
            }
            return null;
        });
    }

    /** 给定配方类型与配方 id，判断该配方当前是否存在。 */
    public static boolean exists(Level level, ResourceLocation typeId, ResourceLocation recipeId) {
        RecipeManager manager = level.getRecipeManager();
        Map<RecipeKey, Boolean> cache;
        synchronized (RESULT_CACHE) {
            cache = RESULT_CACHE.computeIfAbsent(manager, ignored -> new ConcurrentHashMap<>());
        }
        RecipeKey key = new RecipeKey(typeId, recipeId);
        return cache.computeIfAbsent(key, ignored -> lookup(manager, key));
    }

    /**
     * 按配方类型与配方 id 取回 {@link GTRecipe}；不存在或类型不匹配时返回 {@code null}。
     *
     * <p>与 {@link #exists(Level, ResourceLocation, ResourceLocation)} 不同，本方法把
     * “找不到”如实返回，供样板可执行性判定使用；{@code exists} 仍保留原来的宽松语义，
     * 用于样板解码时的失效检测。</p>
     */
    @Nullable
    public static GTRecipe find(Level level, ResourceLocation typeId, ResourceLocation recipeId) {
        if (level == null || typeId == null || recipeId == null) {
            return null;
        }
        RecipeManager manager = level.getRecipeManager();
        RecipeLookupCache cache;
        synchronized (RECIPE_CACHE) {
            cache = RECIPE_CACHE.computeIfAbsent(manager, ignored -> new RecipeLookupCache());
        }
        RecipeKey key = new RecipeKey(typeId, recipeId);
        synchronized (cache) {
            if (cache.misses.contains(key)) {
                return null;
            }
            GTRecipe cached = cache.hits.get(key);
            if (cached != null) {
                return cached;
            }
            GTRecipe found = lookupRecipe(manager, key);
            if (found == null) {
                cache.misses.add(key);
            } else {
                cache.hits.put(key, found);
            }
            return found;
        }
    }

    @Nullable
    private static GTRecipe lookupRecipe(RecipeManager manager, RecipeKey key) {
        GTRecipeType type = findType(key.type());
        if (type == null) {
            return null;
        }

        var byKey = manager.byKey(key.id());
        if (byKey.isPresent() && byKey.get() instanceof GTRecipe recipe && recipe.getType() == type) {
            return recipe;
        }

        for (GTRecipe recipe : manager.getAllRecipesFor(type)) {
            if (key.id().equals(recipe.getId())) {
                return recipe;
            }
        }
        return null;
    }

    private static boolean lookup(RecipeManager manager, RecipeKey key) {
        GTRecipeType type = findType(key.type());
        if (type == null) {
            return false;
        }

        // 快路径：若该配方也被原生 byKey 索引到，直接命中
        var byKey = manager.byKey(key.id());
        if (byKey.isPresent() && byKey.get() instanceof GTRecipe recipe && recipe.getType() == type) {
            return true;
        }

        java.util.List<GTRecipe> recipes = manager.getAllRecipesFor(type);
        if (recipes.isEmpty()) {
            // 完全拿不到该类配方时无法区分“配方被删除”和“当前端尚未同步/该类型使用
            // 动态配方逻辑”，保持宽松，避免把正常样板误判成无效样板。
            return true;
        }
        for (GTRecipe recipe : recipes) {
            if (key.id().equals(recipe.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 配方存在性缓存的键。
     *
     * <p>配方 id 通常在同一个 {@link RecipeManager} 内唯一，但不同配方类型下出现同名 id
     * 并非不可能；把类型一起放进键里，避免两次查询互相污染缓存。</p>
     */
    private record RecipeKey(ResourceLocation type, ResourceLocation id) {
    }

    /** 一个 {@link RecipeManager} 对应的命中/未命中缓存；只在持有该缓存的同步块内访问。 */
    private static final class RecipeLookupCache {

        private final Map<RecipeKey, GTRecipe> hits = new HashMap<>();
        private final Set<RecipeKey> misses = new HashSet<>();
    }
}
