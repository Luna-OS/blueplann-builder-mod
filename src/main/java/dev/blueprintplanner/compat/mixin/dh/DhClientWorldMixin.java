package dev.blueprintplanner.compat.mixin.dh;

import com.seibel.distanthorizons.core.level.DhClientLevel;
import com.seibel.distanthorizons.core.world.DhClientWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import dev.blueprintplanner.compat.dh.DhCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Multiplayer: DH keys levels by dimension, so a planner level would replace the real overworld's LOD level. */
@Mixin(value = DhClientWorld.class, remap = false)
public abstract class DhClientWorldMixin {
	@Inject(
		method = {
			"getOrLoadLevel(Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/ILevelWrapper;)Lcom/seibel/distanthorizons/core/level/DhClientLevel;",
			"getLevel(Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/ILevelWrapper;)Lcom/seibel/distanthorizons/core/level/DhClientLevel;"
		},
		at = @At("HEAD"),
		cancellable = true
	)
	private void blueprintplanner$noPlannerLevel(ILevelWrapper wrapper, CallbackInfoReturnable<DhClientLevel> cir) {
		if (DhCompat.isPlanner(wrapper)) {
			cir.setReturnValue(null);
		}
	}
}
