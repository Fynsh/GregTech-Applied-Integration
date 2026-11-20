package com.Fynsh.gtai.runtime;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.Fynsh.gtai.config.GtaiConfig;

/**
 * 矩阵产物的离线缓存。
 *
 * <p>AE 断开或网络存储已满时，产物先进入这里；AE 恢复后由运行时定期尝试导入。
 * 容量按“不同的 GenericStack 条目数”计算，同一物品/流体的数量会合并。
 * 条目满时：物品掉落到监控器位置，流体直接销毁（没有对应的掉落物）。</p>
 */
final class MatrixOutputBuffer {

    private final List<GenericStack> entries = new ArrayList<>();

    boolean isEmpty() {
        return entries.isEmpty();
    }

    int entryCount() {
        return entries.size();
    }

    int itemEntries() {
        int count = 0;
        for (GenericStack stack : entries) {
            if (stack.what() instanceof AEItemKey) {
                count++;
            }
        }
        return count;
    }

    int fluidEntries() {
        int count = 0;
        for (GenericStack stack : entries) {
            if (stack.what() instanceof AEFluidKey) {
                count++;
            }
        }
        return count;
    }

    List<GenericStack> snapshot() {
        return List.copyOf(entries);
    }

    void replace(List<GenericStack> stacks) {
        entries.clear();
        if (stacks != null) {
            for (GenericStack stack : stacks) {
                if (stack != null && stack.amount() > 0) {
                    entries.add(stack);
                }
            }
        }
    }

    /**
     * 把产物加入缓存；条目数超过配置上限时按条目顺序掉落/销毁。
     */
    void addAll(List<GenericStack> stacks, ServerLevel level, BlockPos pos) {
        if (stacks == null || stacks.isEmpty()) {
            return;
        }
        int capacity = GtaiConfig.outputBufferEntries();
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            int existing = indexOf(stack.what());
            if (existing >= 0) {
                entries.set(existing, merge(entries.get(existing), stack.amount()));
                continue;
            }
            if (entries.size() < capacity) {
                entries.add(stack);
            } else {
                drop(stack, level, pos);
            }
        }
    }

    /** 把缓存交给网络导入；返回未能导入的余量。 */
    List<GenericStack> drainInto(java.util.function.Function<GenericStack, Long> inserter) {
        List<GenericStack> remaining = new ArrayList<>();
        for (GenericStack stack : entries) {
            long inserted = Math.max(0L, inserter.apply(stack));
            long left = stack.amount() - inserted;
            if (left > 0) {
                remaining.add(new GenericStack(stack.what(), left));
            }
        }
        entries.clear();
        entries.addAll(remaining);
        return remaining;
    }

    void dropAll(ServerLevel level, BlockPos pos) {
        for (GenericStack stack : entries) {
            drop(stack, level, pos);
        }
        entries.clear();
    }

    ListTag save() {
        return MatrixTask.writeStacks(entries);
    }

    void load(ListTag tag) {
        entries.clear();
        entries.addAll(MatrixTask.readStacks(tag));
    }

    private int indexOf(AEKey key) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).what().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static GenericStack merge(GenericStack existing, long extra) {
        long total;
        try {
            total = Math.addExact(existing.amount(), extra);
        } catch (ArithmeticException e) {
            total = Long.MAX_VALUE;
        }
        return new GenericStack(existing.what(), total);
    }

    private static void drop(GenericStack stack, ServerLevel level, BlockPos pos) {
        if (stack == null || stack.amount() <= 0 || level == null) {
            return;
        }
        if (stack.what() instanceof AEFluidKey) {
            // 流体没有可掉落的物品形态，按约定直接销毁。
            return;
        }
        List<ItemStack> drops = new ArrayList<>();
        stack.what().addDrops(stack.amount(), drops, level, pos);
        for (ItemStack drop : drops) {
            if (drop != null && !drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
        }
    }
}
