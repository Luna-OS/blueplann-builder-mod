package dev.blueprintplanner.compat.mixin.xaerolib;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xaero.lib.common.packet.ClientPacketHandlerFabric", remap = false)
public abstract class XaeroClientPacketHandlerMixin {
	/** Inside the planner the connection is the planner's: Xaero packets meant for the real server are dropped there. */
	@Inject(method = "sendToServer(Ljava/lang/Object;)V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$notToPlanner(CallbackInfo ci) {
		if (PlannerIsolation.inPlannerClientContext()) {
			ci.cancel();
		}
	}
}
