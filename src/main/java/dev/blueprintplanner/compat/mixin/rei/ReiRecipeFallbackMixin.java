package dev.blueprintplanner.compat.mixin.rei;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "me.shedaniel.rei.impl.client.registry.display.ClientRecipeFallback", remap = false)
public abstract class ReiRecipeFallbackMixin {
	@Inject(method = {"onServerRecipeBookAdd(Ljava/util/List;)V", "onServerRecipeBookRemove(Ljava/util/Set;)V"}, at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$notForPlanner(CallbackInfo ci) {
		if (PlannerIsolation.handlingPlannerPacket()) {
			ci.cancel();
		}
	}
}
