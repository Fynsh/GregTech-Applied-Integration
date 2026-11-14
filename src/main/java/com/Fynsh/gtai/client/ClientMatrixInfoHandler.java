package com.Fynsh.gtai.client;

import net.minecraft.client.Minecraft;

import com.Fynsh.gtai.menu.MatrixMenu;
import com.Fynsh.gtai.multiblock.MatrixInfoView;

/** 客户端处理信息页快照更新；只在客户端类加载路径上引用。 */
public final class ClientMatrixInfoHandler {

    private ClientMatrixInfoHandler() {
    }

    public static void handle(int containerId, MatrixInfoView view) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && minecraft.player.containerMenu instanceof MatrixMenu menu
                && menu.containerId == containerId) {
            menu.setInfoView(view);
        }
    }
}
