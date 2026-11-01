package dev.blueprintplanner.mixin;

import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.PlannerManager;
import dev.blueprintplanner.planner.PlannerSky;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	/** The planner's level is created while its login packet is handled, i.e. inside the planner context. */
	@Inject(method = "addEnvironmentAttributeLayers", at = @At("RETURN"))
	private void blueprintplanner$plannerSky(EnvironmentAttributeSystem.Builder builder, CallbackInfoReturnable<EnvironmentAttributeSystem.Builder> cir) {
		if (PlannerIsolation.inPlannerClientContext()) {
			PlannerSky.addLayers(builder);
		}
	}

	/** Both worlds share coordinates, so particles of the hidden world would show up in the visible one. */
	@Inject(method = "doAddParticle", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$noBackgroundParticles(
		ParticleOptions particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, double xd, double yd, double zd, CallbackInfo ci
	) {
		if (PlannerManager.isBackgroundLevel((ClientLevel) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$noBackgroundDestroyEffects(BlockPos pos, BlockState state, CallbackInfo ci) {
		if (PlannerManager.isBackgroundLevel((ClientLevel) (Object) this)) {
			ci.cancel();
		}
	}
}
