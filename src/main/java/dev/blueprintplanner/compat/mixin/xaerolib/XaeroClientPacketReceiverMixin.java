package dev.blueprintplanner.compat.mixin.xaerolib;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xaero.lib.common.packet.ClientPacketReceiverFabric", remap = false)
public abstract class XaeroClientPacketReceiverMixin {
	/**
	 * All Xaero payloads arrive here. Their handlers change global or real-world state (server config, level id,
	 * tracked players), so one coming over the planner connection is dropped.
	 */
	@Inject(method = "receive(Lxaero/lib/common/packet/payload/PacketPayload;Lnet/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking$Context;)V",
		at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$ignorePlannerPayload(CallbackInfo ci) {
		if (PlannerIsolation.handlingPlannerPacket()) {
			ci.cancel();
		}
	}
}
