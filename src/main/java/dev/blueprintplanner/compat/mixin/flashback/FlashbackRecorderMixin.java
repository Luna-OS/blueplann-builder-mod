package dev.blueprintplanner.compat.mixin.flashback;

import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.PlannerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Flashback recording belongs to the real world. Planner packets, sounds and registries must not end up in it:
 * mixing the planner's registries into the recorder crashes the game, and planner chunks would corrupt the replay.
 * While the planner is shown, the recording is paused (Flashback's own pause, resumed with a fresh snapshot).
 */
@Mixin(targets = "com.moulberry.flashback.record.Recorder", remap = false)
public abstract class FlashbackRecorderMixin {
	@Shadow
	private volatile boolean isPaused;

	@Unique
	private boolean blueprintplanner$forcedPause;
	@Unique
	private boolean blueprintplanner$userPaused;

	@Inject(method = "setRegistryAccess(Lnet/minecraft/core/RegistryAccess;)V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$keepRealRegistries(CallbackInfo ci) {
		if (PlannerIsolation.handlingPlannerPacket() || PlannerIsolation.inPlannerClientContext()) {
			ci.cancel();
		}
	}

	@Inject(
		method = {
			"writePacketAsync(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/ConnectionProtocol;)V",
			"writeLevelEvent(ILnet/minecraft/core/BlockPos;IZ)V",
			"writeSound(Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;DDDFFJ)V",
			"writeEntitySound(Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;Lnet/minecraft/world/entity/Entity;FFJ)V"
		},
		at = @At("HEAD"),
		cancellable = true
	)
	private void blueprintplanner$skipPlannerData(CallbackInfo ci) {
		if (PlannerIsolation.handlingPlannerPacket() || PlannerIsolation.inPlannerClientContext()) {
			ci.cancel();
		}
	}

	@Inject(method = "endTick(Z)V", at = @At("HEAD"))
	private void blueprintplanner$pauseWhilePlanner(boolean close, CallbackInfo ci) {
		if (PlannerManager.isActive()) {
			if (!this.blueprintplanner$forcedPause) {
				this.blueprintplanner$userPaused = this.isPaused;
				this.blueprintplanner$forcedPause = true;
			}
			this.isPaused = true;
		} else if (this.blueprintplanner$forcedPause && !close) {
			this.isPaused = this.blueprintplanner$userPaused;
			this.blueprintplanner$forcedPause = false;
		}
	}
}
