package com.Fynsh.gtai;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import com.Fynsh.gtai.block.MatrixBlocks;
import com.Fynsh.gtai.blockentity.MatrixBlockEntities;
import com.Fynsh.gtai.config.GtaiConfig;
import com.Fynsh.gtai.menu.MatrixMenus;
import com.Fynsh.gtai.multiblock.MatrixMultiblocks;
import com.Fynsh.gtai.network.GtaiNetwork;
import com.Fynsh.gtai.pattern.GtaiPatternItems;

/**
 * GregTech - Applied Integration 模组主类。
 *
 * <p>本模组把格雷科技现代版（GregTech CEu / gtceu）接入应用能源2（ae2）。
 * 两者都在 {@code META-INF/mods.toml} 中声明为强制依赖并指定 {@code ordering="AFTER"}，
 * 因此本类的初始化一定发生在它们之后，可以安全地使用它们的 API。</p>
 */
@Mod(GTAIMod.MODID)
public class GTAIMod {

    /** 模组 id，必须与 gradle.properties 中的 {@code mod_id} 保持一致。 */
    public static final String MODID = "gtai";

    /** 应用能源2 的 mod id。 */
    public static final String AE2_MODID = "ae2";

    /** 格雷科技（GregTech CEu 现代版）的 mod id。 */
    public static final String GTCEU_MODID = "gtceu";

    private static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("removal")
    public GTAIMod(FMLJavaModLoadingContext context) {
        final IEventBus modEventBus = context.getModEventBus();

        // 多方块尺寸配置（multiBlock.minSize / multiBlock.maxSize）
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GtaiConfig.SPEC);

        // 编码样板物品的注册（种类由 GtaiPatternKind 枚举驱动，只注册物品，不加任何获取途径）
        GtaiPatternItems.register(modEventBus);

        // 199 个矩阵方块；监控器核心与矩阵框架会创建方块实体（8 + 32），其余方块保持轻量结构件
        MatrixBlocks.register(modEventBus);
        MatrixBlockEntities.register(modEventBus);

        // 矩阵样板 GUI 菜单类型
        MatrixMenus.register(modEventBus);

        // 四种矩阵结构定义（组装 / 电路组装 / 材料 / 流体材料）
        MatrixMultiblocks.register();

        // 客户端 JEI 编码联动 -> 服务端原子编码 的网络通道
        GtaiNetwork.register();

        // 只有 @Mod 主类会收到 mod 事件总线，注册逻辑统一放在这里，便于后续拆分
        modEventBus.addListener(this::commonSetup);
    }

    /**
     * 通用初始化阶段，客户端与服务端都会执行。
     *
     * <p>此时所有模组的注册表与 API 均已就绪，适合放置 AE2 网络／存储通道与
     * GT 机器／配方的对接代码。</p>
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        // 这两个模组由 mods.toml 强制声明，此处只记录实际加载到的版本，方便排查环境问题
        LOGGER.info("GregTech {} + Applied Energistics 2 {} detected, loading GT/AE integration",
                loadedVersion(GTCEU_MODID), loadedVersion(AE2_MODID));

        // FMLCommonSetupEvent 在线程池中并行触发，任何涉及注册表的操作都必须放进 enqueueWork
        event.enqueueWork(() -> {
            // 要求 §6.9：注册完成后核对方块数量，数量不符以 方块注册表.txt 为准
            verifyMatrixBlockRegistration();

            // TODO: AE2 与 GTM 的具体对接，例如：    
            //   - 注册 AE2 的存储通道／格子，让 GT 的机器与总线能被 ME 网络识别
            //   - 把 GT 的合成配方包装成 AE2 的处理样板（pattern provider）可用形式
        });
    }

    /**
     * 核对矩阵方块的实际注册数量。
     *
     * <p>开发期表被改错时会直接暴露在日志里，而不是"游戏里悄悄少了几个方块"。</p>
     */
    private static void verifyMatrixBlockRegistration() {
        long registered = ForgeRegistries.BLOCKS.getKeys().stream()
                .filter(id -> id.getNamespace().equals(MODID))
                .count();
        if (registered != MatrixBlocks.EXPECTED_COUNT) {
            LOGGER.error("Matrix block registration mismatch: expected {} blocks but the registry holds {}",
                    MatrixBlocks.EXPECTED_COUNT, registered);
        } else {
            LOGGER.info("Registered {} matrix blocks", registered);
        }
    }

    /** 读取指定模组实际加载的版本号，未加载时返回 {@code "unknown"}。 */
    private static String loadedVersion(final String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    /** 构造属于本模组的 {@link ResourceLocation}，用于注册表 id、贴图路径等。 */
    public static ResourceLocation id(final String path) {
        // 本版 Forge 已把 ResourceLocation 的构造器标记为待移除，统一改用工厂方法
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
