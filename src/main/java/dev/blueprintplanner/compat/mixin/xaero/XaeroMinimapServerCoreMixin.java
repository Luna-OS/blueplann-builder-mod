package dev.blueprintplanner.compat.mixin.xaero;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.common.server.core.XaeroMinimapServerCore", remap = false)
public abstract class XaeroMinimapServerCoreMixin {
	/**
	 * Every server part of the minimap (player joins, logs in, ticks) asks this first. Its server data comes from a
	 * server start listener that the Mod shield keeps away from the planner, so the minimap would kick the planner
	 * player ("Error loading server minimap properties") and then crash on its first tick.
	 */
	@Inject(method = "isModLoaded()Z", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$offOnPlannerServer(CallbackInfoReturnable<Boolean> cir) {
		if (PlannerIsolation.onPlannerServerThread()) {
			cir.setReturnValue(false);
		}
	}
}
