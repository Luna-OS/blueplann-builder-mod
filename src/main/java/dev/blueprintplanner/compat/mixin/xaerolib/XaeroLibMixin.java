package dev.blueprintplanner.compat.mixin.xaerolib;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.lib.XaeroLib", remap = false)
public abstract class XaeroLibMixin {
	/**
	 * The lib's server part (dimension and server handshakes, enforced config) works on server state the Mod shield
	 * never set up for the planner; on the planner server it stays off.
	 */
	@Inject(method = "isLoaded()Z", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$offOnPlannerServer(CallbackInfoReturnable<Boolean> cir) {
		if (PlannerIsolation.onPlannerServerThread()) {
			cir.setReturnValue(false);
		}
	}
}
