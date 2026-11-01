package dev.blueprintplanner.compat.mixin.plasmo;

import dev.blueprintplanner.compat.PlannerIsolation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "su.plo.slib.mod.mixinkt.MixinPlayerListKt", remap = false)
public abstract class PlasmoPlayerListMixin {
	@Inject(method = "onRespawn(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$ignorePlannerPlayer(ServerPlayer player, CallbackInfo ci) {
		if (PlannerIsolation.isPlannerLevel(player.level())) {
			ci.cancel();
		}
	}
}
