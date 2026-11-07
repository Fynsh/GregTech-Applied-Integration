package com.Fynsh.gtai.multiblock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;

import com.Fynsh.gtai.block.MatrixBlock;
import com.Fynsh.gtai.block.MatrixBlockKind;
import com.Fynsh.gtai.block.MatrixBlockRole;

/**
 * 矩阵成型时的能源仓/核心爆炸判定与表现。
 *
 * <p>只在结构从未成型变为成型时调用一次。爆炸不创建原版 Explosion 实体：只播放音效、
 * 发送粒子，并移除目标方块；不会伤害实体或破坏其它方块。</p>
 */
public final class MatrixFormationExplosion {

    private MatrixFormationExplosion() {
    }

    /**
     * 计算本次成型需要摧毁的方块位置；返回空列表表示不需要爆炸。
     *
     * <p>能源仓电量为 0 时直接返回空列表，规则 4/5 都不执行。</p>
     */
    public static List<BlockPos> findTargets(ServerLevel level, MatrixStructureMatch match,
            MatrixStructureInfo info) {
        if (match.energyHatches().isEmpty() || info == null || info.energyVoltage() == null) {
            return List.of();
        }

        BlockPos hatchPos = match.energyHatches().get(0);
        if (storedEnergy(level, hatchPos) <= 0L) {
            return List.of();
        }

        int matrixTier = MatrixVoltageTier.of(info.matrixLevel());
        List<BlockPos> targets = new ArrayList<>();

        // 规则 4：能源仓电压高于矩阵最高可用电压
        if (isOvervoltage(match, info)) {
            targets.add(hatchPos);
        }

        boolean activeTransformer = false;
        Set<Integer> transformerTiers = new HashSet<>();
        for (MatrixStructureInfo.TransformerEntry transformer : info.transformers()) {
            if (transformer.active()) {
                activeTransformer = true;
            } else if (transformer.voltage() != null) {
                transformerTiers.add(MatrixVoltageTier.of(transformer.voltage()));
            }
        }

        // 规则 5：低电压核心缺少有源变压器或连续升压链
        if (!activeTransformer) {
            MatrixStructureBounds bounds = match.bounds();
            for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
                for (int y = bounds.min().getY(); y <= bounds.max().getY(); y++) {
                    for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (bounds.boundaryCount(pos) != 0) {
                            continue;
                        }
                        BlockState state = level.getBlockState(pos);
                        if (!(state.getBlock() instanceof MatrixBlock block)) {
                            continue;
                        }
                        MatrixBlockKind kind = block.kind();
                        if (kind.role() != MatrixBlockRole.SPEED_CORE
                                && kind.role() != MatrixBlockRole.PROCESSING_CORE) {
                            continue;
                        }
                        if (kind.voltage() == null) {
                            continue;
                        }
                        int coreTier = MatrixVoltageTier.of(kind.voltage());
                        // 等于矩阵等级不检查；高于矩阵等级只从监控器数据中忽略
                        if (coreTier >= matrixTier) {
                            continue;
                        }
                        boolean completeChain = true;
                        for (int tier = coreTier; tier < matrixTier; tier++) {
                            if (!transformerTiers.contains(tier)) {
                                completeChain = false;
                                break;
                            }
                        }
                        if (!completeChain) {
                            targets.add(pos.immutable());
                        }
                    }
                }
            }
        }

        return targets;
    }

    /** 能源仓电压是否高于矩阵最高可用电压。 */
    public static boolean isOvervoltage(MatrixStructureMatch match, MatrixStructureInfo info) {
        if (match.energyHatches().isEmpty() || info == null || info.energyVoltage() == null) {
            return false;
        }
        return MatrixVoltageTier.byId(info.energyVoltage()) > MatrixVoltageTier.of(info.matrixLevel());
    }

    /**
     * 播放爆炸表现、按原方块掉落目标方块（清空 NBT），然后移除方块。
     *
     * <p>使用 {@link Block#UPDATE_CLIENTS}，避免移除方块时在成型扫描过程中触发递归
     * 方块更新；调用方随后应主动重新执行一次成型判定。</p>
     */
    public static void destroy(ServerLevel level, List<BlockPos> targets) {
        for (BlockPos pos : targets) {
            BlockState state = level.getBlockState(pos);
            if (!isDestroyable(state)) {
                continue;
            }

            ItemStack drop = state.getBlock().getCloneItemStack(level, pos, state);
            if (drop.isEmpty()) {
                drop = new ItemStack(state.getBlock().asItem());
            }
            if (drop.isEmpty()) {
                // 部分机器方块使用自定义 BlockItem，Block.asItem() 可能为空；按方块反查物品
                for (Item item : ForgeRegistries.ITEMS.getValues()) {
                    if (item instanceof BlockItem blockItem && blockItem.getBlock() == state.getBlock()) {
                        drop = new ItemStack(item);
                        break;
                    }
                }
            }
            if (!drop.isEmpty()) {
                drop.setTag(null);
                Block.popResource(level, pos, drop);
            }

            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                    4.0F, 0.8F + level.random.nextFloat() * 0.4F);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.EXPLOSION,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 6, 0.35D, 0.35D, 0.35D, 0.0D);
        }
    }

    private static boolean isDestroyable(BlockState state) {
        if (MatrixEnergyHatch.isEnergyHatch(state)) {
            return true;
        }
        if (state.getBlock() instanceof MatrixBlock block) {
            MatrixBlockKind kind = block.kind();
            return kind.role() == MatrixBlockRole.SPEED_CORE || kind.role() == MatrixBlockRole.PROCESSING_CORE;
        }
        return false;
    }

    /** 读取 GT 能源仓当前存储电量；非 GT 能源仓或方块实体未加载时按 0 处理。 */
    public static long storedEnergy(ServerLevel level, BlockPos pos) {
        IEnergyContainer container = GTCapabilityHelper.getEnergyContainer(level, pos, null);
        return container == null ? 0L : container.getEnergyStored();
    }
}
