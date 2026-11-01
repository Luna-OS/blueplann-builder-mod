package dev.blueprintplanner.mixin;

import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
	/** No recipe/advancement toast spam from the planner world. */
	@Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$noPlannerToasts(Toast toast, CallbackInfo ci) {
		if (PlannerManager.isHandlingPlannerPacket()) {
			ci.cancel();
		}
	}
}
