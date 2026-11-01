package dev.blueprintplanner.compat.mixin.dh;

import com.seibel.distanthorizons.core.api.internal.ClientApi;
import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientApi.class, remap = false)
public abstract class DhClientApiMixin {
	/** Logging into the planner is not joining a new server: DH would close the LOD world of the real one. */
	@Inject(method = {"onClientOnlyConnected", "onClientOnlyDisconnected"}, at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$ignorePlannerConnection(CallbackInfo ci) {
		if (PlannerIsolation.inPlannerClientContext()) {
			ci.cancel();
		}
	}

	/** The real world's LODs share coordinates with the planner, so they are not drawn around it. */
	@Inject(method = {"renderLods", "renderDeferredLodsForShaders", "renderFadeOpaque", "renderFadeTransparent"}, at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$noLodsInPlanner(CallbackInfo ci) {
		if (PlannerIsolation.inPlannerClientContext()) {
			ci.cancel();
		}
	}
}
