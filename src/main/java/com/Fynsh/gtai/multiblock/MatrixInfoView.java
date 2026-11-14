package com.Fynsh.gtai.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;

import com.Fynsh.gtai.block.MatrixFamily;
import com.Fynsh.gtai.block.MatrixProcess;
import com.Fynsh.gtai.block.MatrixVoltage;

/**
 * 信息 GUI 使用的矩阵信息快照。
 *
 * <p>监控器的完整 {@link MatrixStructureInfo} 只保存在服务端（不进入方块更新包），
 * 客户端需要展示信息页时通过菜单打开包与 {@code MatrixInfoPacket} 接收本快照。
 * 快照同时包含结构信息（核心/变压器/超净间）与运行期数据（当前可用的样板供应器数、
 * 已存储样板数），因此客户端不需要再读取方块实体。</p>
 *
 * <p>列表顺序与 {@link MatrixStructureInfo} 中保存的顺序一致，GUI 直接按顺序渲染。
 * 所有列表在构造时复制并只读，网络读写只使用枚举 ordinal，避免传输字符串。</p>
 */
public record MatrixInfoView(
        boolean monitorExtended,
        @Nullable MatrixFamily family,
        int totalPatternProviders,
        int availablePatternProviders,
        int availablePatternCapacity,
        int usedPatternCount,
        /** AE 侧是否已经真正接入并可用：结构校验完成、至少一个框架端口已接入且获得所需频道。 */
        boolean aeOperational,
        /** AE 实际分配给矩阵的频道数。 */
        int linkedChannels,
        /** 结构需要的频道数（监控器节点数）。 */
        int requiredChannels,
        /** 已创建的频道节点数量。 */
        int channelNodeCount,
        /** 已接入的框架端口数量。 */
        int framePortCount,
        List<MatrixStructureInfo.SpeedCoreEntry> speedCores,
        List<MatrixStructureInfo.ProcessingCoreEntry> processingCores,
        List<MatrixStructureInfo.TransformerEntry> transformers,
        boolean cleanroom,
        boolean cleanroomUsable,
        MatrixStructureInfo.FilterType filterType,
        int filterCount) {

    /** 防止损坏/恶意包构造出过大的列表；正常结构远小于这些上限。 */
    private static final int MAX_SPEED_CORES = 64;
    private static final int MAX_PROCESSING_CORES = 128;
    private static final int MAX_TRANSFORMERS = 32;

    public MatrixInfoView {
        totalPatternProviders = Math.max(0, totalPatternProviders);
        availablePatternProviders = Math.min(Math.max(0, availablePatternProviders), totalPatternProviders);
        availablePatternCapacity = Math.max(0, availablePatternCapacity);
        usedPatternCount = Math.max(0, usedPatternCount);
        linkedChannels = Math.max(0, linkedChannels);
        requiredChannels = Math.max(0, requiredChannels);
        channelNodeCount = Math.max(0, channelNodeCount);
        framePortCount = Math.max(0, framePortCount);
        speedCores = List.copyOf(Objects.requireNonNull(speedCores, "speedCores"));
        processingCores = List.copyOf(Objects.requireNonNull(processingCores, "processingCores"));
        transformers = List.copyOf(Objects.requireNonNull(transformers, "transformers"));
        filterType = Objects.requireNonNull(filterType, "filterType");
        filterCount = Math.max(0, filterCount);
        if (!cleanroom) {
            cleanroomUsable = false;
            filterType = MatrixStructureInfo.FilterType.NONE;
            filterCount = 0;
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(monitorExtended);
        buf.writeBoolean(family != null);
        if (family != null) {
            buf.writeVarInt(family.ordinal());
        }
        buf.writeVarInt(totalPatternProviders);
        buf.writeVarInt(availablePatternProviders);
        buf.writeVarInt(availablePatternCapacity);
        buf.writeVarInt(usedPatternCount);
        buf.writeBoolean(aeOperational);
        buf.writeVarInt(linkedChannels);
        buf.writeVarInt(requiredChannels);
        buf.writeVarInt(channelNodeCount);
        buf.writeVarInt(framePortCount);

        buf.writeVarInt(speedCores.size());
        for (MatrixStructureInfo.SpeedCoreEntry entry : speedCores) {
            buf.writeVarInt(entry.voltage().ordinal());
            buf.writeVarInt(entry.count());
        }

        buf.writeVarInt(processingCores.size());
        for (MatrixStructureInfo.ProcessingCoreEntry entry : processingCores) {
            buf.writeVarInt(entry.process().ordinal());
            buf.writeVarInt(entry.voltage().ordinal());
            buf.writeVarInt(entry.count());
        }

        buf.writeVarInt(transformers.size());
        for (MatrixStructureInfo.TransformerEntry entry : transformers) {
            buf.writeBoolean(entry.active());
            if (!entry.active()) {
                buf.writeVarInt(entry.voltage().ordinal());
            }
        }

        buf.writeBoolean(cleanroom);
        buf.writeBoolean(cleanroomUsable);
        buf.writeVarInt(filterType.ordinal());
        buf.writeVarInt(filterCount);
    }

    public static MatrixInfoView read(FriendlyByteBuf buf) {
        boolean monitorExtended = buf.readBoolean();
        MatrixFamily family = null;
        if (buf.readBoolean()) {
            int ordinal = buf.readVarInt();
            MatrixFamily[] values = MatrixFamily.values();
            if (ordinal >= 0 && ordinal < values.length) {
                family = values[ordinal];
            }
        }
        int totalPatternProviders = Math.max(0, buf.readVarInt());
        int availablePatternProviders = Math.max(0, buf.readVarInt());
        int availablePatternCapacity = Math.max(0, buf.readVarInt());
        int usedPatternCount = Math.max(0, buf.readVarInt());
        boolean aeOperational = buf.readBoolean();
        int linkedChannels = Math.max(0, buf.readVarInt());
        int requiredChannels = Math.max(0, buf.readVarInt());
        int channelNodeCount = Math.max(0, buf.readVarInt());
        int framePortCount = Math.max(0, buf.readVarInt());

        int speedSize = Math.min(Math.max(0, buf.readVarInt()), MAX_SPEED_CORES);
        List<MatrixStructureInfo.SpeedCoreEntry> speedCores = new ArrayList<>(speedSize);
        MatrixVoltage[] voltages = MatrixVoltage.values();
        for (int i = 0; i < speedSize; i++) {
            int voltageOrdinal = buf.readVarInt();
            int count = buf.readVarInt();
            if (voltageOrdinal >= 0 && voltageOrdinal < voltages.length && count > 0) {
                speedCores.add(new MatrixStructureInfo.SpeedCoreEntry(voltages[voltageOrdinal], count));
            }
        }

        int processingSize = Math.min(Math.max(0, buf.readVarInt()), MAX_PROCESSING_CORES);
        List<MatrixStructureInfo.ProcessingCoreEntry> processingCores = new ArrayList<>(processingSize);
        MatrixProcess[] processes = MatrixProcess.values();
        for (int i = 0; i < processingSize; i++) {
            int processOrdinal = buf.readVarInt();
            int voltageOrdinal = buf.readVarInt();
            int count = buf.readVarInt();
            if (processOrdinal >= 0 && processOrdinal < processes.length
                    && voltageOrdinal >= 0 && voltageOrdinal < voltages.length && count > 0) {
                processingCores.add(new MatrixStructureInfo.ProcessingCoreEntry(
                        processes[processOrdinal], voltages[voltageOrdinal], count));
            }
        }

        int transformerSize = Math.min(Math.max(0, buf.readVarInt()), MAX_TRANSFORMERS);
        List<MatrixStructureInfo.TransformerEntry> transformers = new ArrayList<>(transformerSize);
        for (int i = 0; i < transformerSize; i++) {
            boolean active = buf.readBoolean();
            if (active) {
                transformers.add(MatrixStructureInfo.TransformerEntry.ofActive());
            } else {
                int voltageOrdinal = buf.readVarInt();
                if (voltageOrdinal >= 0 && voltageOrdinal < voltages.length) {
                    transformers.add(MatrixStructureInfo.TransformerEntry.ofVoltage(voltages[voltageOrdinal]));
                }
            }
        }

        boolean cleanroom = buf.readBoolean();
        boolean cleanroomUsable = buf.readBoolean();
        int filterOrdinal = buf.readVarInt();
        MatrixStructureInfo.FilterType[] filterTypes = MatrixStructureInfo.FilterType.values();
        MatrixStructureInfo.FilterType filterType = filterOrdinal >= 0 && filterOrdinal < filterTypes.length
                ? filterTypes[filterOrdinal]
                : MatrixStructureInfo.FilterType.NONE;
        int filterCount = Math.max(0, buf.readVarInt());

        return new MatrixInfoView(monitorExtended, family, totalPatternProviders, availablePatternProviders,
                availablePatternCapacity, usedPatternCount, aeOperational, linkedChannels, requiredChannels,
                channelNodeCount, framePortCount, speedCores, processingCores, transformers,
                cleanroom, cleanroomUsable, filterType, filterCount);
    }
}
