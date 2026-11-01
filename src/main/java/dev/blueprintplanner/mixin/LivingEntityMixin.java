package dev.blueprintplanner.mixin;

import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** The server announces every hit with a damage event; for the real player that means: leave the planner. */
	@Inject(method = "handleDamageEvent", at = @At("HEAD"))
	private void blueprintplanner$detectRealDamage(DamageSource source, CallbackInfo ci) {
		if (PlannerManager.phase() != PlannerManager.Phase.OFF && (Object) this == PlannerManager.realPlayer()) {
			PlannerManager.onRealPlayerDamageEvent();
		}
	}
}
