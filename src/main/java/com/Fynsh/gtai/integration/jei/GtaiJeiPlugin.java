package com.Fynsh.gtai.integration.jei;

import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;

import com.Fynsh.gtai.GTAIMod;
import com.Fynsh.gtai.client.MatrixScreen;

/** JEI 插件：注册矩阵样板 GUI 的幽灵拖拽目标。 */
@JeiPlugin
public class GtaiJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return GTAIMod.id("jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(MatrixScreen.class, new MatrixGhostIngredientHandler());
    }
}
