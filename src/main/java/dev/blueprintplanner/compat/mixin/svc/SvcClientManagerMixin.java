package dev.blueprintplanner.compat.mixin.svc;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "de.maxhenkel.voicechat.voice.client.ClientManager", remap = false)
public abstract class SvcClientManagerMixin {
	/** With the Mod shield off, joining the planner would close the real voice connection. */
	@Inject(method = "onJoinWorld()V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$keepRealVoice(CallbackInfo ci) {
		if (PlannerIsolation.handlingPlannerPacket()) {
			ci.cancel();
		}
	}

	/** With the Mod shield off, "Open to LAN" in the planner would move the real voice server and drop your voice connection. */
	@Inject(method = "onPublishServer(I)V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$notForPlanner(int port, CallbackInfo ci) {
		if (PlannerIsolation.onPlannerServerThread()) {
			ci.cancel();
		}
	}
}
