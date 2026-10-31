package com.Fynsh.gtai.pattern;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.Fynsh.gtai.GTAIMod;

/**
 * 编码样板物品的注册。
 *
 * <p>注册完全由 {@link GtaiPatternKind} 枚举驱动：遍历枚举值，按 {@link GtaiPatternKind#path()}
 * 注册一个 {@link GtaiPatternItem}。新增样板只需要往枚举里加常量，本类不必改动。</p>
 *
 * <p>只做物品注册：不加合成表、不加创造模式标签页、不加掉落／任务／命令获取入口，
 * 也不注册进样板编码终端的任何手动选择列表。正常游戏流程中它们只能由 JEI 的
 * "编写样板"联动生成。</p>
 */
public final class GtaiPatternItems {

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            GTAIMod.MODID);

    private static final Map<GtaiPatternKind, RegistryObject<GtaiPatternItem>> BY_KIND = new EnumMap<>(
            GtaiPatternKind.class);

    static {
        for (GtaiPatternKind kind : GtaiPatternKind.values()) {
            // 样板是消耗品，一次只允许拿一个，避免堆叠后编码／返还逻辑出现歧义
            BY_KIND.put(kind, ITEMS.register(kind.path(),
                    () -> new GtaiPatternItem(kind, new Item.Properties().stacksTo(1))));
        }
    }

    private GtaiPatternItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    /** 注册表里的物品实例；注册表尚未就绪时返回 {@code null}。 */
    @Nullable
    public static GtaiPatternItem item(@Nullable GtaiPatternKind kind) {
        if (kind == null) {
            return null;
        }
        RegistryObject<GtaiPatternItem> holder = BY_KIND.get(kind);
        return holder != null && holder.isPresent() ? holder.get() : null;
    }

    /** 只读视图，方便外部遍历所有样板。 */
    public static Map<GtaiPatternKind, RegistryObject<GtaiPatternItem>> all() {
        return Collections.unmodifiableMap(BY_KIND);
    }
}
