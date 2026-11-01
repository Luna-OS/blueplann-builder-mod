package dev.blueprintplanner.compat.mixin.xaerolib;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xaero.lib.client.config.sync.ClientConfigSynchronizer", remap = false)
public abstract class XaeroClientConfigSynchronizerMixin {
	/**
	 * The lib resets the synced server config when the current level's server has no Xaero lib. The planner never
	 * says it has one, so entering it would drop the real server's config (and its restrictions) until relog.
	 */
	@Inject(method = "reset()V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$keepRealServerConfig(CallbackInfo ci) {
		if (PlannerIsolation.inPlannerClientContext() || PlannerIsolation.handlingPlannerPacket()) {
			ci.cancel();
		}
	}
}
