package com.Fynsh.gtai.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;

import com.Fynsh.gtai.block.MatrixProcess;
import com.Fynsh.gtai.block.MatrixVoltage;

/**
 * 信息页“运行状态”分区的快照。
 *
 * <p>由服务端 {@code MatrixRuntime.createView()} 构造，随 {@link MatrixInfoView} 同步。
 * 列表都在构造时截断并复制，客户端不读取方块实体。</p>
 */
public record MatrixRuntimeView(
        boolean enabled,
        int runningTasks,
        int pausedTasks,
        int maxTasks,
        long euPerTick,
        long energyStored,
        long energyCapacity,
        int bufferEntries,
        int bufferCapacity,
        int bufferItems,
        int bufferFluids,
        List<ProcessRuntime> processes,
        List<SpeedRuntime> speeds,
        List<TaskRuntime> tasks) {

    private static final int MAX_PROCESSES = 64;
    private static final int MAX_SPEEDS = 16;
    private static final int MAX_TASKS = 16;
    private static final int MAX_RECIPE_ID = 128;

    public static final MatrixRuntimeView EMPTY = new MatrixRuntimeView(false, 0, 0, 0, 0L, 0L, 0L,
            0, 0, 0, 0, List.of(), List.of(), List.of());

    public MatrixRuntimeView {
        runningTasks = Math.max(0, runningTasks);
        pausedTasks = Math.max(0, pausedTasks);
        maxTasks = Math.max(0, maxTasks);
        euPerTick = Math.max(0L, euPerTick);
        energyStored = Math.max(0L, energyStored);
        energyCapacity = Math.max(0L, energyCapacity);
        bufferEntries = Math.max(0, bufferEntries);
        bufferCapacity = Math.max(0, bufferCapacity);
        bufferItems = Math.max(0, bufferItems);
        bufferFluids = Math.max(0, bufferFluids);
        processes = List.copyOf(trim(processes, MAX_PROCESSES));
        speeds = List.copyOf(trim(speeds, MAX_SPEEDS));
        tasks = List.copyOf(trim(tasks, MAX_TASKS));
    }

    private static <T> List<T> trim(List<T> list, int max) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<T> copy = new ArrayList<>();
        for (T value : list) {
            if (value != null) {
                copy.add(value);
                if (copy.size() >= max) {
                    break;
                }
            }
        }
        return copy;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeVarInt(runningTasks);
        buf.writeVarInt(pausedTasks);
        buf.writeVarInt(maxTasks);
        buf.writeVarLong(euPerTick);
        buf.writeVarLong(energyStored);
        buf.writeVarLong(energyCapacity);
        buf.writeVarInt(bufferEntries);
        buf.writeVarInt(bufferCapacity);
        buf.writeVarInt(bufferItems);
        buf.writeVarInt(bufferFluids);

        buf.writeVarInt(processes.size());
        for (ProcessRuntime entry : processes) {
            buf.writeVarInt(entry.process().ordinal());
            buf.writeVarInt(entry.voltage().ordinal());
            buf.writeVarInt(entry.active());
            buf.writeVarInt(entry.total());
            buf.writeVarInt(entry.overloaded());
        }

        buf.writeVarInt(speeds.size());
        for (SpeedRuntime entry : speeds) {
            buf.writeVarInt(entry.voltage().ordinal());
            buf.writeVarInt(entry.assigned());
            buf.writeVarInt(entry.total());
        }

        buf.writeVarInt(tasks.size());
        for (TaskRuntime entry : tasks) {
            buf.writeVarInt(entry.slot());
            buf.writeUtf(entry.recipeId(), MAX_RECIPE_ID);
            buf.writeVarInt(entry.elapsed());
            buf.writeVarInt(entry.duration());
            buf.writeVarLong(entry.euPerTick());
            buf.writeVarInt(entry.speedCores());
            buf.writeBoolean(entry.coreVoltage() != null);
            if (entry.coreVoltage() != null) {
                buf.writeVarInt(entry.coreVoltage().ordinal());
            }
            buf.writeVarInt(entry.ocLevel());
            buf.writeBoolean(entry.paused());
        }
    }

    public static MatrixRuntimeView read(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int running = buf.readVarInt();
        int paused = buf.readVarInt();
        int max = buf.readVarInt();
        long eu = buf.readVarLong();
        long stored = buf.readVarLong();
        long capacity = buf.readVarLong();
        int bufferEntries = buf.readVarInt();
        int bufferCapacity = buf.readVarInt();
        int bufferItems = buf.readVarInt();
        int bufferFluids = buf.readVarInt();

        int processCount = Math.min(Math.max(0, buf.readVarInt()), MAX_PROCESSES);
        List<ProcessRuntime> processes = new ArrayList<>(processCount);
        MatrixProcess[] processValues = MatrixProcess.values();
        MatrixVoltage[] voltageValues = MatrixVoltage.values();
        for (int i = 0; i < processCount; i++) {
            int processOrdinal = buf.readVarInt();
            int voltageOrdinal = buf.readVarInt();
            int active = buf.readVarInt();
            int total = buf.readVarInt();
            int overloaded = buf.readVarInt();
            if (processOrdinal >= 0 && processOrdinal < processValues.length
                    && voltageOrdinal >= 0 && voltageOrdinal < voltageValues.length) {
                processes.add(new ProcessRuntime(processValues[processOrdinal], voltageValues[voltageOrdinal],
                        Math.max(0, active), Math.max(0, total), Math.max(0, overloaded)));
            }
        }

        int speedCount = Math.min(Math.max(0, buf.readVarInt()), MAX_SPEEDS);
        List<SpeedRuntime> speeds = new ArrayList<>(speedCount);
        for (int i = 0; i < speedCount; i++) {
            int voltageOrdinal = buf.readVarInt();
            int assigned = buf.readVarInt();
            int total = buf.readVarInt();
            if (voltageOrdinal >= 0 && voltageOrdinal < voltageValues.length) {
                speeds.add(new SpeedRuntime(voltageValues[voltageOrdinal],
                        Math.max(0, assigned), Math.max(0, total)));
            }
        }

        int taskCount = Math.min(Math.max(0, buf.readVarInt()), MAX_TASKS);
        List<TaskRuntime> tasks = new ArrayList<>(taskCount);
        for (int i = 0; i < taskCount; i++) {
            int slot = buf.readVarInt();
            String recipeId = buf.readUtf(MAX_RECIPE_ID);
            int elapsed = buf.readVarInt();
            int duration = buf.readVarInt();
            long euPerTick = buf.readVarLong();
            int speedCores = buf.readVarInt();
            MatrixVoltage coreVoltage = null;
            if (buf.readBoolean()) {
                int ordinal = buf.readVarInt();
                if (ordinal >= 0 && ordinal < voltageValues.length) {
                    coreVoltage = voltageValues[ordinal];
                }
            }
            int ocLevel = buf.readVarInt();
            boolean taskPaused = buf.readBoolean();
            tasks.add(new TaskRuntime(Math.max(0, slot), recipeId, Math.max(0, elapsed),
                    Math.max(1, duration), Math.max(0L, euPerTick), Math.max(0, speedCores),
                    coreVoltage, Math.max(0, ocLevel), taskPaused));
        }

        return new MatrixRuntimeView(enabled, running, paused, max, eu, stored, capacity,
                bufferEntries, bufferCapacity, bufferItems, bufferFluids, processes, speeds, tasks);
    }

    /** 一条“处理类型 + 电压”的并行状态。 */
    public record ProcessRuntime(MatrixProcess process, MatrixVoltage voltage, int active, int total,
            int overloaded) {

        public ProcessRuntime {
            Objects.requireNonNull(process, "process");
            Objects.requireNonNull(voltage, "voltage");
            active = Math.max(0, active);
            total = Math.max(0, total);
            overloaded = Math.max(0, overloaded);
        }
    }

    /** 一个电压等级的速度核心占用状态。 */
    public record SpeedRuntime(MatrixVoltage voltage, int assigned, int total) {

        public SpeedRuntime {
            Objects.requireNonNull(voltage, "voltage");
            assigned = Math.max(0, assigned);
            total = Math.max(0, total);
        }
    }

    /** 一条运行中/暂停的任务摘要。 */
    public record TaskRuntime(int slot, String recipeId, int elapsed, int duration, long euPerTick,
            int speedCores, @Nullable MatrixVoltage coreVoltage, int ocLevel, boolean paused) {

        public TaskRuntime {
            recipeId = recipeId == null ? "" : recipeId;
            if (recipeId.length() > MAX_RECIPE_ID) {
                recipeId = recipeId.substring(0, MAX_RECIPE_ID);
            }
            elapsed = Math.max(0, elapsed);
            duration = Math.max(1, duration);
            euPerTick = Math.max(0L, euPerTick);
            speedCores = Math.max(0, speedCores);
            ocLevel = Math.max(0, ocLevel);
        }

        public int percent() {
            return (int) Math.min(100L, Math.max(0L, elapsed * 100L / duration));
        }
    }
}
