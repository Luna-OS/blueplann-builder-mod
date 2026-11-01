package dev.blueprintplanner.compat.mixin.dh;

import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import dev.blueprintplanner.compat.dh.DhCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Planner chunks never become LODs: they would end up in the database of the real world (same dimension). */
@Mixin(value = SharedApi.class, remap = false)
public abstract class DhSharedApiMixin {
	@Inject(method = "applyChunkUpdate", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$ignorePlannerChunks(IChunkWrapper chunk, ILevelWrapper level, boolean waitForLoadedWorld, CallbackInfo ci) {
		if (DhCompat.isPlanner(level)) {
			ci.cancel();
		}
	}
}
