package com.Fynsh.gtai.recipe;

import java.util.Collections;
import java.util.Map;
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

    private static final Map<RecipeManager, Map<ResourceLocation, Boolean>> RESULT_CACHE = Collections
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
        Map<ResourceLocation, Boolean> cache;
        synchronized (RESULT_CACHE) {
            cache = RESULT_CACHE.computeIfAbsent(manager, ignored -> new ConcurrentHashMap<>());
        }
        return cache.computeIfAbsent(recipeId, id -> lookup(manager, typeId, id));
    }

    private static boolean lookup(RecipeManager manager, ResourceLocation typeId, ResourceLocation recipeId) {
        GTRecipeType type = findType(typeId);
        if (type == null) {
            return false;
        }

        // 快路径：若该配方也被原生 byKey 索引到，直接命中
        var byKey = manager.byKey(recipeId);
        if (byKey.isPresent() && byKey.get() instanceof GTRecipe recipe && recipe.getType() == type) {
            return true;
        }

        java.util.List<GTRecipe> recipes = manager.getAllRecipesFor(type);
        if (recipes.isEmpty()) {
            // 当前端的配方表里完全没有该类型的配方（例如客户端还没同步到 GT 的配方数据），
            // 此时无法判断“配方被删除”，按有效处理，避免把正常样板误判成无效样板。
            return true;
        }
        for (GTRecipe recipe : recipes) {
            if (recipeId.equals(recipe.getId())) {
                return true;
            }
        }
        return false;
    }
}
