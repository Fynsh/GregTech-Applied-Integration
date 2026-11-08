package com.Fynsh.gtai.multiblock;

import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.Fynsh.gtai.block.MatrixBlockRole;

/**
 * 一次成功成型判定的完整结果。
 *
 * <p>除了结构定义，还携带结构边界、监控器位置与渲染需要的信息：</p>
 * <ul>
 *   <li>{@link #bounds()}：包含外壳的完整包围盒；</li>
 *   <li>{@link #monitorPos()}：结构中唯一的矩阵监控器位置，成型信息写入该方块实体；</li>
 *   <li>{@link #frameState()}：本次结构实际使用的矩阵框架方块状态（含普通/超净变体）；</li>
 *   <li>{@link #energyHatches()}：能源仓位置，供客户端 BER 在对应位置绘制覆盖层；</li>
 *   <li>{@link #layout()}：验证时顺手记录的角色坐标快照，后续逻辑不再重复扫描整结构。</li>
 * </ul>
 */
public record MatrixStructureMatch(MatrixMultiblockDefinition definition, MatrixStructureBounds bounds,
        BlockState frameState, List<BlockPos> energyHatches, BlockPos monitorPos, MatrixStructureLayout layout) {

    public MatrixStructureMatch {
        definition = Objects.requireNonNull(definition, "definition");
        bounds = Objects.requireNonNull(bounds, "bounds");
        frameState = Objects.requireNonNull(frameState, "frameState");
        energyHatches = List.copyOf(energyHatches);
        if (energyHatches.isEmpty()) {
            throw new IllegalArgumentException("A formed matrix must contain exactly one energy hatch");
        }
        monitorPos = Objects.requireNonNull(monitorPos, "monitorPos").immutable();
        layout = Objects.requireNonNull(layout, "layout");
    }

    /** 按角色取结构内坐标；不存在该角色时返回空列表。 */
    public List<BlockPos> positions(MatrixBlockRole role) {
        return layout.positions(role);
    }
}
