package dev.blueprintplanner.compat.mixin.xaeromap;

import dev.blueprintplanner.compat.PlannerIsolation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xaero.map.events.CommonEvents", remap = false)
public abstract class XaeroWorldMapCommonEventsMixin {
	/** The server player tick reads the missing server data without a null check (would crash the planner every tick). */
	@Inject(method = "handlePlayerTickStart(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$skipPlannerPlayer(Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer && PlannerIsolation.isPlannerLevel(player.level())) {
			ci.cancel();
		}
	}
}
