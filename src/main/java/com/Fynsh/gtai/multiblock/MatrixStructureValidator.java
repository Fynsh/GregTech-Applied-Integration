package com.Fynsh.gtai.multiblock;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.Fynsh.gtai.block.MatrixBlock;
import com.Fynsh.gtai.block.MatrixBlockKind;
import com.Fynsh.gtai.block.MatrixBlockRole;
import com.Fynsh.gtai.block.MatrixFamily;
import com.Fynsh.gtai.block.MatrixShellLevel;

/**
 * 矩阵多方块成型判定。
 *
 * <p>判定本身不修改世界、不建立任务/网络/GUI。结构必须</p>
 * <ul>
 *   <li>是轴对齐实心长方体，尺寸含外壳，边长在配置的 3..16 内；</li>
 *   <li>外壳只含框架、玻璃、能源仓、过滤器；顶点只能是框架；棱只能是框架或能源仓；</li>
 *   <li>恰好一个能源仓，且电压等级至少为 HV；</li>
 *   <li>框架同等级同类型，玻璃若存在则与框架同等级；</li>
 *   <li>内部无空气/其它方块，恰好一个监控器，最多一个超净间；</li>
 *   <li>样板核心、速度核心、监控器核心与外壳同类型；核心若带等级则还必须同等级；
 *       处理核心按映射匹配外壳类型。</li>
 * </ul>
 */
public final class MatrixStructureValidator {

    private MatrixStructureValidator() {
    }

    /** 兼容原有 boolean 入口。 */
    public static boolean validate(MatrixMultiblockDefinition definition, Level level, MatrixStructureBounds bounds) {
        return match(definition, level, bounds) != null;
    }

