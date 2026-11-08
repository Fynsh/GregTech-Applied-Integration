package com.Fynsh.gtai.network;

import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import com.Fynsh.gtai.GTAIMod;

/** 本模组的网络通道：编码请求与 JEI 样板槽写入。 */
public final class GtaiNetwork {

    private static final String PROTOCOL_VERSION = "2";

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
    }

    public static void sendToServer(EncodePatternPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToServer(SetPatternSlotPacket packet) {
        CHANNEL.sendToServer(packet);
    }
}
