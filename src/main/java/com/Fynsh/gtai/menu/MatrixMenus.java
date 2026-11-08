package com.Fynsh.gtai.menu;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.Fynsh.gtai.GTAIMod;

/** 本模组的菜单类型注册。 */
public final class MatrixMenus {

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister
            .create(ForgeRegistries.MENU_TYPES, GTAIMod.MODID);

    /** 矩阵样板 GUI。客户端屏幕在 {@code GtaiClientSetup} 中注册。 */
    public static final RegistryObject<MenuType<MatrixMenu>> MATRIX = MENUS.register("matrix",
            () -> IForgeMenuType.create(MatrixMenu::new));

    private MatrixMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
