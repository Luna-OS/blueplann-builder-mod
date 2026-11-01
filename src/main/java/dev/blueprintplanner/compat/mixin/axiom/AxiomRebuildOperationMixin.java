package dev.blueprintplanner.compat.mixin.axiom;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An animated rebuild started in the real world pauses while the planner is shown. */
@Mixin(targets = "com.moulberry.axiom.operations.RebuildOperation", remap = false)
public abstract class AxiomRebuildOperationMixin {
	@Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$pauseInPlanner(CallbackInfo ci) {
		if (PlannerIsolation.inPlannerClientContext()) {
			ci.cancel();
		}
	}
}
