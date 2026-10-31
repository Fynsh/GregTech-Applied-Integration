package com.Fynsh.gtai.block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import appeng.block.AEBaseBlockItem;

import com.Fynsh.gtai.GTAIMod;

/**
 * 本模组全部矩阵方块的注册表。
 *
 * <p>这张表是 {@code 方块注册表.txt} 的逐项落地：<b>199 条</b>，顺序与注册表一致，
 * 每条都带中文名，便于和原表逐行核对。类初始化时会校验条数与注册名唯一性，
 * 数量不符直接抛异常（要求 §6.9）。</p>
 *
 * <p>每个方块同时注册一个 AE2 的 {@link AEBaseBlockItem}，并统一收进创造模式标签页
 * {@code gtai:blocks}；本阶段不提供合成表。矩阵监控器核心带有
 * {@link com.Fynsh.gtai.blockentity.MatrixMonitorBlockEntity}，矩阵框架带有 AE 接入端口方块实体；
 * 其余方块保持轻量结构件。</p>
 *
 * <p>贴图与模型不在这里：层叠关系见 {@code assets/gtai/models/block/<id>.json}，
 * 渲染类型见 {@link MatrixRenderKind}。</p>
 */
public final class MatrixBlocks {

    /** 期望的方块数量，与 {@code 方块注册表.txt} 一致。 */
    public static final int EXPECTED_COUNT = 199;

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
            GTAIMod.MODID);

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            GTAIMod.MODID);

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, GTAIMod.MODID);

    /** 全部方块定义，顺序即 {@code 方块注册表.txt} 的顺序。 */
    private static final List<MatrixBlockSpec> SPECS = List.of(
            new MatrixBlockSpec("basic_assembly_matrix_frame", "基础组装应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("basic_circuit_assembly_matrix_frame", "基础电路组装应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("basic_material_matrix_frame", "基础材料应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("basic_fluid_material_matrix_frame", "基础流体材料应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("advanced_assembly_matrix_frame", "高级组装应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("advanced_circuit_assembly_matrix_frame", "高级电路组装应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("advanced_material_matrix_frame", "高级材料应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("advanced_fluid_material_matrix_frame", "高级流体材料应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("elite_assembly_matrix_frame", "精英组装应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("elite_circuit_assembly_matrix_frame", "精英电路组装应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("elite_material_matrix_frame", "精英材料应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("elite_fluid_material_matrix_frame", "精英流体材料应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ultimate_assembly_matrix_frame", "终极组装应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ultimate_circuit_assembly_matrix_frame", "终极电路组装应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ultimate_material_matrix_frame", "终极材料应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ultimate_fluid_material_matrix_frame", "终极流体材料应用集成矩阵框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("basic_assembly_matrix_cleanroom_frame", "基础组装应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("basic_circuit_assembly_matrix_cleanroom_frame", "基础电路组装应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("basic_material_matrix_cleanroom_frame", "基础材料应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("basic_fluid_material_matrix_cleanroom_frame", "基础流体材料应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("advanced_assembly_matrix_cleanroom_frame", "高级组装应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("advanced_circuit_assembly_matrix_cleanroom_frame", "高级电路组装应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("advanced_material_matrix_cleanroom_frame", "高级材料应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("advanced_fluid_material_matrix_cleanroom_frame", "高级流体材料应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("elite_assembly_matrix_cleanroom_frame", "精英组装应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("elite_circuit_assembly_matrix_cleanroom_frame", "精英电路组装应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("elite_material_matrix_cleanroom_frame", "精英材料应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("elite_fluid_material_matrix_cleanroom_frame", "精英流体材料应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ultimate_assembly_matrix_cleanroom_frame", "终极组装应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ultimate_circuit_assembly_matrix_cleanroom_frame", "终极电路组装应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ultimate_material_matrix_cleanroom_frame", "终极材料应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ultimate_fluid_material_matrix_cleanroom_frame", "终极流体材料应用集成矩阵超净框架", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("basic_matrix_glass", "基础应用集成矩阵玻璃", MatrixRenderKind.CUTOUT_WINDOW),
            new MatrixBlockSpec("advanced_matrix_glass", "高级应用集成矩阵玻璃", MatrixRenderKind.CUTOUT_WINDOW),
            new MatrixBlockSpec("elite_matrix_glass", "精英应用集成矩阵玻璃", MatrixRenderKind.CUTOUT_WINDOW),
            new MatrixBlockSpec("ultimate_matrix_glass", "终极应用集成矩阵玻璃", MatrixRenderKind.CUTOUT_WINDOW),
            new MatrixBlockSpec("basic_matrix_cleanroom_glass", "基础应用集成矩阵超净玻璃", MatrixRenderKind.CUTOUT_WINDOW),
            new MatrixBlockSpec("advanced_matrix_cleanroom_glass", "高级应用集成矩阵超净玻璃", MatrixRenderKind.CUTOUT_WINDOW),
            new MatrixBlockSpec("elite_matrix_cleanroom_glass", "精英应用集成矩阵超净玻璃", MatrixRenderKind.CUTOUT_WINDOW),
            new MatrixBlockSpec("ultimate_matrix_cleanroom_glass", "终极应用集成矩阵超净玻璃", MatrixRenderKind.CUTOUT_WINDOW),
            new MatrixBlockSpec("assembly_matrix_pattern_provider_core", "应用集成样板供应器组装矩阵核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("circuit_assembly_matrix_pattern_provider_core", "应用集成样板供应器电路组装矩阵核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("material_matrix_pattern_provider_core", "应用集成样板供应器材料矩阵核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("fluid_material_matrix_pattern_provider_core", "应用集成样板供应器流体材料矩阵核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("assembly_matrix_pattern_provider_core_extend", "应用集成扩展样板供应器组装矩阵核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("circuit_assembly_matrix_pattern_provider_core_extend", "应用集成扩展样板供应器电路组装矩阵核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("material_matrix_pattern_provider_core_extend", "应用集成扩展样板供应器材料矩阵核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("fluid_material_matrix_pattern_provider_core_extend", "应用集成扩展样板供应器流体材料矩阵核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_assembly_matrix_processing_core", "应用集成矩阵LV组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_circuit_assembly_matrix_processing_core", "应用集成矩阵LV电路组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_cutting_matrix_processing_core", "应用集成矩阵LV切削处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_pulverizing_matrix_processing_core", "应用集成矩阵LV粉碎处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_forming_matrix_processing_core", "应用集成矩阵LV成型处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_solidifying_matrix_processing_core", "应用集成矩阵LV固化处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_extracting_matrix_processing_core", "应用集成矩阵LV提取处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_assembly_matrix_processing_core", "应用集成矩阵MV组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_circuit_assembly_matrix_processing_core", "应用集成矩阵MV电路组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_cutting_matrix_processing_core", "应用集成矩阵MV切削处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_pulverizing_matrix_processing_core", "应用集成矩阵MV粉碎处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_forming_matrix_processing_core", "应用集成矩阵MV成型处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_solidifying_matrix_processing_core", "应用集成矩阵MV固化处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_extracting_matrix_processing_core", "应用集成矩阵MV提取处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_assembly_matrix_processing_core", "应用集成矩阵HV组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_circuit_assembly_matrix_processing_core", "应用集成矩阵HV电路组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_cutting_matrix_processing_core", "应用集成矩阵HV切削处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_pulverizing_matrix_processing_core", "应用集成矩阵HV粉碎处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_forming_matrix_processing_core", "应用集成矩阵HV成型处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_solidifying_matrix_processing_core", "应用集成矩阵HV固化处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_extracting_matrix_processing_core", "应用集成矩阵HV提取处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_assembly_matrix_processing_core", "应用集成矩阵EV组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_circuit_assembly_matrix_processing_core", "应用集成矩阵EV电路组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_cutting_matrix_processing_core", "应用集成矩阵EV切削处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_pulverizing_matrix_processing_core", "应用集成矩阵EV粉碎处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_forming_matrix_processing_core", "应用集成矩阵EV成型处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_solidifying_matrix_processing_core", "应用集成矩阵EV固化处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_extracting_matrix_processing_core", "应用集成矩阵EV提取处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_assembly_matrix_processing_core", "应用集成矩阵IV组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_circuit_assembly_matrix_processing_core", "应用集成矩阵IV电路组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_cutting_matrix_processing_core", "应用集成矩阵IV切削处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_pulverizing_matrix_processing_core", "应用集成矩阵IV粉碎处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_forming_matrix_processing_core", "应用集成矩阵IV成型处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_solidifying_matrix_processing_core", "应用集成矩阵IV固化处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_extracting_matrix_processing_core", "应用集成矩阵IV提取处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_assembly_matrix_processing_core", "应用集成矩阵LuV组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_circuit_assembly_matrix_processing_core", "应用集成矩阵LuV电路组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_cutting_matrix_processing_core", "应用集成矩阵LuV切削处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_pulverizing_matrix_processing_core", "应用集成矩阵LuV粉碎处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_forming_matrix_processing_core", "应用集成矩阵LuV成型处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_solidifying_matrix_processing_core", "应用集成矩阵LuV固化处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_extracting_matrix_processing_core", "应用集成矩阵LuV提取处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_assembly_matrix_processing_core_extend", "应用集成矩阵扩展LV组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_circuit_assembly_matrix_processing_core_extend", "应用集成矩阵扩展LV电路组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_cutting_matrix_processing_core_extend", "应用集成矩阵扩展LV切削处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_pulverizing_matrix_processing_core_extend", "应用集成矩阵扩展LV粉碎处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_forming_matrix_processing_core_extend", "应用集成矩阵扩展LV成型处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_solidifying_matrix_processing_core_extend", "应用集成矩阵扩展LV固化处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_extracting_matrix_processing_core_extend", "应用集成矩阵扩展LV提取处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_assembly_matrix_processing_core_extend", "应用集成矩阵扩展MV组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_circuit_assembly_matrix_processing_core_extend", "应用集成矩阵扩展MV电路组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_cutting_matrix_processing_core_extend", "应用集成矩阵扩展MV切削处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_pulverizing_matrix_processing_core_extend", "应用集成矩阵扩展MV粉碎处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_forming_matrix_processing_core_extend", "应用集成矩阵扩展MV成型处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_solidifying_matrix_processing_core_extend", "应用集成矩阵扩展MV固化处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_extracting_matrix_processing_core_extend", "应用集成矩阵扩展MV提取处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_assembly_matrix_processing_core_extend", "应用集成矩阵扩展HV组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_circuit_assembly_matrix_processing_core_extend", "应用集成矩阵扩展HV电路组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_cutting_matrix_processing_core_extend", "应用集成矩阵扩展HV切削处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_pulverizing_matrix_processing_core_extend", "应用集成矩阵扩展HV粉碎处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_forming_matrix_processing_core_extend", "应用集成矩阵扩展HV成型处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_solidifying_matrix_processing_core_extend", "应用集成矩阵扩展HV固化处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_extracting_matrix_processing_core_extend", "应用集成矩阵扩展HV提取处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_assembly_matrix_processing_core_extend", "应用集成矩阵扩展EV组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_circuit_assembly_matrix_processing_core_extend", "应用集成矩阵扩展EV电路组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_cutting_matrix_processing_core_extend", "应用集成矩阵扩展EV切削处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_pulverizing_matrix_processing_core_extend", "应用集成矩阵扩展EV粉碎处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_forming_matrix_processing_core_extend", "应用集成矩阵扩展EV成型处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_solidifying_matrix_processing_core_extend", "应用集成矩阵扩展EV固化处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_extracting_matrix_processing_core_extend", "应用集成矩阵扩展EV提取处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_assembly_matrix_processing_core_extend", "应用集成矩阵扩展IV组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_circuit_assembly_matrix_processing_core_extend", "应用集成矩阵扩展IV电路组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_cutting_matrix_processing_core_extend", "应用集成矩阵扩展IV切削处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_pulverizing_matrix_processing_core_extend", "应用集成矩阵扩展IV粉碎处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_forming_matrix_processing_core_extend", "应用集成矩阵扩展IV成型处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_solidifying_matrix_processing_core_extend", "应用集成矩阵扩展IV固化处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_extracting_matrix_processing_core_extend", "应用集成矩阵扩展IV提取处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_assembly_matrix_processing_core_extend", "应用集成矩阵扩展LuV组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_circuit_assembly_matrix_processing_core_extend", "应用集成矩阵扩展LuV电路组装处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_cutting_matrix_processing_core_extend", "应用集成矩阵扩展LuV切削处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_pulverizing_matrix_processing_core_extend", "应用集成矩阵扩展LuV粉碎处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_forming_matrix_processing_core_extend", "应用集成矩阵扩展LuV成型处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_solidifying_matrix_processing_core_extend", "应用集成矩阵扩展LuV固化处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_extracting_matrix_processing_core_extend", "应用集成矩阵扩展LuV提取处理核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_assembly_matrix_speed_core", "应用集成矩阵LV组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_circuit_assembly_matrix_speed_core", "应用集成矩阵LV电路组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_material_matrix_speed_core", "应用集成矩阵LV材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_fluid_material_matrix_speed_core", "应用集成矩阵LV流体材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_assembly_matrix_speed_core", "应用集成矩阵MV组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_circuit_assembly_matrix_speed_core", "应用集成矩阵MV电路组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_material_matrix_speed_core", "应用集成矩阵MV材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_fluid_material_matrix_speed_core", "应用集成矩阵MV流体材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_assembly_matrix_speed_core", "应用集成矩阵HV组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_circuit_assembly_matrix_speed_core", "应用集成矩阵HV电路组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_material_matrix_speed_core", "应用集成矩阵HV材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_fluid_material_matrix_speed_core", "应用集成矩阵HV流体材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_assembly_matrix_speed_core", "应用集成矩阵EV组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_circuit_assembly_matrix_speed_core", "应用集成矩阵EV电路组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_material_matrix_speed_core", "应用集成矩阵EV材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_fluid_material_matrix_speed_core", "应用集成矩阵EV流体材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_assembly_matrix_speed_core", "应用集成矩阵IV组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_circuit_assembly_matrix_speed_core", "应用集成矩阵IV电路组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_material_matrix_speed_core", "应用集成矩阵IV材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_fluid_material_matrix_speed_core", "应用集成矩阵IV流体材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_assembly_matrix_speed_core", "应用集成矩阵LuV组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_circuit_assembly_matrix_speed_core", "应用集成矩阵LuV电路组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_material_matrix_speed_core", "应用集成矩阵LuV材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_fluid_material_matrix_speed_core", "应用集成矩阵LuV流体材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_assembly_matrix_speed_core_extend", "应用集成矩阵扩展LV组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_circuit_assembly_matrix_speed_core_extend", "应用集成矩阵扩展LV电路组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_material_matrix_speed_core_extend", "应用集成矩阵扩展LV材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_fluid_material_matrix_speed_core_extend", "应用集成矩阵扩展LV流体材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_assembly_matrix_speed_core_extend", "应用集成矩阵扩展MV组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_circuit_assembly_matrix_speed_core_extend", "应用集成矩阵扩展MV电路组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_material_matrix_speed_core_extend", "应用集成矩阵扩展MV材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_fluid_material_matrix_speed_core_extend", "应用集成矩阵扩展MV流体材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_assembly_matrix_speed_core_extend", "应用集成矩阵扩展HV组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_circuit_assembly_matrix_speed_core_extend", "应用集成矩阵扩展HV电路组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_material_matrix_speed_core_extend", "应用集成矩阵扩展HV材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_fluid_material_matrix_speed_core_extend", "应用集成矩阵扩展HV流体材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_assembly_matrix_speed_core_extend", "应用集成矩阵扩展EV组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_circuit_assembly_matrix_speed_core_extend", "应用集成矩阵扩展EV电路组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_material_matrix_speed_core_extend", "应用集成矩阵扩展EV材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_fluid_material_matrix_speed_core_extend", "应用集成矩阵扩展EV流体材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_assembly_matrix_speed_core_extend", "应用集成矩阵扩展IV组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_circuit_assembly_matrix_speed_core_extend", "应用集成矩阵扩展IV电路组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_material_matrix_speed_core_extend", "应用集成矩阵扩展IV材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_fluid_material_matrix_speed_core_extend", "应用集成矩阵扩展IV流体材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_assembly_matrix_speed_core_extend", "应用集成矩阵扩展LuV组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_circuit_assembly_matrix_speed_core_extend", "应用集成矩阵扩展LuV电路组装速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_material_matrix_speed_core_extend", "应用集成矩阵扩展LuV材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_fluid_material_matrix_speed_core_extend", "应用集成矩阵扩展LuV流体材料速度核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("assembly_matrix_monitor_core", "应用集成矩阵组装监控器核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("circuit_assembly_matrix_monitor_core", "应用集成矩阵电路组装监控器核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("material_matrix_monitor_core", "应用集成矩阵材料监控器核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("fluid_material_matrix_monitor_core", "应用集成矩阵流体材料监控器核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("assembly_matrix_monitor_core_extend", "应用集成矩阵扩展组装监控器核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("circuit_assembly_matrix_monitor_core_extend", "应用集成矩阵扩展电路组装监控器核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("material_matrix_monitor_core_extend", "应用集成矩阵扩展材料监控器核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("fluid_material_matrix_monitor_core_extend", "应用集成矩阵扩展流体材料监控器核心", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("lv_matrix_transformer", "应用集成矩阵LV变压器", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("mv_matrix_transformer", "应用集成矩阵MV变压器", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("hv_matrix_transformer", "应用集成矩阵HV变压器", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("ev_matrix_transformer", "应用集成矩阵EV变压器", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("iv_matrix_transformer", "应用集成矩阵IV变压器", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("luv_matrix_transformer", "应用集成矩阵LuV变压器", MatrixRenderKind.CUTOUT),
            new MatrixBlockSpec("matrix_cleanroom", "应用集成矩阵超净间", MatrixRenderKind.OPAQUE),
            new MatrixBlockSpec("matrix_filter_casing", "应用集成矩阵过滤器机械方块", MatrixRenderKind.OPAQUE),
            new MatrixBlockSpec("matrix_sterile_filter_casing", "应用集成矩阵无菌过滤器机械方块", MatrixRenderKind.OPAQUE),
            new MatrixBlockSpec("matrix_active_transformer", "应用集成矩阵有源变压器", MatrixRenderKind.OPAQUE),
            new MatrixBlockSpec("empty_matrix_core", "空应用集成矩阵核心", MatrixRenderKind.OPAQUE));

    private static final Map<String, RegistryObject<MatrixBlock>> BLOCKS_BY_ID = new LinkedHashMap<>();

    private static final Map<String, RegistryObject<Item>> ITEMS_BY_ID = new LinkedHashMap<>();

    static {
        validate();

        for (MatrixBlockSpec spec : SPECS) {
            RegistryObject<MatrixBlock> block = BLOCKS.register(spec.id(), () -> switch (spec.kind().role()) {
                case MONITOR -> new MatrixMonitorBlock(spec, properties(spec));
                case FRAME -> new MatrixFrameBlock(spec, properties(spec));
                default -> new MatrixBlock(spec, properties(spec));
            });
            RegistryObject<Item> item = ITEMS.register(spec.id(),
                    () -> new AEBaseBlockItem(block.get(), new Item.Properties()));
            BLOCKS_BY_ID.put(spec.id(), block);
            ITEMS_BY_ID.put(spec.id(), item);
        }
    }

    /** 创造模式标签页，收纳全部矩阵方块。 */
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("blocks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.gtai.blocks"))
                    .icon(() -> new ItemStack(BLOCKS_BY_ID.get("basic_assembly_matrix_frame").get()))
                    .displayItems((parameters, output) -> BLOCKS_BY_ID.values()
                            .forEach(block -> output.accept(block.get())))
                    .build());

    private MatrixBlocks() {
    }

    /** 注册到模组事件总线。 */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
    }

    /** 全部方块定义（只读，顺序与注册表一致）。 */
    public static List<MatrixBlockSpec> specs() {
        return SPECS;
    }

    /** 注册名 → 方块。 */
    public static Map<String, RegistryObject<MatrixBlock>> blocks() {
        return Collections.unmodifiableMap(BLOCKS_BY_ID);
    }

    /** 注册名 → 方块物品。 */
    public static Map<String, RegistryObject<Item>> items() {
        return Collections.unmodifiableMap(ITEMS_BY_ID);
    }

    /**
     * 全部矩阵监控器方块。
     *
     * <p>监控器核心保存结构状态与详情；该方法在方块实体类型注册阶段使用。</p>
     */
    public static Block[] monitorBlocks() {
        return BLOCKS_BY_ID.values().stream()
                .map(RegistryObject::get)
                .filter(block -> block.spec().kind().role() == MatrixBlockRole.MONITOR)
                .toArray(Block[]::new);
    }

    /**
     * 全部矩阵框架方块。
     *
     * <p>只有框架需要 AE 接入端口方块实体；玻璃、过滤器、核心保持轻量结构件，
     * 因此 AE 线缆/总线无法连接它们。</p>
     */
    public static Block[] frameBlocks() {
        return BLOCKS_BY_ID.values().stream()
                .map(RegistryObject::get)
                .filter(block -> block.spec().kind().role() == MatrixBlockRole.FRAME)
                .toArray(Block[]::new);
    }

    /** 按注册名取方块；不存在时返回 {@code null}。 */
    public static MatrixBlock get(String id) {
        RegistryObject<MatrixBlock> holder = BLOCKS_BY_ID.get(id);
        return holder != null && holder.isPresent() ? holder.get() : null;
    }

    /**
     * 方块属性：参考格雷机械外壳的手感，除玻璃族外完全一致。
     *
     * <p>玻璃族的底图是"带透明孔洞的窗格"，必须 {@code noOcclusion()}，否则相邻方块的
     * 贴合面会被剔除，透过孔洞会看到空洞。</p>
     */
    private static BlockBehaviour.Properties properties(MatrixBlockSpec spec) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
        if (spec.renderKind() == MatrixRenderKind.CUTOUT_WINDOW) {
            properties.noOcclusion();
        }
        return properties;
    }

    /** 数量与唯一性校验，失败即中止加载，避免"悄悄少注册几个方块"。 */
    private static void validate() {
        if (SPECS.size() != EXPECTED_COUNT) {
            throw new IllegalStateException("Matrix block table must contain " + EXPECTED_COUNT
                    + " entries but has " + SPECS.size() + "; take 方块注册表.txt as the source of truth");
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (MatrixBlockSpec spec : SPECS) {
            if (!seen.add(spec.id())) {
                throw new IllegalStateException("Duplicate matrix block id: " + spec.id());
            }
        }
    }
}
