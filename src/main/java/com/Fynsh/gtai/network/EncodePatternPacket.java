package com.Fynsh.gtai.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.network.NetworkEvent;

import appeng.api.stacks.GenericStack;
import appeng.menu.me.items.PatternEncodingTermMenu;

import com.Fynsh.gtai.pattern.GtaiPatternKind;
import com.Fynsh.gtai.pattern.PatternEncoder;

/**
 * 客户端在 JEI 中点击“编写样板”且命中映射后，发给服务端的原子编码请求。
 *
 * <p>真正的消费与生成全部在服务端 {@link PatternEncoder} 里完成，客户端只负责收集
 * JEI 配方页上的原料／产物快照。</p>
 */
public class EncodePatternPacket {

    private static final String TAG_KIND = "kind";
    private static final String TAG_RECIPE_TYPE = "type";
    private static final String TAG_RECIPE = "recipe";
    private static final String TAG_INPUTS = "in";
    private static final String TAG_OUTPUTS = "out";

    private final GtaiPatternKind kind;
    private final ResourceLocation recipeType;
    private final ResourceLocation recipeId;
    private final List<GenericStack> inputs;
    private final List<GenericStack> outputs;

    public EncodePatternPacket(GtaiPatternKind kind, ResourceLocation recipeType, ResourceLocation recipeId,
            List<GenericStack> inputs, List<GenericStack> outputs) {
        this.kind = kind;
        this.recipeType = recipeType;
        this.recipeId = recipeId;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
    }

    public EncodePatternPacket(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag == null) {
            this.kind = null;
            this.recipeType = null;
            this.recipeId = null;
            this.inputs = List.of();
            this.outputs = List.of();
            return;
        }

        GtaiPatternKind parsedKind = null;
        try {
            parsedKind = GtaiPatternKind.valueOf(tag.getString(TAG_KIND));
        } catch (IllegalArgumentException ignored) {
            // 未知样板类型 -> 交给 handle() 丢弃
        }
        this.kind = parsedKind;
        this.recipeType = ResourceLocation.tryParse(tag.getString(TAG_RECIPE_TYPE));
        this.recipeId = ResourceLocation.tryParse(tag.getString(TAG_RECIPE));
        this.inputs = readStacks(tag, TAG_INPUTS);
        this.outputs = readStacks(tag, TAG_OUTPUTS);
    }

    public void encode(FriendlyByteBuf buf) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_KIND, kind.name());
        tag.putString(TAG_RECIPE_TYPE, recipeType.toString());
        tag.putString(TAG_RECIPE, recipeId.toString());
        tag.put(TAG_INPUTS, writeStacks(inputs));
        tag.put(TAG_OUTPUTS, writeStacks(outputs));
        buf.writeNbt(tag);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || kind == null || recipeType == null || recipeId == null) {
                return;
            }
            if (player.containerMenu instanceof PatternEncodingTermMenu menu) {
                PatternEncoder.encode(menu, kind, recipeType, recipeId, inputs, outputs);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static ListTag writeStacks(List<GenericStack> stacks) {
        ListTag list = new ListTag();
        for (GenericStack stack : stacks) {
            if (stack != null) {
                list.add(GenericStack.writeTag(stack));
            }
        }
        return list;
    }

    private static List<GenericStack> readStacks(CompoundTag tag, String key) {
        List<GenericStack> result = new ArrayList<>();
        ListTag list = tag.getList(key, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            GenericStack stack = readStack(list.getCompound(i));
            if (stack != null) {
                result.add(stack);
            }
        }
        return result;
    }

    @Nullable
    private static GenericStack readStack(CompoundTag tag) {
        try {
            return GenericStack.readTag(tag);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
