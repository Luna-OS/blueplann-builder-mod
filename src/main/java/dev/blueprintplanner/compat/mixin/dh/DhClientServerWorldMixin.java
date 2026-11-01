package dev.blueprintplanner.compat.mixin.dh;

import com.seibel.distanthorizons.core.level.DhClientServerLevel;
import com.seibel.distanthorizons.core.world.DhClientServerWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import dev.blueprintplanner.compat.dh.DhCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Singleplayer: planner levels have no counterpart on the real integrated server (DH asserts that they do). */
@Mixin(value = DhClientServerWorld.class, remap = false)
public abstract class DhClientServerWorldMixin {
	@Inject(
		method = "getOrLoadLevel(Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/ILevelWrapper;)Lcom/seibel/distanthorizons/core/level/DhClientServerLevel;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void blueprintplanner$noPlannerLevel(ILevelWrapper wrapper, CallbackInfoReturnable<DhClientServerLevel> cir) {
		if (DhCompat.isPlanner(wrapper)) {
			cir.setReturnValue(null);
		}
	}
}
