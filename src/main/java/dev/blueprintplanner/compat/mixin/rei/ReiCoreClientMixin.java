package dev.blueprintplanner.compat.mixin.rei;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The planner's recipe packets must not reload REI: it would drop the real server's recipes and use planner registries. */
@Mixin(targets = "me.shedaniel.rei.RoughlyEnoughItemsCoreClient", remap = false)
public abstract class ReiCoreClientMixin {
	@Inject(
		method = "reloadPlugins(Lorg/apache/commons/lang3/mutable/MutableLong;Lme/shedaniel/rei/api/common/registry/ReloadStage;Lnet/minecraft/core/RegistryAccess;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void blueprintplanner$notForPlanner(CallbackInfo ci) {
		if (PlannerIsolation.handlingPlannerPacket()) {
			ci.cancel();
		}
	}
}
