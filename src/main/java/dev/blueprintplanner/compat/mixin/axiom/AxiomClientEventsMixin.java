package dev.blueprintplanner.compat.mixin.axiom;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blueprintplanner.compat.PlannerIsolation;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.moulberry.axiom.ClientEvents", remap = false)
public abstract class AxiomClientEventsMixin {
	/** Axiom packets from the real server stay queued while the planner is shown (they read the installed world). */
	@WrapOperation(
		method = "lambda$register$16(Lcom/moulberry/axiom/utils/BooleanWrapper;Lnet/minecraft/client/Minecraft;)V",
		at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ConcurrentLinkedQueue;isEmpty()Z")
	)
	private static boolean blueprintplanner$holdRealPackets(ConcurrentLinkedQueue<?> queue, Operation<Boolean> original) {
		return PlannerIsolation.inPlannerClientContext() || original.call(queue);
	}

	/** Closing the planner connection is not leaving the server (Axiom would switch the real player to creative). */
	@Inject(method = "beforeDisconnect()V", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$notForPlanner(CallbackInfo ci) {
		if (PlannerIsolation.handlingPlannerPacket()) {
			ci.cancel();
		}
	}
}
