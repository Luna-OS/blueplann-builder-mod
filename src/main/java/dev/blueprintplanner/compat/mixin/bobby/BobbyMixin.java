package dev.blueprintplanner.compat.mixin.bobby;

import de.johni0702.minecraft.bobby.Bobby;
import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No Bobby chunk cache for the planner level: the planner server runs locally at full render distance anyway, and
 * Bobby would store every planner world (of every server) in one shared "unknown" cache folder and show ghost
 * terrain from other planner worlds.
 */
@Mixin(value = Bobby.class, remap = false)
public abstract class BobbyMixin {
	@Inject(method = "isEnabled()Z", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$noBobbyInPlanner(CallbackInfoReturnable<Boolean> cir) {
		if (PlannerIsolation.inPlannerClientContext()) {
			cir.setReturnValue(false);
		}
	}
}
