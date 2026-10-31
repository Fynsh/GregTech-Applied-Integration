package com.Fynsh.gtai.blockentity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.Fynsh.gtai.GTAIMod;
import com.Fynsh.gtai.block.MatrixBlocks;

/**
 * 矩阵方块实体注册。
 *
 * <p>矩阵监控器核心需要方块实体保存结构状态；矩阵框架需要方块实体提供 AE 接入端口。
 * 玻璃、过滤器、内部核心等其余结构件保持无方块实体的轻量结构件。</p>
 */
public final class MatrixBlockEntities {

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(ForgeRegistries.BLOCK_ENTITY_TYPES, GTAIMod.MODID);

    /** 8 个矩阵监控器核心共享一个方块实体类型。 */
    public static final RegistryObject<BlockEntityType<MatrixMonitorBlockEntity>> MATRIX_MONITOR = BLOCK_ENTITIES
            .register("matrix_monitor",
                    () -> BlockEntityType.Builder
                            .of(MatrixMonitorBlockEntity::new, MatrixBlocks.monitorBlocks())
                            .build(null));

    /** 40 种矩阵框架（含超净框架变体）共享一个方块实体类型。 */
    public static final RegistryObject<BlockEntityType<MatrixFrameBlockEntity>> MATRIX_FRAME = BLOCK_ENTITIES
            .register("matrix_frame",
                    () -> BlockEntityType.Builder
                            .of(MatrixFrameBlockEntity::new, MatrixBlocks.frameBlocks())
                            .build(null));

    private MatrixBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
