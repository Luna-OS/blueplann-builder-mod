package dev.blueprintplanner.compat.mixin.xaerolib;

import dev.blueprintplanner.planner.PlannerServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xaero.lib.common.config.server.ServerConfigManager", remap = false)
public abstract class XaeroServerConfigManagerMixin {
	/**
	 * With the Mod shield off the lib would point its global server config at the planner server and never back
	 * (in singleplayer the real world's Xaero settings would then follow the planner).
	 */
	@Inject(method = "setServer(Lnet/minecraft/server/MinecraftServer;)V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$notThePlanner(MinecraftServer server, CallbackInfo ci) {
		if (server instanceof PlannerServer) {
			ci.cancel();
		}
	}
}
