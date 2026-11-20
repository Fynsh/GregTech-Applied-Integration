package com.Fynsh.gtai.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;

import com.Fynsh.gtai.block.MatrixProcess;
import com.Fynsh.gtai.block.MatrixVoltage;
import com.Fynsh.gtai.blockentity.MatrixMonitorBlockEntity;
import com.Fynsh.gtai.config.GtaiConfig;
import com.Fynsh.gtai.multiblock.MatrixRuntimeView;
import com.Fynsh.gtai.multiblock.MatrixStructureInfo;
import com.Fynsh.gtai.multiblock.MatrixVoltageTier;

/**
 * 矩阵配方执行器的运行时状态。
 *
 * <p>所有任务、速度核心占用、并行额度与离线产物缓存都归监控器所有；本类只在
 * 服务端主线程被调用，不做同步。</p>
 *
 * <p>几个刻意为之的规则：</p>
 * <ul>
 *   <li>结构失效 → 立即取消全部任务并丢弃输入，不退款；缓存产物保留；</li>
 *   <li>断电 → 默认暂停并把进度清零、保留输入与速度核心；配置可改为丢弃输入；</li>
 *   <li>AE 断开 → 任务继续，产物进入缓存，恢复后自动导入；</li>
 *   <li>速度核心按配方电压等级严格匹配，任务开始分配、结束释放。</li>
 * </ul>
 */
public final class MatrixRuntime {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String TAG_RUNTIME = "gtai_runtime";
    private static final String TAG_TASKS = "tasks";
    private static final String TAG_BUFFER = "buffer";
    private static final String TAG_NEXT_ID = "next_id";

    private final MatrixMonitorBlockEntity monitor;
    private final List<MatrixTask> tasks = new ArrayList<>();
    private final MatrixOutputBuffer outputBuffer = new MatrixOutputBuffer();
    private int nextTaskId = 1;

    public MatrixRuntime(MatrixMonitorBlockEntity monitor) {
        this.monitor = monitor;
    }

    /** 任务数量，供命令/界面使用。 */
    public int taskCount() {
        return tasks.size();
    }

    public boolean hasWork() {
        return !tasks.isEmpty() || !outputBuffer.isEmpty();
    }

    /**
     * 创建一个任务。输入已经由调用方从 ME 取出；返回 {@code false} 表示并发/处理核心额度不足。
     */
    public boolean startTask(MatrixPatternPlan plan, List<GenericStack> inputs) {
        if (!GtaiConfig.runtimeEnabled() || !monitor.isFormed() || plan == null || !plan.executable()) {
            return false;
        }
        if (tasks.size() >= GtaiConfig.maxConcurrentTasks()) {
            return false;
        }

        // GT 配方电压向下兼容：从最高电压核心开始找第一个还有并行额度的核心，
        // 这样高电压核心会被优先占用（并产生 GT 标准超频）。
        MatrixVoltage coreVoltage = selectCoreVoltage(plan);
        if (coreVoltage == null) {
            return false;
        }

        int freeSpeed = Math.max(0, totalSpeed(coreVoltage) - usedSpeed(coreVoltage));
        int assigned = Math.min(GtaiConfig.maxSpeedCoresPerTask(), freeSpeed);
        int duration = plan.durationFor(coreVoltage, assigned);
        long euPerTick = plan.euFor(coreVoltage);
        int ocLevel = plan.overclockLevel(coreVoltage);

        List<GenericStack> outputs = new ArrayList<>();
        for (GenericStack output : plan.outputs()) {
            if (output != null && output.amount() > 0) {
                outputs.add(output);
            }
        }

        tasks.add(new MatrixTask(nextTaskId++, plan.slot(), plan.recipeId(), plan.process(), coreVoltage,
                ocLevel, assigned, duration, euPerTick, inputs, outputs));
        monitor.setChanged();
        return true;
    }

