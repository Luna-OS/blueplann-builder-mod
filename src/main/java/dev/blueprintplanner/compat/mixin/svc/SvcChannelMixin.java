package dev.blueprintplanner.compat.mixin.svc;

import dev.blueprintplanner.compat.PlannerIsolation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "de.maxhenkel.voicechat.net.Channel", remap = false)
public abstract class SvcChannelMixin {
	/**
	 * Backstop: voice packets that still reach the planner server are dropped. In singleplayer the planner player has
	 * your UUID, so the real voice server would otherwise act on them (new secret, group changes).
	 */
	@Inject(method = "onServerPacket(Lnet/minecraft/server/level/ServerPlayer;Lde/maxhenkel/voicechat/net/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$ignorePlannerPlayer(ServerPlayer player, @Coerce CustomPacketPayload packet, CallbackInfo ci) {
		if (PlannerIsolation.isPlannerLevel(player.level())) {
			ci.cancel();
		}
	}
}
