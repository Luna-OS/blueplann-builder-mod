package dev.blueprintplanner.compat.mixin.plasmo;

import com.llamalad7.mixinextras.sugar.Local;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The voice connection always stays on the real server: voice packets arriving on the planner connection are dropped. */
@Mixin(targets = "su.plo.voice.client.connection.ModClientChannelHandler", remap = false)
public abstract class PlasmoClientChannelMixin {
	@Inject(
		method = {
			"receive(Lnet/minecraft/network/Connection;[B)V",
			"receive(Lnet/minecraft/network/Connection;Lnet/minecraft/network/FriendlyByteBuf;)V",
			"receive(Lnet/minecraft/network/Connection;Lsu/plo/voice/proto/packets/Packet;)V"
		},
		at = @At("HEAD"),
		cancellable = true
	)
	private void blueprintplanner$ignorePlannerConnection(CallbackInfo ci, @Local(argsOnly = true) Connection connection) {
		if (PlannerManager.isPlannerConnection(connection)) {
			ci.cancel();
		}
	}
}
