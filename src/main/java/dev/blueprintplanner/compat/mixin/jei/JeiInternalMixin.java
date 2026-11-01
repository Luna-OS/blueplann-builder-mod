package dev.blueprintplanner.compat.mixin.jei;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blueprintplanner.planner.ContextSwitcher;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "mezz.jei.common.Internal", remap = false)
public abstract class JeiInternalMixin {
	/**
	 * JEI closes its recipe screen when it stops. A stop caused by the real world while the planner is shown (for
	 * example /reload on the real server) would leave the screen open; it closes back into the planner instead.
	 */
	@WrapOperation(method = "onRuntimeStopped()V", at = @At(value = "INVOKE", target = "Lmezz/jei/common/Internal;closeRecipeGuiIfOpen()V"))
	private static void blueprintplanner$closeInPlanner(Operation<Void> original) {
		if (Minecraft.getInstance().isSameThread() && ContextSwitcher.inTemporarySwitch()
			&& ContextSwitcher.active() == ContextSwitcher.Side.PLANNER && ContextSwitcher.installed() == ContextSwitcher.Side.REAL) {
			ContextSwitcher.runIn(ContextSwitcher.Side.PLANNER, () -> original.call());
		} else {
			original.call();
		}
	}
}
