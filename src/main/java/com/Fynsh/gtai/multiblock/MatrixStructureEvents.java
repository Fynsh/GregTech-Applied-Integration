package com.Fynsh.gtai.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.Fynsh.gtai.GTAIMod;
import com.Fynsh.gtai.block.MatrixBlock;
import com.Fynsh.gtai.blockentity.MatrixMonitorBlockEntity;

/**
 * 结构方块变化事件。
 *
 * <p>自动刷新主要靠 {@link MatrixBlock#onPlace} / {@link MatrixBlock#onRemove}；
 * 这里额外覆盖 GT 能源仓（不是 MatrixBlock）的放置与拆除：放置时确保“最后一块是
 * 能源仓”也能触发判定，拆除时确保失效结构立刻清空成型信息。</p>
 */
@Mod.EventBusSubscriber(modid = GTAIMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MatrixStructureEvents {

    private MatrixStructureEvents() {
    }

    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        if (placed.getBlock() instanceof MatrixBlock || MatrixEnergyHatch.isEnergyHatch(placed)) {
            MatrixMonitorBlockEntity.refreshNear(serverLevel, event.getPos());
        }
    }

    /**
     * GT 能源仓拆除时不会触发 {@link MatrixBlock#onRemove}，如果监控器不相邻，
     * 结构会一直保持旧的成型状态。这里把刷新延后到本 tick 结束（方块已真正移除），
     * 让现有成型判定自然返回失败并清空成型信息。
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState broken = serverLevel.getBlockState(event.getPos());
        if (!MatrixEnergyHatch.isEnergyHatch(broken)) {
            return;
        }
        BlockPos changed = event.getPos().immutable();
        serverLevel.getServer().execute(() -> MatrixMonitorBlockEntity.refreshNear(serverLevel, changed));
    }
}
