package com.Fynsh.gtai.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.Fynsh.gtai.GTAIMod;
import com.Fynsh.gtai.block.MatrixBlocks;
import com.Fynsh.gtai.block.MatrixRenderKind;
import com.Fynsh.gtai.blockentity.MatrixBlockEntities;

/**
 * 客户端初始化：给带透明像素的矩阵方块指定 cutout 渲染层。
 *
 * <p>矩阵方块的模型由多个同位置 element 叠成（背景 → 类型 → 等级／电压 → 覆盖版），
 * 上层贴图带透明像素。若沿用默认的 solid 渲染层，这些透明像素会被写成不透明像素，
 * 表现为整块方块的图案被最上层盖死。</p>
 *
 * <p>只处理 {@link MatrixRenderKind#CUTOUT}；{@link MatrixRenderKind#OPAQUE} 保持
 * 默认的 solid 渲染层。</p>
 */
@Mod.EventBusSubscriber(modid = GTAIMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GtaiClientSetup {

    private static final Logger LOGGER = LogUtils.getLogger();

    private GtaiClientSetup() {
    }

    /**
     * 1.20.1 的区块渲染层仍以 {@link ItemBlockRenderTypes} 为准，而 Forge 已把这个运行时 API
     * 标记为"待移除"（推荐改用模型 JSON 的 {@code render_type}）。本项目两条路都走：
     * 模型里声明 {@code render_type}，这里再显式设置一次，保证在任何资源包替换模型的情况下
     * 渲染层都不会退回 solid。
     */
    /** 注册矩阵监控器的 BER：成型后在能源仓位置绘制框架与 IO 覆盖层。 */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MatrixBlockEntities.MATRIX_MONITOR.get(), MatrixMonitorRenderer::new);
    }

    @SuppressWarnings("removal")
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 客户端初始化阶段可能处于并行线程，改渲染层必须丢进主线程队列
        event.enqueueWork(() -> {
            int cutout = 0;
            for (var holder : MatrixBlocks.blocks().values()) {
                if (holder.get().spec().renderKind() != MatrixRenderKind.OPAQUE) {
                    ItemBlockRenderTypes.setRenderLayer(holder.get(), RenderType.cutout());
                    cutout++;
                }
            }
            LOGGER.info("Registered cutout render layer for {} matrix blocks (windows included)", cutout);
        });
    }
}
