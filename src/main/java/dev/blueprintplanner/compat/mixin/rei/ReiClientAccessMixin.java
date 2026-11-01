package dev.blueprintplanner.compat.mixin.rei;

import dev.blueprintplanner.compat.PlannerIsolation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** REI reloads on its own thread and reads "the" client world; it always gets the real one. */
@Mixin(targets = "me.shedaniel.rei.impl.common.InternalRegistryAccess", remap = false)
public abstract class ReiClientAccessMixin {
	@Inject(method = "getFromClient()Lnet/minecraft/core/RegistryAccess;", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$realRegistries(CallbackInfoReturnable<RegistryAccess> cir) {
		ClientLevel real = PlannerIsolation.plannerExists() ? PlannerIsolation.realLevel() : null;
		if (real != null) {
			cir.setReturnValue(real.registryAccess());
		}
	}
}
