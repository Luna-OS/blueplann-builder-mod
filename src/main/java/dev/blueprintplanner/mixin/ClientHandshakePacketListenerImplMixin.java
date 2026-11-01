package dev.blueprintplanner.mixin;

import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakePacketListenerImplMixin {
	@Shadow
	@Final
	private Connection connection;

	@Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$plannerDisconnect(DisconnectionDetails details, CallbackInfo ci) {
		if (PlannerManager.isPlannerConnection(this.connection)) {
			PlannerManager.onPlannerDisconnected(details.reason());
			ci.cancel();
		}
	}
}
