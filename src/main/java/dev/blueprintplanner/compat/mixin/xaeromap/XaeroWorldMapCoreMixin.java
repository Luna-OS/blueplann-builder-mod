package dev.blueprintplanner.compat.mixin.xaeromap;

import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xaero.map.core.XaeroWorldMapCore", remap = false)
public abstract class XaeroWorldMapCoreMixin {
	/**
	 * A login ends the current world map session and starts one for the new connection. For the planner the real map
	 * stayed dead, and the orphaned planner session kept Xaero's map thread alive so the game could not quit.
	 */
	@Inject(method = "onPlayNetHandler(Lnet/minecraft/client/multiplayer/ClientPacketListener;Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",
		at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$noSessionForPlanner(ClientPacketListener listener, ClientboundLoginPacket packet, CallbackInfo ci) {
		if (PlannerManager.isPlannerConnection(listener.getConnection())) {
			ci.cancel();
		}
	}

	/** The planner's spawn would become the real map's spawn (used to tell sub-worlds apart). */
	@Inject(method = "handlePlayerSetSpawnPacket(Lnet/minecraft/network/protocol/game/ClientboundSetDefaultSpawnPositionPacket;)V",
		at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$ignorePlannerSpawn(CallbackInfo ci) {
		if (PlannerIsolation.handlingPlannerPacket()) {
			ci.cancel();
		}
	}
}
