package com.Fynsh.gtai.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.Fynsh.gtai.blockentity.MatrixMonitorBlockEntity;

/**
 * 矩阵监控器的 BER：在成型后的能源仓位置叠加矩阵框架外观，再叠加能源仓原本的 IO 标识。
 *
 * <p>能源仓本身仍是 GT 的方块，方块模型/能力不变；这里只在客户端绘制覆盖层。</p>
 */
@SuppressWarnings("deprecation")
public class MatrixMonitorRenderer implements BlockEntityRenderer<MatrixMonitorBlockEntity> {

    private static final ResourceLocation ENERGY_OVERLAY_ROOT = ResourceLocation.fromNamespaceAndPath("gtceu",
            "block/overlay/machine/overlay_energy_");

    public MatrixMonitorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MatrixMonitorBlockEntity monitor, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!monitor.isFormed() || monitor.frameState() == null || monitor.energyHatches().isEmpty()) {
            return;
        }
        Level level = monitor.getLevel();
        if (level == null) {
            return;
        }

        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        BlockPos monitorPos = monitor.getBlockPos();

        for (BlockPos hatchPos : monitor.energyHatches()) {
            BlockPos relative = hatchPos.subtract(monitorPos);
            int hatchLight = LevelRenderer.getLightColor(level, hatchPos);

            // 1. 用矩阵框架的实际模型覆盖能源仓
            poseStack.pushPose();
            poseStack.translate(relative.getX(), relative.getY(), relative.getZ());
            // 略微放大，避免与原能源仓模型共面导致 z-fighting
            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.scale(1.002F, 1.002F, 1.002F);
            poseStack.translate(-0.5, -0.5, -0.5);
            // renderBatched 会结合世界光照/环境光遮蔽，避免 renderSingleBlock 那种“全亮”观感
            blockRenderer.renderBatched(monitor.frameState(), hatchPos, level, poseStack,
                    bufferSource.getBuffer(RenderType.cutout()), false, level.random, ModelData.EMPTY,
                    RenderType.cutout());
            poseStack.popPose();

            // 2. 在能源仓“前方”叠加原版 IO 标识
            BlockState hatchState = level.getBlockState(hatchPos);
            Direction facing = hatchState.hasProperty(BlockStateProperties.FACING)
                    ? hatchState.getValue(BlockStateProperties.FACING)
                    : Direction.NORTH;
            String amperage = amperageSuffix(hatchState);

            poseStack.pushPose();
            poseStack.translate(relative.getX(), relative.getY(), relative.getZ());
            renderOverlay(poseStack, bufferSource, facing, amperage, false, hatchLight);
            renderOverlay(poseStack, bufferSource, facing, amperage, true, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
        }
    }

    private static String amperageSuffix(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) {
            return "1a";
        }
        String path = id.getPath();
        int underscore = path.lastIndexOf('_');
        if (underscore >= 0) {
            String suffix = path.substring(underscore + 1);
            if (suffix.matches("\\d+a")) {
                return suffix;
            }
        }
        return "1a";
    }

    private static void renderOverlay(PoseStack poseStack, MultiBufferSource bufferSource, Direction facing,
            String amperage, boolean emissive, int light) {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(ENERGY_OVERLAY_ROOT.getNamespace(),
                ENERGY_OVERLAY_ROOT.getPath() + amperage + "_in" + (emissive ? "_emissive" : ""));
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        rotateToFace(poseStack, facing);
        // 略微伸出框架覆盖层，避免 z-fighting
        poseStack.translate(0.0, 0.0, 0.502);

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        vertex(consumer, pose, normal, -0.5F, -0.5F, u0, v1, light);
        vertex(consumer, pose, normal, 0.5F, -0.5F, u1, v1, light);
        vertex(consumer, pose, normal, 0.5F, 0.5F, u1, v0, light);
        vertex(consumer, pose, normal, -0.5F, 0.5F, u0, v0, light);

        poseStack.popPose();
    }

    private static void rotateToFace(PoseStack poseStack, Direction facing) {
        switch (facing) {
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            case SOUTH -> {
                // 默认四边形的 +Z 就是南方
            }
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            case UP -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float u,
            float v, int light) {
        consumer.vertex(pose, x, y, 0.0F)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F)
                .endVertex();
    }
}
