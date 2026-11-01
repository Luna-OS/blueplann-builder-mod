package dev.blueprintplanner.compat.mixin.axiom;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The real world's build symmetry survives the planner login (see AxiomDispatcherMixin#reset). */
@Mixin(targets = "com.moulberry.axiom.capabilities.BuildSymmetry", remap = false)
public abstract class AxiomBuildSymmetryMixin {
	@Inject(method = "clear()V", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$keepRealSymmetry(CallbackInfo ci) {
		if (PlannerIsolation.inPlannerClientContext()) {
			ci.cancel();
		}
	}
}
