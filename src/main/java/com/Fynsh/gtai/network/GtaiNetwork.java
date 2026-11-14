package com.Fynsh.gtai.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import com.Fynsh.gtai.GTAIMod;

/** 本模组的网络通道：编码请求、JEI 样板槽写入与信息页快照同步。 */
public final class GtaiNetwork {

    private static final String PROTOCOL_VERSION = "4";

    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(GTAIMod.id("main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private GtaiNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, EncodePatternPacket.class,
                EncodePatternPacket::encode,
                EncodePatternPacket::new,
                EncodePatternPacket::handle);
        CHANNEL.registerMessage(1, SetPatternSlotPacket.class,
                SetPatternSlotPacket::encode,
                SetPatternSlotPacket::new,
                SetPatternSlotPacket::handle);
        CHANNEL.registerMessage(2, MatrixInfoPacket.class,
                MatrixInfoPacket::encode,
                MatrixInfoPacket::new,
                MatrixInfoPacket::handle);
    }

    public static void sendToServer(EncodePatternPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToServer(SetPatternSlotPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    /** 向指定玩家发送信息页快照；只在服务端调用。 */
    public static void sendToPlayer(Player player, MatrixInfoPacket packet) {
        if (player instanceof ServerPlayer serverPlayer) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), packet);
        }
    }
}
