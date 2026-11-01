package dev.blueprintplanner.compat.mixin.dh;

import com.seibel.distanthorizons.core.api.internal.ServerApi;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.compat.dh.DhCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The planner server is not a singleplayer world for DH (it is normally already hidden by PlannerIsolation). */
@Mixin(value = ServerApi.class, remap = false)
public abstract class DhServerApiMixin {
	@Inject(
		method = {"serverLoadEvent", "serverUnloadEvent", "serverPlayerJoinEvent", "serverPlayerDisconnectEvent", "serverPlayerLevelChangeEvent", "pluginMessageReceived"},
		at = @At("HEAD"),
		cancellable = true
	)
	private void blueprintplanner$ignorePlannerServer(CallbackInfo ci) {
		if (PlannerIsolation.onPlannerServerThread()) {
			ci.cancel();
		}
	}

	@Inject(method = {"serverLevelLoadEvent", "serverLevelUnloadEvent"}, at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$ignorePlannerLevel(IServerLevelWrapper level, CallbackInfo ci) {
		if (DhCompat.isPlanner(level)) {
			ci.cancel();
		}
	}

	@Inject(method = {"serverChunkLoadEvent", "serverChunkSaveEvent"}, at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$ignorePlannerChunk(IChunkWrapper chunk, ILevelWrapper level, CallbackInfo ci) {
		if (DhCompat.isPlanner(level)) {
			ci.cancel();
		}
	}
}
