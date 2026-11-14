package com.Fynsh.gtai.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.Fynsh.gtai.client.ClientMatrixInfoHandler;
import com.Fynsh.gtai.multiblock.MatrixInfoView;

/**
 * 服务端 -> 客户端的信息页快照更新。
 *
 * <p>结构详情与运行期数据都不进入方块更新包；菜单打开时快照随打开包发送，打开期间
 * 样板数量或频道可用数变化时由 {@link com.Fynsh.gtai.menu.MatrixMenu#broadcastChanges()}
 * 发送本包刷新。</p>
 */
public class MatrixInfoPacket {

    private final int containerId;
    private final MatrixInfoView view;

    public MatrixInfoPacket(int containerId, MatrixInfoView view) {
        this.containerId = containerId;
        this.view = view;
    }

    public MatrixInfoPacket(FriendlyByteBuf buf) {
        this.containerId = buf.readVarInt();
        this.view = MatrixInfoView.read(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        view.write(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientMatrixInfoHandler.handle(containerId, view)));
        ctx.setPacketHandled(true);
    }
}
