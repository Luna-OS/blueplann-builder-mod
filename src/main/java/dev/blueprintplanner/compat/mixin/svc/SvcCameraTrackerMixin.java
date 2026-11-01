package dev.blueprintplanner.compat.mixin.svc;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "de.maxhenkel.voicechat.voice.client.camera.CameraTracker", remap = false)
public abstract class SvcCameraTrackerMixin {
	/** You keep hearing from where your real body stands; it does not move while you are in the planner. */
	@Inject(method = "update()V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$keepRealListener(CallbackInfo ci) {
		if (PlannerIsolation.inPlannerClientContext()) {
			ci.cancel();
		}
	}
}
