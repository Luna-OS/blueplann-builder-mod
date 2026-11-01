package dev.blueprintplanner.mixin;

import dev.blueprintplanner.planner.PlannerManager;
import dev.blueprintplanner.planner.PlannerServer;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
	@Shadow
	@Final
	protected MinecraftServer server;

	/** Configuration and play listeners both end up here; the reason is shown on the client if its side only saw the channel close. */
	@Inject(method = "onDisconnect", at = @At("HEAD"))
	private void blueprintplanner$rememberPlannerReason(DisconnectionDetails details, CallbackInfo ci) {
		if (this.server instanceof PlannerServer) {
			PlannerManager.onPlannerServerDisconnect(details.reason());
		}
	}
}
