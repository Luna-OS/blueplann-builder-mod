package dev.blueprintplanner.compat.mixin.xaeromap;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world map keeps working on the real world while the planner is shown: it must not adopt the planner level and
 * player (planner terrain would be written into the real world's map) or pick its cave layer from planner blocks.
 */
@Mixin(targets = "xaero.map.MapProcessor", remap = false)
public abstract class XaeroMapProcessorMixin {
	@Inject(method = "setMainValues()V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$keepRealWorld(CallbackInfo ci) {
		if (PlannerIsolation.inPlannerClientContext()) {
			ci.cancel();
		}
	}

	/** World map actions in the planner (teleport, dimension switch) would pick the real map's sub-world from planner data. */
	@Inject(method = "checkForWorldUpdate()V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$keepRealSubWorld(CallbackInfo ci) {
		if (PlannerIsolation.inPlannerClientContext()) {
			ci.cancel();
		}
	}

	@Inject(method = "updateCaveStart()V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$keepRealCaveLayer(CallbackInfo ci) {
		if (PlannerIsolation.inPlannerClientContext()) {
			ci.cancel();
		}
	}
}
