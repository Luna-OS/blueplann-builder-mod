package dev.blueprintplanner.compat.mixin.xaero;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.hud.HudSession", remap = false)
public abstract class XaeroHudSessionMixin {
	/**
	 * Without a planner session Xaero falls back to the real world's session and would update it with the planner
	 * world (dimension, waypoints, deathpoints at planner positions). Inside the planner the minimap is simply off.
	 */
	@Inject(method = "getCurrentSession()Lxaero/hud/HudSession;", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$noneInPlanner(CallbackInfoReturnable<Object> cir) {
		if (PlannerIsolation.inPlannerClientContext()) {
			cir.setReturnValue(null);
		}
	}
}
