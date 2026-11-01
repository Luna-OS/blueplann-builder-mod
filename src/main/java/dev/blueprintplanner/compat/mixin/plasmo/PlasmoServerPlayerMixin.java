package dev.blueprintplanner.compat.mixin.plasmo;

import dev.blueprintplanner.compat.PlannerIsolation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The planner player has the same UUID as you. Plasmo Voice would rebind your voice-server player to it, so the real
 * voice server would place you in the planner world. Planner players are simply not handed to Plasmo Voice.
 */
@Mixin(targets = "su.plo.slib.mod.mixinkt.MixinServerPlayerKt", remap = false)
public abstract class PlasmoServerPlayerMixin {
	@Inject(method = "updateOptions(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ClientInformation;)V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$ignorePlannerPlayer(ServerPlayer player, ClientInformation information, CallbackInfo ci) {
		if (PlannerIsolation.isPlannerLevel(player.level())) {
			ci.cancel();
		}
	}
}
