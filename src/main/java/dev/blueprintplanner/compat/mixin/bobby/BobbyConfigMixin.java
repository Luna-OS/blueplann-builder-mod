package dev.blueprintplanner.compat.mixin.bobby;

import de.johni0702.minecraft.bobby.BobbyConfig;
import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bobby's "view distance overwrite" is meant for the real singleplayer server, not for the planner server. */
@Mixin(value = BobbyConfig.class, remap = false)
public abstract class BobbyConfigMixin {
	@Inject(method = "getViewDistanceOverwrite()I", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$noOverwriteForPlanner(CallbackInfoReturnable<Integer> cir) {
		if (PlannerIsolation.onPlannerServerThread()) {
			cir.setReturnValue(0);
		}
	}
}