    /**
     * 调试用：从槽位构建计划、从 ME 抽取输入并创建任务。
     *
     * @return 面向命令发送者的结果描述
     */
    public String startDebugTask(int slot) {
        if (!(monitor.getLevel() instanceof ServerLevel level)) {
            return "not a server level";
        }
        if (!monitor.isFormed()) {
            return "matrix is not formed";
        }
        MatrixStructureInfo info = monitor.structureInfo();
        if (info == null) {
            return "matrix info is unavailable";
        }
        MatrixPatternPlan plan = MatrixPatternPlan.create(level, info, slot, monitor.getPatternStack(slot));
        if (!plan.executable()) {
            return "pattern is not executable: " + plan.status().id();
        }
        List<GenericStack> inputs = extractInputs(plan.inputList());
        if (inputs == null) {
            return "not enough inputs in ME network";
        }
        return startTask(plan, inputs) ? "task started" : "no free parallel slot or speed core assignment failed";
    }

    /** 服务端 tick：推进任务、处理断电/完成、刷新离线缓存。 */
    public void tick(ServerLevel level) {
        if (tasks.isEmpty() && outputBuffer.isEmpty()) {
            return;
        }
        if (monitor.isStructureValidationPending()) {
            // 重进世界时区块可能还没加载完；先不动任务，等初步校验结束。
            return;
        }
        if (!monitor.isFormed()) {
            // 正常情况下 onStructureInvalid 已经清理过；这里兜底，避免残留任务在失效结构上继续跑。
            tasks.clear();
            return;
        }

        for (Iterator<MatrixTask> iterator = tasks.iterator(); iterator.hasNext();) {
            MatrixTask task = iterator.next();
            if (task.paused) {
                if (!canPay(level, task)) {
                    continue;
                }
                task.paused = false;
            }

            if (!canPay(level, task)) {
                if (GtaiConfig.onPowerLoss() == GtaiConfig.PowerLossPolicy.CONSUME_INPUTS) {
                    iterator.remove();
                } else {
                    task.resetProgress();
                }
                continue;
            }

            consumeEnergy(level, task);
            task.elapsed++;
            if (task.finished()) {
                iterator.remove();
                List<GenericStack> leftover = insertIntoNetwork(task.outputs);
                outputBuffer.addAll(leftover, level, monitor.getBlockPos());
            }
        }

        if (!outputBuffer.isEmpty() && monitor.isAeNetworkOperational()
                && level.getGameTime() % 20L == 0L) {
            outputBuffer.drainInto(this::insertOne);
        }
    }

    public void onStructureFormed() {
        // 读档/重新成型后任务保持暂停，下一 tick 由能量检查决定是否继续。
    }

    /** 结构失效：立即停止并丢弃任务输入，缓存产物保留。 */
    public void onStructureInvalid() {
        tasks.clear();
    }

    /** 监控器持久数据真正清理：缓存产物掉落到世界。 */
    public void onDataCleared() {
        tasks.clear();
        if (monitor.getLevel() instanceof ServerLevel level) {
            outputBuffer.dropAll(level, monitor.getBlockPos());
        }
    }

    public void onUnloaded() {
        // 任务与缓存随 NBT 持久化，区块卸载时不做清理。
    }

    public void save(CompoundTag root) {
        CompoundTag tag = new CompoundTag();
        ListTag taskList = new ListTag();
        for (MatrixTask task : tasks) {
            taskList.add(task.save());
        }
        tag.put(TAG_TASKS, taskList);
        tag.put(TAG_BUFFER, outputBuffer.save());
        tag.putInt(TAG_NEXT_ID, nextTaskId);
        root.put(TAG_RUNTIME, tag);
    }

