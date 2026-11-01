package dev.blueprintplanner.compat.mixin.ldl;

import dev.blueprintplanner.planner.ContextSwitcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entity ids of the two worlds overlap and LambDynamicLights compares light sources by id: a removal in the hidden
 * world would also remove the light of a visible entity with the same id. The hidden world has no sources anyway.
 */
@Mixin(targets = "dev.lambdaurora.lambdynlights.LambDynLights", remap = false)
public abstract class LdlRemoveLightSourceMixin {
	@Inject(method = "removeLightSource(Ldev/lambdaurora/lambdynlights/engine/source/EntityDynamicLightSourceBehavior;)V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$ignoreHiddenWorld(CallbackInfo ci) {
		if (ContextSwitcher.inTemporarySwitch()) {
			ci.cancel();
		}
	}
}
