package com.Fynsh.gtai.runtime;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.stacks.GenericStack;

import com.Fynsh.gtai.block.MatrixProcess;
import com.Fynsh.gtai.block.MatrixVoltage;

/**
 * 一次矩阵配方执行的运行时快照。
 *
 * <p>输入在任务创建时已经从 ME 网络取出（或由调试命令取出），因此任务本身不再
 * 依赖网络；结构破坏时直接丢弃这些输入，断电时按策略保留或丢弃。</p>
 */
final class MatrixTask {

    private static final String TAG_ID = "id";
    private static final String TAG_SLOT = "slot";
    private static final String TAG_RECIPE = "recipe";
    private static final String TAG_PROCESS = "process";
    private static final String TAG_CORE_VOLTAGE = "core_voltage";
    private static final String TAG_OC_LEVEL = "oc_level";
    private static final String TAG_SPEED_CORES = "speed_cores";
    private static final String TAG_DURATION = "duration";
    private static final String TAG_ELAPSED = "elapsed";
    private static final String TAG_EU = "eu";
    private static final String TAG_PAUSED = "paused";
    private static final String TAG_INPUTS = "inputs";
    private static final String TAG_OUTPUTS = "outputs";

    final int id;
    final int slot;
    final String recipeId;
    final MatrixProcess process;
    @Nullable
    final MatrixVoltage coreVoltage;
    final int ocLevel;
    final int speedCores;
    final int duration;
    final long euPerTick;
    final List<GenericStack> inputs;
    final List<GenericStack> outputs;

    int elapsed;
    boolean paused;

    MatrixTask(int id, int slot, String recipeId, MatrixProcess process, @Nullable MatrixVoltage coreVoltage,
            int ocLevel, int speedCores, int duration, long euPerTick, List<GenericStack> inputs,
            List<GenericStack> outputs) {
        this.id = id;
        this.slot = slot;
        this.recipeId = recipeId == null ? "" : recipeId;
        this.process = process;
        this.coreVoltage = coreVoltage;
        this.ocLevel = Math.max(0, ocLevel);
        this.speedCores = Math.max(0, speedCores);
        this.duration = Math.max(1, duration);
        this.euPerTick = Math.max(0L, euPerTick);
        this.inputs = copyStacks(inputs);
        this.outputs = copyStacks(outputs);
    }

    private static List<GenericStack> copyStacks(List<GenericStack> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<GenericStack> copy = new ArrayList<>();
        for (GenericStack stack : source) {
            if (stack != null && stack.amount() > 0) {
                copy.add(stack);
            }
        }
        return List.copyOf(copy);
    }

    /** 断电默认策略：进度清零并暂停，输入与速度核心继续保留。 */
    void resetProgress() {
        this.elapsed = 0;
        this.paused = true;
    }

    boolean finished() {
        return elapsed >= duration;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_ID, id);
        tag.putInt(TAG_SLOT, slot);
        tag.putString(TAG_RECIPE, recipeId);
        tag.putString(TAG_PROCESS, process.id());
        if (coreVoltage != null) {
            tag.putString(TAG_CORE_VOLTAGE, coreVoltage.id());
        }
        tag.putInt(TAG_OC_LEVEL, ocLevel);
        tag.putInt(TAG_SPEED_CORES, speedCores);
        tag.putInt(TAG_DURATION, duration);
        tag.putInt(TAG_ELAPSED, elapsed);
        tag.putLong(TAG_EU, euPerTick);
        tag.putBoolean(TAG_PAUSED, paused);
        tag.put(TAG_INPUTS, writeStacks(inputs));
        tag.put(TAG_OUTPUTS, writeStacks(outputs));
        return tag;
    }

    @Nullable
    static MatrixTask load(CompoundTag tag) {
        MatrixProcess process = MatrixProcess.byIdOrNull(tag.getString(TAG_PROCESS));
        if (process == null) {
            return null;
        }
        MatrixVoltage coreVoltage = tag.contains(TAG_CORE_VOLTAGE, Tag.TAG_STRING)
                ? MatrixVoltage.byIdOrNull(tag.getString(TAG_CORE_VOLTAGE))
                : null;
        List<GenericStack> inputs = readStacks(tag.getList(TAG_INPUTS, Tag.TAG_COMPOUND));
        List<GenericStack> outputs = readStacks(tag.getList(TAG_OUTPUTS, Tag.TAG_COMPOUND));
        MatrixTask task = new MatrixTask(
                tag.getInt(TAG_ID),
                tag.getInt(TAG_SLOT),
                tag.getString(TAG_RECIPE),
                process,
                coreVoltage,
                tag.getInt(TAG_OC_LEVEL),
                tag.getInt(TAG_SPEED_CORES),
                tag.getInt(TAG_DURATION),
                tag.getLong(TAG_EU),
                inputs,
                outputs);
        task.elapsed = Math.max(0, tag.getInt(TAG_ELAPSED));
        // 读档后一律先暂停，等结构校验与能量检查通过后再恢复。
        task.paused = true;
        return task;
    }

    static ListTag writeStacks(List<GenericStack> stacks) {
        ListTag list = new ListTag();
        for (GenericStack stack : stacks) {
            if (stack != null && stack.amount() > 0) {
                list.add(GenericStack.writeTag(stack));
            }
        }
        return list;
    }

    static List<GenericStack> readStacks(ListTag list) {
        List<GenericStack> stacks = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            try {
                GenericStack stack = GenericStack.readTag(list.getCompound(i));
                if (stack != null && stack.amount() > 0) {
                    stacks.add(stack);
                }
            } catch (RuntimeException ignored) {
                // 单条损坏不影响其它条目
            }
        }
        return stacks;
    }
}
