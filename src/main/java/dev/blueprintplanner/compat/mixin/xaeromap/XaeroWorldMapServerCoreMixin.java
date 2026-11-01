package dev.blueprintplanner.compat.mixin.xaeromap;

import dev.blueprintplanner.compat.PlannerIsolation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xaero.map.server.core.XaeroWorldMapServerCore", remap = false)
public abstract class XaeroWorldMapServerCoreMixin {
	/** Same as the minimap: no server data on the planner server, so the player join would kick the planner player. */
	@Inject(method = "onServerWorldInfo(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$skipPlannerPlayer(Player player, CallbackInfo ci) {
		if (PlannerIsolation.isPlannerLevel(player.level())) {
			ci.cancel();
		}
	}
}
