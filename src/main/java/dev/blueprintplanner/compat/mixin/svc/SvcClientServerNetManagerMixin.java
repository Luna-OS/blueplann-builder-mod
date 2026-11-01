package dev.blueprintplanner.compat.mixin.svc;

import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.ContextSwitcher;
import dev.blueprintplanner.planner.PlannerManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "de.maxhenkel.voicechat.net.ClientServerNetManager", remap = false)
public abstract class SvcClientServerNetManagerMixin {
	/**
	 * Voice chat sends its control packets (muted, groups, secret requests) to Minecraft's current connection, which
	 * is the planner's while you are in it. They always belong to the real server.
	 */
	@Inject(method = "sendToServer(Lde/maxhenkel/voicechat/net/Packet;)V", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$toRealServer(@Coerce CustomPacketPayload packet, CallbackInfo ci) {
		if (PlannerManager.plannerConnection() == null
			|| Minecraft.getInstance().isSameThread() && ContextSwitcher.installed() == ContextSwitcher.Side.REAL) {
			// the real world is installed: voice chat's own code already sends to the right place
			return;
		}
		ci.cancel();
		ClientPacketListener real = PlannerIsolation.realListener();
		if (real != null && real.getLevel() != null) {
			real.send(ClientPlayNetworking.createServerboundPacket(packet));
		}
	}
}
