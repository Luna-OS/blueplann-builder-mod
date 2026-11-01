package dev.blueprintplanner.compat.mixin.iris;

import dev.blueprintplanner.compat.PlannerIsolation;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.irisshaders.iris.UpdateChecker", remap = false)
public abstract class IrisUpdateCheckerMixin {
	@Inject(method = "getUpdateMessage()Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$noRepeatedUpdateMessage(CallbackInfoReturnable<Optional<?>> cir) {
		if (PlannerIsolation.handlingPlannerPacket()) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
