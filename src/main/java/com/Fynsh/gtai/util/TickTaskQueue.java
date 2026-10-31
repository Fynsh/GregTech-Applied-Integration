package com.Fynsh.gtai.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 按 gameTime 触发的延迟任务队列。
 *
 * <p>矩阵监控器与矩阵框架都需要“第 N tick 再做一次”的能力（结构恢复、线缆重连、
 * 数据延迟清理）。两处原本各写了一份完全相同的实现，这里合并成一份，语义保持不变：
 * 任务保存的是<b>绝对</b>触发时间点，{@link #runDue(long)} 只执行 {@code now >= at} 的任务；
 * 执行过程中新入队的任务留到下一轮。</p>
 *
 * <p>只在服务端线程访问，因此不做同步。</p>
 */
public final class TickTaskQueue {

    private final List<Task> tasks = new ArrayList<>();

    /**
     * 以 {@code now + delay} 为触发时间点加入一个任务。
     *
     * @param now    当前 gameTime
     * @param delay  延迟 tick 数；0 表示下一次 {@link #runDue(long)} 就执行
     * @param action 到期后执行的动作
     */
    public void schedule(long now, int delay, Runnable action) {
        tasks.add(new Task(now + delay, action));
    }

    /** 执行全部已到期的任务。 */
    public void runDue(long now) {
        if (tasks.isEmpty()) {
            return;
        }
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Task> iterator = tasks.iterator(); iterator.hasNext();) {
            Task task = iterator.next();
            if (now >= task.at()) {
                iterator.remove();
                due.add(task.action());
            }
        }
        for (Runnable action : due) {
            action.run();
        }
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }

    /** 丢弃全部未执行的任务（方块实体移除/区块卸载时调用）。 */
    public void clear() {
        tasks.clear();
    }

    private record Task(long at, Runnable action) {
    }
}