    public void load(CompoundTag root) {
        if (!(monitor.getLevel() instanceof ServerLevel)) {
            return;
        }
        tasks.clear();
        outputBuffer.replace(List.of());
        if (!root.contains(TAG_RUNTIME, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag tag = root.getCompound(TAG_RUNTIME);
        ListTag taskList = tag.getList(TAG_TASKS, Tag.TAG_COMPOUND);
        int maxId = 0;
        for (int i = 0; i < taskList.size(); i++) {
            MatrixTask task = MatrixTask.load(taskList.getCompound(i));
            if (task != null) {
                tasks.add(task);
                maxId = Math.max(maxId, task.id);
            }
        }
        outputBuffer.load(tag.getList(TAG_BUFFER, Tag.TAG_COMPOUND));
        nextTaskId = Math.max(tag.getInt(TAG_NEXT_ID), maxId + 1);
    }

    /**
     * 构造信息页“运行状态”快照。
     *
     * <p>运行中的任务按 id 排序并最多发送 8 条；处理核心按“处理类型 + 电压”汇总，
     * 已超限的核心不会出现在并行总额度里，信息页会单独把它们列到“不可用”。</p>
     */
    public MatrixRuntimeView createView() {
        if (!(monitor.getLevel() instanceof ServerLevel level)) {
            return MatrixRuntimeView.EMPTY;
        }

        int running = 0;
        int paused = 0;
        long euPerTick = 0L;
        List<MatrixRuntimeView.TaskRuntime> taskViews = new ArrayList<>();
        List<MatrixTask> ordered = new ArrayList<>(tasks);
        ordered.sort(Comparator.comparing((MatrixTask task) -> task.paused)
                .thenComparingInt(task -> task.id));
        for (MatrixTask task : ordered) {
            if (task.paused) {
                paused++;
            } else {
                running++;
                euPerTick = saturatedAdd(euPerTick, task.euPerTick);
            }
            if (taskViews.size() < 8) {
                taskViews.add(new MatrixRuntimeView.TaskRuntime(task.slot, task.recipeId,
                        task.elapsed, task.duration, task.euPerTick, task.speedCores,
                        task.coreVoltage, task.ocLevel, task.paused));
            }
        }

        IEnergyContainer hatch = energyContainer(level);
        long energyStored = hatch == null ? 0L : hatch.getEnergyStored();
        long energyCapacity = hatch == null ? 0L : hatch.getEnergyCapacity();

        List<MatrixRuntimeView.ProcessRuntime> processes = new ArrayList<>();
        MatrixStructureInfo info = monitor.structureInfo();
        if (info != null) {
            for (MatrixStructureInfo.ProcessingCoreEntry entry : info.processingCores()) {
                int usable = usableEquivalents(info, entry);
                int total = Math.min(GtaiConfig.maxParallelPerVoltage(),
                        usable * GtaiConfig.parallelPerProcessingCore());
                int overloaded = Math.max(0, entry.count() - usable);
                processes.add(new MatrixRuntimeView.ProcessRuntime(entry.process(), entry.voltage(),
                        usedParallel(entry.process(), entry.voltage()), total, overloaded));
            }
        }

        List<MatrixRuntimeView.SpeedRuntime> speeds = new ArrayList<>();
        if (info != null) {
            for (MatrixStructureInfo.SpeedCoreEntry entry : info.speedCores()) {
                speeds.add(new MatrixRuntimeView.SpeedRuntime(entry.voltage(),
                        usedSpeed(entry.voltage()), entry.count()));
            }
        }

        return new MatrixRuntimeView(GtaiConfig.runtimeEnabled(), running, paused,
                GtaiConfig.maxConcurrentTasks(), euPerTick, energyStored, energyCapacity,
                outputBuffer.entryCount(), GtaiConfig.outputBufferEntries(),
                outputBuffer.itemEntries(), outputBuffer.fluidEntries(),
                processes, speeds, taskViews);
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    /** 供命令/调试查看的简短状态。 */
    public String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append("tasks=").append(tasks.size())
                .append(" buffered=").append(outputBuffer.snapshot().size());
        MatrixStructureInfo info = monitor.structureInfo();
        if (info != null) {
            builder.append(" providers=").append(info.patternProviderCount());
        }
        return builder.toString();
    }

    /** 当前已占用的“处理类型 + 电压”并行数。 */
    public int usedParallel(MatrixProcess process, MatrixVoltage voltage) {
        int used = 0;
        for (MatrixTask task : tasks) {
            if (task.process == process && task.coreVoltage == voltage) {
                used++;
            }
        }
        return used;
    }

    /**
     * 某“处理类型 + 电压”的并行总额度。
     *
     * <p>每个电压等级最多 {@code runtime.maxParallelPerVoltage} 并行（该电压下所有
     * 处理类型共享）；超出的核心等价数量不参与运行，并在信息页的“不可用”分区显示。</p>
     */
    public int totalParallel(MatrixProcess process, MatrixVoltage voltage) {
        MatrixStructureInfo info = monitor.structureInfo();
        if (info == null) {
            return 0;
        }
        for (MatrixStructureInfo.ProcessingCoreEntry entry : info.processingCores()) {
            if (entry.process() == process && entry.voltage() == voltage) {
                int perCore = GtaiConfig.parallelPerProcessingCore();
                return Math.min(GtaiConfig.maxParallelPerVoltage(),
                        usableEquivalents(info, entry) * perCore);
            }
        }
        return 0;
    }

    /**
     * 某处理核心条目在“整个电压等级最多 16 并行”约束下可用的等价数量。
     *
     * <p>同一电压下所有处理类型共享 16 并行；额度按各处理类型的核心数量比例分配，
     * 余数按 {@link MatrixStructureInfo#processingCores()} 顺序补齐，避免某一种处理类型
     * 把额度全部吃光。超出部分在信息页列为超限。</p>
     */
    private int usableEquivalents(MatrixStructureInfo info, MatrixStructureInfo.ProcessingCoreEntry target) {
        int perCore = GtaiConfig.parallelPerProcessingCore();
        int maxEquivalents = Math.max(1, GtaiConfig.maxParallelPerVoltage() / perCore);
        MatrixVoltage voltage = target.voltage();

        List<MatrixStructureInfo.ProcessingCoreEntry> sameVoltage = new ArrayList<>();
        int demand = 0;
        int targetIndex = -1;
        for (MatrixStructureInfo.ProcessingCoreEntry entry : info.processingCores()) {
            if (entry.voltage() != voltage) {
                continue;
            }
            if (entry.equals(target)) {
                targetIndex = sameVoltage.size();
            }
            sameVoltage.add(entry);
            demand += entry.count();
        }
        if (targetIndex < 0) {
            return 0;
        }
        if (demand <= maxEquivalents) {
            return target.count();
        }

        // 同一电压下按核心数量比例分配额度，避免前面的处理类型把额度吃光。
        int[] usable = new int[sameVoltage.size()];
        int assigned = 0;
        for (int i = 0; i < sameVoltage.size(); i++) {
            MatrixStructureInfo.ProcessingCoreEntry entry = sameVoltage.get(i);
            usable[i] = (int) Math.min(entry.count(),
                    (long) maxEquivalents * entry.count() / demand);
            assigned += usable[i];
        }
        int remaining = maxEquivalents - assigned;
        for (int i = 0; i < sameVoltage.size() && remaining > 0; i++) {
            if (usable[i] < sameVoltage.get(i).count()) {
                usable[i]++;
                remaining--;
                // 一轮不够时从头再来，保证剩余额度分完
                if (i == sameVoltage.size() - 1 && remaining > 0) {
                    i = -1;
                }
            }
        }
        return usable[targetIndex];
    }

    /** 某处理核心条目超出该电压并行上限的等价数量。 */
    public int overloadedEquivalents(MatrixStructureInfo.ProcessingCoreEntry entry) {
        MatrixStructureInfo info = monitor.structureInfo();
        if (info == null || entry == null) {
            return 0;
        }
        return Math.max(0, entry.count() - usableEquivalents(info, entry));
    }

    /** 选择可运行该配方的最高电压核心；没有可用并行时返回 {@code null}。 */
    @Nullable
    private MatrixVoltage selectCoreVoltage(MatrixPatternPlan plan) {
        MatrixVoltage[] values = MatrixVoltage.values();
        for (int i = values.length - 1; i >= 0; i--) {
            MatrixVoltage voltage = values[i];
            if (MatrixVoltageTier.of(voltage) < plan.recipeTier()) {
                continue;
            }
            int total = totalParallel(plan.process(), voltage);
            if (total > 0 && usedParallel(plan.process(), voltage) < total) {
                return voltage;
            }
        }
        return null;
    }

    private int totalSpeed(MatrixVoltage tier) {
        MatrixStructureInfo info = monitor.structureInfo();
        if (info == null) {
            return 0;
        }
        for (MatrixStructureInfo.SpeedCoreEntry entry : info.speedCores()) {
            if (entry.voltage() == tier) {
                return entry.count();
            }
        }
        return 0;
    }

    private int usedSpeed(MatrixVoltage tier) {
        int used = 0;
        for (MatrixTask task : tasks) {
            if (task.coreVoltage == tier) {
                used += task.speedCores;
            }
        }
        return used;
    }

    private boolean canPay(ServerLevel level, MatrixTask task) {
        if (task.euPerTick <= 0L) {
            return true;
        }
        IEnergyContainer container = energyContainer(level);
        return container != null && container.getEnergyStored() >= task.euPerTick;
    }

    private void consumeEnergy(ServerLevel level, MatrixTask task) {
        if (task.euPerTick <= 0L) {
            return;
        }
        IEnergyContainer container = energyContainer(level);
        if (container != null) {
            container.removeEnergy(task.euPerTick);
        }
    }

    @Nullable
    private IEnergyContainer energyContainer(ServerLevel level) {
        List<BlockPos> hatches = monitor.energyHatches();
        if (hatches.isEmpty()) {
            return null;
        }
        return GTCapabilityHelper.getEnergyContainer(level, hatches.get(0), null);
    }

    /**
     * 尝试把产物插入 ME；返回未能插入的余量。
     */
    private List<GenericStack> insertIntoNetwork(List<GenericStack> stacks) {
        List<GenericStack> remaining = new ArrayList<>();
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            long inserted = insertOne(stack);
            long left = stack.amount() - inserted;
            if (left > 0) {
                remaining.add(new GenericStack(stack.what(), left));
            }
        }
        return remaining;
    }

    private long insertOne(GenericStack stack) {
        IGrid grid = monitor.getActiveGrid();
        if (grid == null || !monitor.isAeNetworkOperational()) {
            return 0L;
        }
        try {
            var storageService = grid.getStorageService();
            if (storageService == null) {
                return 0L;
            }
            MEStorage storage = storageService.getInventory();
            return Math.max(0L, storage.insert(stack.what(), stack.amount(), Actionable.MODULATE,
                    IActionSource.empty()));
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to insert matrix output {} into AE storage", stack, e);
            return 0L;
        }
    }

    /**
     * 调试用：从 ME 抽取样板声明的输入；先模拟全部，再实际抽取，避免半途失败吞材料。
     */
    @Nullable
    private List<GenericStack> extractInputs(List<GenericStack> wanted) {
        IGrid grid = monitor.getActiveGrid();
        if (grid == null) {
            return null;
        }
        var storageService = grid.getStorageService();
        if (storageService == null) {
            return null;
        }
        MEStorage storage = storageService.getInventory();
        IActionSource source = IActionSource.empty();
        for (GenericStack stack : wanted) {
            if (storage.extract(stack.what(), stack.amount(), Actionable.SIMULATE, source) < stack.amount()) {
                return null;
            }
        }

        List<GenericStack> taken = new ArrayList<>();
        for (GenericStack stack : wanted) {
            long got = storage.extract(stack.what(), stack.amount(), Actionable.MODULATE, source);
            if (got < stack.amount()) {
                // 回滚已经取出的部分
                for (GenericStack previous : taken) {
                    storage.insert(previous.what(), previous.amount(), Actionable.MODULATE, source);
                }
                return null;
            }
            taken.add(new GenericStack(stack.what(), got));
        }
        return taken;
    }
}
