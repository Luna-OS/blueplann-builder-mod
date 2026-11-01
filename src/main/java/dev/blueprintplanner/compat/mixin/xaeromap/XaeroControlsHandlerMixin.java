package dev.blueprintplanner.compat.mixin.xaeromap;

import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xaero.map.controls.ControlsHandler", remap = false)
public abstract class XaeroControlsHandlerMixin {
	/**
	 * World map keys act on the real world's map (open it, switch its dimension, confirm its sub-world); inside the
	 * planner they would compare against the planner world. The press is already used up, so nothing piles up.
	 */
	@Inject(method = "keyDown(Lnet/minecraft/client/KeyMapping;ZZ)V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$notInPlanner(KeyMapping key, boolean tickEnd, boolean isRepeat, CallbackInfo ci) {
		if (PlannerIsolation.inPlannerClientContext()) {
			if (!isRepeat) {
				PlannerManager.message(Component.translatable("blueprintplanner.compat.xaero_map"), true);
			}
			ci.cancel();
		}
	}
}
