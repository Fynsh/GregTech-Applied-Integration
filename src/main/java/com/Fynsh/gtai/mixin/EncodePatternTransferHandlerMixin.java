package com.Fynsh.gtai.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.player.Player;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.menu.me.items.PatternEncodingTermMenu;

import com.Fynsh.gtai.integration.jei.JeiEncodeSupport;
import com.Fynsh.gtai.network.EncodePatternPacket;
import com.Fynsh.gtai.network.GtaiNetwork;
import com.Fynsh.gtai.pattern.GtaiPatternKind;
import com.Fynsh.gtai.recipe.PatternTypeMapping;

/**
 * 唯一的一处注入点：AE2 现有的 JEI “编写样板”联动入口。
 *
 * <p>AE2 的 {@code EncodePatternTransferHandler} 是 JEI 的通用配方转移处理器，
 * 负责样板编码终端里的“编写样板”点击。这里只在 <b>真正执行转移</b>（{@code doTransfer}）、
 * 配方确实是格雷配方且命中映射时介入：把“立即编码并得到指定样板”的请求发给服务端，
 * 然后返回 {@code null} 表示成功（关闭配方页由 JEI／AE2 原逻辑负责）。</p>
 *
 * <p>其余所有情况（未命中映射、输出槽被占、没有空白样板、超出样板容量等）都不取消原方法，
 * AE2 原有行为保持不变。</p>
 */
@Mixin(targets = "appeng.integration.modules.jei.transfer.EncodePatternTransferHandler", remap = false)
public abstract class EncodePatternTransferHandlerMixin {

    @Inject(method = "transferRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtai$encodeGtPattern(PatternEncodingTermMenu menu, Object recipeBase, IRecipeSlotsView slotsView,
            Player player, boolean maxTransfer, boolean doTransfer,
            CallbackInfoReturnable<IRecipeTransferError> cir) {
        // 只有真正点击“编写样板”时才介入；doTransfer == false 是 JEI 的可行性检查，交给 AE2 处理
        if (!doTransfer) {
            return;
        }

        // 配方类型必须来自格雷科技的真实配方对象，未命中映射则完全走 AE2 原逻辑
        if (!(recipeBase instanceof GTRecipe recipe)) {
            return;
        }
        GtaiPatternKind kind = PatternTypeMapping.kindFor(recipe.getType());
        if (kind == null) {
            return;
        }
        // 没有配方 id 就无法做后续失效检测，交回 AE2 原逻辑
        if (recipe.getId() == null) {
            return;
        }

        // 终端条件不足时不做任何事，交由 AE2 原逻辑处理（不生成样板、不损失物品）
        if (!JeiEncodeSupport.canEncode(menu)) {
            return;
        }

        List<GenericStack> inputs = JeiEncodeSupport.collectInputs(slotsView);
        List<GenericStack> outputs = JeiEncodeSupport.collectOutputs(slotsView);
        if (inputs.isEmpty() || inputs.size() > AEProcessingPattern.MAX_INPUT_SLOTS) {
            return;
        }
        if (outputs.isEmpty() || outputs.size() > AEProcessingPattern.MAX_OUTPUT_SLOTS) {
            return;
        }

        GtaiNetwork.sendToServer(new EncodePatternPacket(kind, recipe.getType().registryName, recipe.getId(),
                inputs, outputs));

        // 视为编码成功：JEI 会关闭配方页，返回终端界面由原逻辑完成
        cir.setReturnValue(null);
    }
}
