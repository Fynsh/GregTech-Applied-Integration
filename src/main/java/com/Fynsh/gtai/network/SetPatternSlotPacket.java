package com.Fynsh.gtai.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import com.Fynsh.gtai.menu.MatrixMenu;

/**
 * 客户端 JEI 拖入样板时发送的请求。
 *
 * <p>请求只包含绝对样板槽位与物品；服务端会重新校验菜单、容量与矩阵族，
 * 客户端无法通过伪造包绕过族限制或覆盖已有样板。</p>
 */
public class SetPatternSlotPacket {

    private final int containerId;
    private final int patternIndex;
    private final ItemStack stack;

    public SetPatternSlotPacket(int containerId, int patternIndex, ItemStack stack) {
        this.containerId = containerId;
        this.patternIndex = patternIndex;
        this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public SetPatternSlotPacket(FriendlyByteBuf buf) {
        this.containerId = buf.readVarInt();
        this.patternIndex = buf.readVarInt();
        this.stack = buf.readItem();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(patternIndex);
        buf.writeItem(stack);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.containerMenu.containerId != containerId) {
                return;
            }
            if (player.containerMenu instanceof MatrixMenu menu && menu.stillValid(player)) {
                if (menu.setPatternFromClient(patternIndex, stack)) {
                    menu.broadcastChanges();
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
