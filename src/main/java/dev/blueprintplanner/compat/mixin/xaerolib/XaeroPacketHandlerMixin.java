package dev.blueprintplanner.compat.mixin.xaerolib;

import dev.blueprintplanner.compat.PlannerIsolation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xaero.lib.common.packet.PacketHandlerFabric", remap = false)
public abstract class XaeroPacketHandlerMixin {
	/** Every Xaero mod sends to players through here: nothing goes to the planner player. */
	@Inject(method = "sendToPlayer(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/Object;)V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$notToPlanner(ServerPlayer player, Object packet, CallbackInfo ci) {
		if (PlannerIsolation.isPlannerLevel(player.level())) {
			ci.cancel();
		}
	}
}
