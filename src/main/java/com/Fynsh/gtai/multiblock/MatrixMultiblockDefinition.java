package com.Fynsh.gtai.multiblock;

import java.util.Objects;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import com.Fynsh.gtai.block.MatrixFamily;
import com.Fynsh.gtai.block.MatrixProcess;
import com.Fynsh.gtai.config.GtaiConfig;

/**
 * 一种矩阵多方块的结构定义。
 *
 * <p>定义只负责“这类结构允许哪些处理核心、属于哪个外壳类型”，具体几何/方块校验在
 * {@link MatrixStructureValidator}。新增结构时注册一个新的定义即可，不需要改扫描器。</p>
 *
 * <p>尺寸上限/下限来自 {@link GtaiConfig}，对所有定义生效；这两个方法保留为非 final，
 * 方便后续为特殊结构覆盖。</p>
 */
public class MatrixMultiblockDefinition {

    private final ResourceLocation id;
    private final MatrixFamily family;
    private final Set<MatrixProcess> processes;

    public MatrixMultiblockDefinition(ResourceLocation id, MatrixFamily family, Set<MatrixProcess> processes) {
        this.id = Objects.requireNonNull(id, "id");
        this.family = Objects.requireNonNull(family, "family");
        this.processes = Set.copyOf(processes);
        if (this.processes.isEmpty()) {
            throw new IllegalArgumentException("Matrix multiblock " + id + " must allow at least one process");
        }
    }

    public ResourceLocation id() {
        return id;
    }

    /** 该结构要求的外壳类型。 */
    public MatrixFamily family() {
        return family;
    }

    public Set<MatrixProcess> processes() {
        return processes;
    }

    public boolean acceptsProcess(MatrixProcess process) {
        return processes.contains(process);
    }

    public int minSize() {
        return GtaiConfig.minSize();
    }

    public int maxSize() {
        return GtaiConfig.maxSize();
    }

    /** 给定六个边界，判断该结构是否成型。 */
    public boolean isFormed(Level level, BlockPos min, BlockPos max) {
        return MatrixStructureValidator.validate(this, level, new MatrixStructureBounds(min, max));
    }

    /** 从一个结构方块出发，先找边界再判断是否成型。 */
    public boolean isFormedAt(Level level, BlockPos anchor) {
        return MatrixStructureScanner.findBounds(level, anchor)
                .map(bounds -> isFormed(level, bounds.min(), bounds.max()))
                .orElse(false);
    }
}
