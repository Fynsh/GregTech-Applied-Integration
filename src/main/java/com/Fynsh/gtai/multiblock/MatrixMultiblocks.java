package com.Fynsh.gtai.multiblock;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.Fynsh.gtai.GTAIMod;
import com.Fynsh.gtai.block.MatrixBlock;
import com.Fynsh.gtai.block.MatrixBlockKind;
import com.Fynsh.gtai.block.MatrixBlockRole;
import com.Fynsh.gtai.block.MatrixFamily;
import com.Fynsh.gtai.block.MatrixProcess;

/**
 * 本模组全部矩阵多方块定义的注册表。
 *
 * <p>按“族”注册四种结构：组装、电路组装、材料、流体材料。每个定义声明该族允许的
 * 处理核心类型；成型判定仍然是统一的几何/内容校验，只是最后按定义检查外壳类型。</p>
 *
 * <p>本类只做结构定义注册与 boolean 判定，不注册 GT 多方块、不建立 AE2 网络、
 * 不实现 tick/GUI/能量/配方/交互。</p>
 */
public final class MatrixMultiblocks {

    private static final Map<ResourceLocation, MatrixMultiblockDefinition> BY_ID = new LinkedHashMap<>();
    private static final Map<MatrixFamily, MatrixMultiblockDefinition> BY_FAMILY = new EnumMap<>(MatrixFamily.class);
    private static boolean registered;

    private MatrixMultiblocks() {
    }

    /**
     * 注册四种结构定义。由 {@link GTAIMod} 在模组构造阶段调用；重复调用无副作用。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        register(new MatrixMultiblockDefinition(GTAIMod.id("assembly_matrix"), MatrixFamily.ASSEMBLY,
                EnumSet.of(MatrixProcess.ASSEMBLY)));
        register(new MatrixMultiblockDefinition(GTAIMod.id("circuit_assembly_matrix"), MatrixFamily.CIRCUIT_ASSEMBLY,
                EnumSet.of(MatrixProcess.CIRCUIT_ASSEMBLY)));
        register(new MatrixMultiblockDefinition(GTAIMod.id("material_matrix"), MatrixFamily.MATERIAL,
                EnumSet.of(MatrixProcess.CUTTING, MatrixProcess.PULVERIZING, MatrixProcess.FORMING)));
        register(new MatrixMultiblockDefinition(GTAIMod.id("fluid_material_matrix"), MatrixFamily.FLUID_MATERIAL,
                EnumSet.of(MatrixProcess.SOLIDIFYING, MatrixProcess.EXTRACTING)));
    }

    /**
     * 注册一个额外的结构定义。
     *
     * <p>预置的四种结构由 {@link #register()} 注册；后续若增加新的矩阵族，可以调用本方法，
     * 不需要修改扫描器或验证器。</p>
     */
    public static synchronized void register(MatrixMultiblockDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Matrix multiblock definition must not be null");
        }
        if (BY_ID.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("Duplicate matrix multiblock id: " + definition.id());
        }
        if (BY_FAMILY.putIfAbsent(definition.family(), definition) != null) {
            throw new IllegalStateException("Duplicate matrix multiblock family: " + definition.family());
        }
    }

    /** 按外壳类型取定义。 */
    public static Optional<MatrixMultiblockDefinition> forFamily(MatrixFamily family) {
        register();
        return Optional.ofNullable(BY_FAMILY.get(family));
    }

    /**
     * 给定六个边界，返回第一个匹配的结构结果，包含框架外观和能源仓位置。
     */
    public static Optional<MatrixStructureMatch> findMatch(Level level, BlockPos min, BlockPos max) {
        register();
        MatrixStructureBounds bounds = new MatrixStructureBounds(min, max);

        // 合法结构的最小角一定是矩阵框架，而框架类型唯一决定结构定义。
        // 先据此选定定义，避免像原来那样对四种定义各做一次整结构扫描。
        MatrixMultiblockDefinition preferred = definitionAt(level, bounds.min());
        if (preferred != null) {
            return Optional.ofNullable(MatrixStructureValidator.match(preferred, level, bounds));
        }

        // 最小角不是框架（仍在搭建、或调用方传入任意包围盒）时保留原行为：
        // 依次尝试全部定义，第一个匹配的胜出。
        for (MatrixMultiblockDefinition definition : BY_ID.values()) {
            MatrixStructureMatch match = MatrixStructureValidator.match(definition, level, bounds);
            if (match != null) {
                return Optional.of(match);
            }
        }
        return Optional.empty();
    }

    /**
     * 读取位置上的框架类型并反查结构定义；不是框架或类型未知时返回 {@code null}。
     *
     * <p>只读取一个方块，不触发扫描；未知类型走完整回退路径，保持原有容错行为。</p>
     */
    @Nullable
    private static MatrixMultiblockDefinition definitionAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MatrixBlock block)) {
            return null;
        }
        MatrixBlockKind kind = block.kind();
        if (kind.role() != MatrixBlockRole.FRAME || kind.family() == null) {
            return null;
        }
        return BY_FAMILY.get(kind.family());
    }

    /** 给定六个边界，返回匹配的定义；不匹配返回空。 */
    public static Optional<MatrixMultiblockDefinition> findFormed(Level level, BlockPos min, BlockPos max) {
        return findMatch(level, min, max).map(MatrixStructureMatch::definition);
    }

    /** 给定六个边界，返回是否成型。这是本阶段要求的 boolean 判定入口。 */
    public static boolean checkFormed(Level level, BlockPos min, BlockPos max) {
        return findMatch(level, min, max).isPresent();
    }

    /** 从一个结构方块出发，自动找边界并返回完整结果。 */
    public static Optional<MatrixStructureMatch> findMatchAt(Level level, BlockPos anchor) {
        register();
        return MatrixStructureScanner.findBounds(level, anchor)
                .flatMap(bounds -> findMatch(level, bounds.min(), bounds.max()));
    }

    /** 从一个结构方块出发，返回匹配的定义。 */
    public static Optional<MatrixMultiblockDefinition> findFormedAt(Level level, BlockPos anchor) {
        return findMatchAt(level, anchor).map(MatrixStructureMatch::definition);
    }

    /** 从一个结构方块出发，返回是否成型。 */
    public static boolean checkAt(Level level, BlockPos anchor) {
        return findMatchAt(level, anchor).isPresent();
    }

    /** 全部定义（只读）。 */
    public static List<MatrixMultiblockDefinition> all() {
        register();
        return Collections.unmodifiableList(List.copyOf(BY_ID.values()));
    }
}
