package com.Fynsh.gtai.network;

import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import com.Fynsh.gtai.GTAIMod;

/** 本模组的网络通道。只有一个客户端 -> 服务端的编码请求包。 */
public final class GtaiNetwork {

    private static final String PROTOCOL_VERSION = "1";

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
    }

    public static void sendToServer(EncodePatternPacket packet) {
        CHANNEL.sendToServer(packet);
    }
}
