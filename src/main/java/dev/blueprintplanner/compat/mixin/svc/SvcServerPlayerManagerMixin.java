package dev.blueprintplanner.compat.mixin.svc;

import dev.blueprintplanner.compat.PlannerIsolation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "de.maxhenkel.voicechat.voice.server.ServerPlayerManager", remap = false)
public abstract class SvcServerPlayerManagerMixin {
	/** With the Mod shield off the planner's player list would replace the one the real voice server uses for ranges. */
	@Inject(method = {"onPlayerLoggedIn(Lnet/minecraft/server/level/ServerPlayer;)V", "onPlayerLoggedOut(Lnet/minecraft/server/level/ServerPlayer;)V"},
		at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$ignorePlannerPlayer(ServerPlayer player, CallbackInfo ci) {
		if (PlannerIsolation.isPlannerLevel(player.level())) {
			ci.cancel();
		}
	}
}
