package com.Fynsh.gtai.multiblock;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;

import com.Fynsh.gtai.block.MatrixBlockRole;

/**
 * 一次成型判定得到的结构布局快照。
 *
 * <p>成型判定本来就要逐格扫描整个长方体，因此在这里顺手把每个方块按角色分类保存下来，
 * 后续的信息收集、爆炸检查、框架绑定和外壳方块更新都只遍历这些小列表，不再重复做
 * O(n^3) 的整结构扫描。</p>
 *
 * <p>快照的生命周期与 {@link MatrixStructureMatch} 一致：结构内任何方块变化都会触发
 * 监控器重新判定并生成新的快照，因此快照不会在结构变化后继续被使用。坐标为不可变
 * {@link BlockPos}，列表与映射在构造时复制并只读。</p>
 *
 * @param bounds        结构边界（含外壳）
 * @param positions     角色 -> 位置；没有该角色的方块时映射中不出现该键
 * @param shell         所有外壳位置（框架/玻璃/过滤器/能源仓）
 * @param energyHatches 能源仓位置；成型结构恰好一个
 */
public record MatrixStructureLayout(MatrixStructureBounds bounds,
        Map<MatrixBlockRole, List<BlockPos>> positions,
        List<BlockPos> shell,
        List<BlockPos> energyHatches) {

    public MatrixStructureLayout {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(shell, "shell");
        Objects.requireNonNull(energyHatches, "energyHatches");

        EnumMap<MatrixBlockRole, List<BlockPos>> copy = new EnumMap<>(MatrixBlockRole.class);
        positions.forEach((role, list) -> {
            if (role != null && list != null && !list.isEmpty()) {
                copy.put(role, List.copyOf(list));
            }
        });
        positions = Collections.unmodifiableMap(copy);
        shell = List.copyOf(shell);
        energyHatches = List.copyOf(energyHatches);
    }

    /** 某个角色的全部位置；没有该角色时返回空列表。 */
    public List<BlockPos> positions(MatrixBlockRole role) {
        return positions.getOrDefault(role, List.of());
    }

    /** 该角色是否至少出现一次。 */
    public boolean has(MatrixBlockRole role) {
        return positions.containsKey(role);
    }
}
