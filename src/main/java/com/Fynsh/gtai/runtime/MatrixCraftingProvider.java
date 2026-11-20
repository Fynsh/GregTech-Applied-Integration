package com.Fynsh.gtai.runtime;

import java.util.ArrayList;
import java.util.List;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.world.item.ItemStack;

import com.Fynsh.gtai.blockentity.MatrixMonitorBlockEntity;
import com.Fynsh.gtai.config.GtaiConfig;

/**
 * 矩阵对 AE 暴露的样板供应器。
 *
 * <p>只把 {@link MatrixMonitorBlockEntity#executablePatternSlots()} 里的样板交给 AE；
 * 不可执行（缺处理核心、超净间类型不符、电压不足、配方不匹配）的样板不会出现在
 * {@link #getAvailablePatterns()} 中。</p>
 *
 * <p>AE 在调用 {@link #pushPattern} 之前已经从网络扣除了输入；本类只负责校验、
 * 找到对应样板槽并创建任务。返回 {@code false} 时 AE 会自行把输入退回。</p>
 */
public final class MatrixCraftingProvider implements ICraftingProvider {

    private final MatrixMonitorBlockEntity monitor;

    private List<IPatternDetails> cachedPatterns;
    private int cachedVersion = -1;

    public MatrixCraftingProvider(MatrixMonitorBlockEntity monitor) {
        this.monitor = monitor;
    }

    /** 样板可执行性/库存变化后使缓存失效。 */
    public void invalidate() {
        cachedPatterns = null;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        // 不要依赖 isAeNetworkOperational()：它包含 framePorts/linkedChannels 的统计，
        // 在 AE 刚索引 provider 时可能还没更新；provider 节点本身处于活动网络即可。
        if (!GtaiConfig.runtimeEnabled() || !monitor.isFormed() || monitor.getActiveGrid() == null) {
            return List.of();
        }
        int version = monitor.patternStatusVersion();
        if (cachedPatterns != null && cachedVersion == version) {
            return cachedPatterns;
        }

        List<IPatternDetails> patterns = new ArrayList<>();
        for (int slot : monitor.executablePatternSlots()) {
            ItemStack stack = monitor.getPatternStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            try {
                IPatternDetails details = PatternDetailsHelper.decodePattern(stack, monitor.getLevel());
                if (details != null && details.getOutputs() != null) {
                    patterns.add(details);
                }
            } catch (RuntimeException ignored) {
                // 单条样板解码失败不影响其它样板
            }
        }
        cachedPatterns = List.copyOf(patterns);
        cachedVersion = version;
        return cachedPatterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        if (!GtaiConfig.runtimeEnabled() || !monitor.isFormed() || monitor.getActiveGrid() == null) {
            return false;
        }
        if (pattern == null || inputs == null) {
            return false;
        }

        int slot = findSlot(pattern);
        if (slot < 0) {
            return false;
        }
        MatrixPatternPlan plan = MatrixPatternPlan.create(monitor.getLevel(), monitor.structureInfo(),
                slot, monitor.getPatternStack(slot));
        if (!plan.executable()) {
            return false;
        }

        List<GenericStack> actual = new ArrayList<>();
        for (KeyCounter counter : inputs) {
            if (counter == null) {
                continue;
            }
            for (var entry : counter) {
                if (entry.getLongValue() > 0) {
                    actual.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                }
            }
        }

        return monitor.runtime().startTask(plan, actual);
    }

    @Override
    public boolean isBusy() {
        return monitor.runtime().taskCount() >= GtaiConfig.maxConcurrentTasks();
    }

    private int findSlot(IPatternDetails pattern) {
        AEItemKey definition = pattern.getDefinition();
        if (definition == null) {
            return -1;
        }
        for (int slot : monitor.executablePatternSlots()) {
            AEItemKey key = AEItemKey.of(monitor.getPatternStack(slot));
            if (definition.equals(key)) {
                return slot;
            }
        }
        return -1;
    }
}
