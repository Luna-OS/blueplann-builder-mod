package dev.blueprintplanner.compat.mixin.iris;

import dev.blueprintplanner.compat.PlannerIsolation;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iris posts its login messages on every login; the planner login must not repeat (or swallow) them. */
@Mixin(targets = "net.irisshaders.iris.Iris", remap = false)
public abstract class IrisMessagesMixin {
	@Inject(method = "getStoredError()Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$keepErrorForRealWorld(CallbackInfoReturnable<Optional<?>> cir) {
		if (PlannerIsolation.handlingPlannerPacket()) {
			cir.setReturnValue(Optional.empty());
		}
	}

	@Inject(method = "loadedIncompatiblePack()Z", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$noRepeatedPackWarning(CallbackInfoReturnable<Boolean> cir) {
		if (PlannerIsolation.handlingPlannerPacket()) {
			cir.setReturnValue(false);
		}
	}
}
