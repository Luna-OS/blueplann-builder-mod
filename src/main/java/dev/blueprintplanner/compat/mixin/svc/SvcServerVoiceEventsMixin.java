package dev.blueprintplanner.compat.mixin.svc;

import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.PlannerServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Normally the Mod shield already keeps these events away from the planner. With the shield off, the planner server
 * would replace the real voice server (singleplayer) and a "force voice chat" timer could kick the planner player.
 */
@Mixin(targets = "de.maxhenkel.voicechat.voice.server.ServerVoiceEvents", remap = false)
public abstract class SvcServerVoiceEventsMixin {
	@Inject(method = {"serverStarting(Lnet/minecraft/server/MinecraftServer;)V", "serverStopping(Lnet/minecraft/server/MinecraftServer;)V"},
		at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$noVoiceServerForPlanner(MinecraftServer server, CallbackInfo ci) {
		if (server instanceof PlannerServer) {
			ci.cancel();
		}
	}

	@Inject(method = {"playerLoggedIn(Lnet/minecraft/server/level/ServerPlayer;)V", "playerLoggedOut(Lnet/minecraft/server/level/ServerPlayer;)V"},
		at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$ignorePlannerPlayer(ServerPlayer player, CallbackInfo ci) {
		if (PlannerIsolation.isPlannerLevel(player.level())) {
			ci.cancel();
		}
	}
}