    /**
     * 执行完整判定；成功时返回结构边界、框架外观、能源仓位置与唯一监控器位置，失败返回 {@code null}。
     */
    @Nullable
    public static MatrixStructureMatch match(MatrixMultiblockDefinition definition, Level level,
            MatrixStructureBounds bounds) {
        final int minSize = definition.minSize();
        final int maxSize = definition.maxSize();

        if (bounds.sizeX() < minSize || bounds.sizeY() < minSize || bounds.sizeZ() < minSize) {
            return null;
        }
        if (bounds.sizeX() > maxSize || bounds.sizeY() > maxSize || bounds.sizeZ() > maxSize) {
            return null;
        }

        Tracker tracker = new Tracker(definition, bounds, level);

        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int y = bounds.min().getY(); y <= bounds.max().getY(); y++) {
                for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    boolean energyHatch = MatrixEnergyHatch.isEnergyHatch(state);
                    MatrixBlockKind kind = state.getBlock() instanceof MatrixBlock block ? block.kind() : null;

                    int boundary = bounds.boundaryCount(pos);
                    boolean accepted;
                    if (boundary == 3) {
                        accepted = tracker.shellVertex(kind, energyHatch);
                    } else if (boundary == 2) {
                        accepted = tracker.shellEdge(kind, energyHatch);
                    } else if (boundary == 1) {
                        accepted = tracker.shellFace(kind, energyHatch);
                    } else {
                        accepted = tracker.interior(pos, kind, energyHatch);
                    }

                    if (!accepted) {
                        return null;
                    }

                    if (kind != null && kind.role() == MatrixBlockRole.FRAME) {
                        tracker.recordFrame(state);
                    }
                    if (energyHatch) {
                        tracker.recordEnergyHatch(pos);
                    }
                }
            }
        }

        return tracker.finish();
    }

    /** 一次扫描的可变状态；避免为每个格子创建对象。 */
    private static final class Tracker {

        private final MatrixMultiblockDefinition definition;
        private final MatrixStructureBounds bounds;
        private final Level level;
        private MatrixFamily frameFamily;
        private MatrixShellLevel frameLevel;
        private MatrixShellLevel glassLevel;
        private BlockState frameState;
        private final List<BlockPos> energyHatches = new ArrayList<>();
        private BlockPos monitorPos;
        private int monitors;
        private int cleanrooms;

        private Tracker(MatrixMultiblockDefinition definition, MatrixStructureBounds bounds, Level level) {
            this.definition = definition;
            this.bounds = bounds;
            this.level = level;
        }

        private boolean shellVertex(MatrixBlockKind kind, boolean energyHatch) {
            // 8 个顶点只允许矩阵框架，能源仓/玻璃/过滤器都不行
            return !energyHatch && kind != null && kind.role() == MatrixBlockRole.FRAME && acceptFrame(kind);
        }

        private boolean shellEdge(MatrixBlockKind kind, boolean energyHatch) {
            // 棱的非顶点段只允许框架或能源仓；过滤器/玻璃不可
            if (energyHatch) {
                return true;
            }
            return kind != null && kind.role() == MatrixBlockRole.FRAME && acceptFrame(kind);
        }

        private boolean shellFace(MatrixBlockKind kind, boolean energyHatch) {
            // 普通表面允许框架、玻璃、能源仓、过滤器
            if (energyHatch) {
                return true;
            }
            if (kind == null) {
                return false;
            }
            return switch (kind.role()) {
                case FRAME -> acceptFrame(kind);
                case GLASS -> acceptGlass(kind);
                case FILTER, STERILE_FILTER -> true;
                default -> false;
            };
        }

        private boolean interior(BlockPos pos, MatrixBlockKind kind, boolean energyHatch) {
            // 内部不得为空气/其它方块，也不允许能源仓或任何外壳方块
            if (energyHatch || kind == null) {
                return false;
            }
            return switch (kind.role()) {
                case EMPTY_CORE -> true;
                case PATTERN_CORE -> matchesShell(kind);
                case SPEED_CORE -> matchesShell(kind);
                case PROCESSING_CORE -> kind.process() != null && definition.acceptsProcess(kind.process());
                case MONITOR -> {
                    // 与样板核心/速度核心一致：监控器核心的种类必须与外壳相同，
                    // 否则“材料监控器 + 组装外壳”也会被判定成型。
                    if (!matchesShell(kind)) {
                        yield false;
                    }
                    monitors++;
                    // 结构只允许一个监控器；这里记录位置供成型信息写入使用
                    monitorPos = pos.immutable();
                    yield true;
                }
                case TRANSFORMER, ACTIVE_TRANSFORMER -> true;
                case CLEANROOM -> {
                    cleanrooms++;
                    yield true;
                }
                default -> false;
            };
        }

        private boolean acceptFrame(MatrixBlockKind kind) {
            if (kind.family() == null || kind.level() == null) {
                return false;
            }
            if (frameFamily == null) {
                frameFamily = kind.family();
                frameLevel = kind.level();
                return true;
            }
            return frameFamily == kind.family() && frameLevel == kind.level();
        }

        private boolean acceptGlass(MatrixBlockKind kind) {
            if (kind.level() == null) {
                return false;
            }
            if (glassLevel == null) {
                glassLevel = kind.level();
                return true;
            }
            return glassLevel == kind.level();
        }

        private boolean matchesShell(MatrixBlockKind kind) {
            if (frameFamily == null || kind.family() != frameFamily) {
                return false;
            }
            // 方块表目前没有给样板核心/速度核心提供等级变体，因此它们的 level 为空；
            // 这种情况下只校验类型。若后续补充了等级变体，level 非空时严格校验等级。
            return kind.level() == null || kind.level() == frameLevel;
        }

        private void recordFrame(BlockState state) {
            if (frameState == null) {
                frameState = state;
            }
        }

        private void recordEnergyHatch(BlockPos pos) {
            energyHatches.add(pos.immutable());
        }

        @Nullable
        private MatrixStructureMatch finish() {
            if (energyHatches.size() != 1 || monitors != 1 || monitorPos == null || cleanrooms > 1) {
                return null;
            }
            // 硬性最低电压门槛：LV/MV 能源仓不允许矩阵成型
            int hatchTier = MatrixEnergyHatch.voltageTier(level.getBlockState(energyHatches.get(0)));
            if (hatchTier < MatrixVoltageTier.HV) {
                return null;
            }
            if (frameFamily == null || frameLevel == null || frameState == null) {
                return null;
            }
            // 结构类型由框架决定，必须与注册的定义一致
            if (definition.family() != frameFamily) {
                return null;
            }
            // 玻璃可以不存在；存在时必须与框架同等级
            if (glassLevel != null && glassLevel != frameLevel) {
                return null;
            }
            return new MatrixStructureMatch(definition, bounds, frameState, energyHatches, monitorPos);
        }
    }
}
