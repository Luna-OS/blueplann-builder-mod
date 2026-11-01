package dev.blueprintplanner.compat.mixin.svc;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "de.maxhenkel.voicechat.net.ClientServerChannel", remap = false)
public abstract class SvcClientServerChannelMixin {
	/**
	 * Voice packets from the planner connection would overwrite the real server's voice state (one global client
	 * state); a secret from there would even reconnect the voice chat to this computer.
	 */
	@Inject(method = "onClientPacket(Lnet/minecraft/client/player/LocalPlayer;Lde/maxhenkel/voicechat/net/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$ignorePlannerPacket(CallbackInfo ci) {
		if (PlannerIsolation.handlingPlannerPacket()) {
			ci.cancel();
		}
	}
}
