package dev.blueprintplanner.compat.mixin.rei;

import dev.blueprintplanner.compat.PlannerIsolation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "me.shedaniel.rei.impl.common.util.InstanceHelper", remap = false)
public abstract class ReiInstanceHelperMixin {
	@Inject(method = "connectionFromClient()Lnet/minecraft/client/multiplayer/ClientPacketListener;", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$realConnection(CallbackInfoReturnable<ClientPacketListener> cir) {
		ClientPacketListener real = PlannerIsolation.plannerExists() ? PlannerIsolation.realListener() : null;
		if (real != null) {
			cir.setReturnValue(real);
		}
	}
}
