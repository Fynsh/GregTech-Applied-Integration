package com.Fynsh.gtai.multiblock;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import com.Fynsh.gtai.GTAIMod;

/**
 * 外部能源仓识别。
 *
 * <p>矩阵结构本身不注册能源仓，外壳允许放置格雷科技的能源输入仓。成型判定只关心
 * “这一格是不是能源仓”，不关心它的电压等级；等级与结构是否成型无关。</p>
 *
 * <p>这里通过注册名识别，避免在成型扫描时触碰 GT 的机器方块实体/配方 API。GT 7.5.3
 * 的能源输入仓注册名为 {@code gtceu:<tier>_energy_input_hatch}、{@code _4a}、
 * {@code _16a}。</p>
 */
public final class MatrixEnergyHatch {

    private MatrixEnergyHatch() {
    }

    public static boolean isEnergyHatch(BlockState state) {
        return isEnergyHatch(state.getBlock());
    }

    public static boolean isEnergyHatch(Block block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null || !GTAIMod.GTCEU_MODID.equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return path.endsWith("_energy_input_hatch")
                || path.endsWith("_energy_input_hatch_4a")
                || path.endsWith("_energy_input_hatch_16a");
    }

    /**
     * 读取能源仓的电压等级片段，例如 {@code lv}、{@code mv}。
     *
     * <p>GT 的高等级能源仓（UHV、MAX 等）同样会被识别，因此这里返回原始注册名片段
     * 而不是 {@code MatrixVoltage} 枚举；本模组方块表只覆盖 LV..LuV，但成型判定并不限制
     * 能源仓等级。</p>
     *
     * @return 小写电压等级片段；不是能源仓或无法解析时返回 {@code null}
     */
    @Nullable
    public static String voltageId(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null || !GTAIMod.GTCEU_MODID.equals(id.getNamespace())) {
            return null;
        }
        String path = id.getPath();
        int suffix = path.indexOf("_energy_input_hatch");
        if (suffix <= 0) {
            return null;
        }
        return path.substring(0, suffix);
    }
}
