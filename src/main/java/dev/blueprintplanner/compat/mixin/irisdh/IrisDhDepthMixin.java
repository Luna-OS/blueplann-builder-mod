package dev.blueprintplanner.compat.mixin.irisdh;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distant Horizons does not draw in the planner, so its depth textures still hold the last LODs of the real world.
 * DH-aware shader packs would show them as a frozen ghost of real terrain in the planner sky. Report "no LOD depth"
 * instead, which is what Iris itself does when DH has nothing to offer.
 */
@Mixin(targets = "net.irisshaders.iris.compat.dh.DHCompat", remap = false)
public abstract class IrisDhDepthMixin {
	@Inject(method = {"getDepthTex()I", "getDepthTexNoTranslucent()I"}, at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$noLodDepthInPlanner(CallbackInfoReturnable<Integer> cir) {
		if (PlannerIsolation.inPlannerClientContext()) {
			cir.setReturnValue(0);
		}
	}
}
