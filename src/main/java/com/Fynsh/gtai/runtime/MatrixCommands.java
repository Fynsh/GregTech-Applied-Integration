package com.Fynsh.gtai.runtime;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.Fynsh.gtai.GTAIMod;
import com.Fynsh.gtai.blockentity.MatrixMonitorBlockEntity;

/**
 * 开发/调试命令：查看矩阵任务状态、手动从 ME 抽取材料启动一条样板。
 *
 * <p>手动启动只用于在 AE 自动合成接入前验证执行器；正式流程由
 * {@code MatrixCraftingProvider} 推送样板。</p>
 */
@Mod.EventBusSubscriber(modid = GTAIMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MatrixCommands {

    private MatrixCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("gtai")
                .then(Commands.literal("matrix")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> status(context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos")))))
                        .then(Commands.literal("start")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                                .executes(context -> start(context.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                        IntegerArgumentType.getInteger(context, "slot"))))))));
    }

    private static int status(CommandSourceStack source, BlockPos pos) {
        BlockEntity blockEntity = source.getLevel().getBlockEntity(pos);
        if (!(blockEntity instanceof MatrixMonitorBlockEntity monitor)) {
            source.sendFailure(Component.literal("No matrix monitor at " + pos.toShortString()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(monitor.runtime().describe()), false);
        return 1;
    }

    private static int start(CommandSourceStack source, BlockPos pos, int slot) {
        BlockEntity blockEntity = source.getLevel().getBlockEntity(pos);
        if (!(blockEntity instanceof MatrixMonitorBlockEntity monitor)) {
            source.sendFailure(Component.literal("No matrix monitor at " + pos.toShortString()));
            return 0;
        }
        String result = monitor.runtime().startDebugTask(slot);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
}
