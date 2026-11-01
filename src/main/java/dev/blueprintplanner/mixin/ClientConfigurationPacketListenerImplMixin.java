package dev.blueprintplanner.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class ClientConfigurationPacketListenerImplMixin {
	/** The planner client must share registries with the planner server (like singleplayer does with its server). */
	@WrapOperation(
		method = "handleConfigurationFinished",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSingleplayerServer()Lnet/minecraft/client/server/IntegratedServer;")
	)
	private IntegratedServer blueprintplanner$plannerRegistries(Minecraft minecraft, Operation<IntegratedServer> original) {
		if (PlannerManager.isPlannerConnection(((ClientCommonPacketListenerAccessor) this).blueprintplanner$getConnection())) {
			return PlannerManager.server();
		}
		return original.call(minecraft);
	}

	@Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$plannerDisconnect(DisconnectionDetails details, CallbackInfo ci) {
		if (PlannerManager.isPlannerConnection(((ClientCommonPacketListenerAccessor) this).blueprintplanner$getConnection())) {
			PlannerManager.onPlannerDisconnected(details.reason());
			ci.cancel();
		}
	}
}
