package dev.blueprintplanner.compat.mixin.axiom;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Axiom's client state belongs to the real server. Inside the planner it would act on the real world (in
 * singleplayer every Axiom edit went straight into the real save), so it is switched off there.
 */
@Mixin(targets = "com.moulberry.axiom.AxiomClient", remap = false)
public abstract class AxiomClientMixin {
	@Inject(method = "isAxiomActive(Lnet/minecraft/world/level/GameType;)Z", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$offInPlanner(CallbackInfoReturnable<Boolean> cir) {
		if (PlannerManager.isActive() || PlannerIsolation.inPlannerClientContext()) {
			cir.setReturnValue(false);
		}
	}

	/** Real-world Axiom traffic (unfinished edits, handshake replies) keeps going to the real server while the planner is shown. */
	@WrapOperation(
		method = "sendInner",
		at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking;send(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V")
	)
	private static void blueprintplanner$sendToRealServer(CustomPacketPayload payload, Operation<Void> original) {
		if (PlannerIsolation.inPlannerClientContext()) {
			ClientPacketListener real = PlannerManager.realListener();
			if (real != null) {
				real.send(new ServerboundCustomPayloadPacket(payload));
			}
			return;
		}
		original.call(payload);
	}
}
