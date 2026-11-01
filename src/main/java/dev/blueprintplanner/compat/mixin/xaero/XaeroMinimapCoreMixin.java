package dev.blueprintplanner.compat.mixin.xaero;

import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xaero.common.core.XaeroMinimapCore", remap = false)
public abstract class XaeroMinimapCoreMixin {
	/**
	 * A login on any connection ends the current minimap session and starts a new one. For the planner that ended the
	 * real world's minimap until the next relog, and the planner session was never cleaned up.
	 */
	@Inject(method = "onPlayNetHandler(Lnet/minecraft/client/multiplayer/ClientPacketListener;Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",
		at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$noSessionForPlanner(ClientPacketListener listener, ClientboundLoginPacket packet, CallbackInfo ci) {
		if (PlannerManager.isPlannerConnection(listener.getConnection())) {
			ci.cancel();
		}
	}
}
