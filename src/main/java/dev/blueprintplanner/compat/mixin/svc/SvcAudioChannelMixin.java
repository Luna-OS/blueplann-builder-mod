package dev.blueprintplanner.compat.mixin.svc;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "de.maxhenkel.voicechat.voice.client.AudioChannel", remap = false)
public abstract class SvcAudioChannelMixin {
	/** Nearby speakers are real players: look them up in the real world, also while the planner is shown. */
	@WrapOperation(method = "writeToSpeaker(Lde/maxhenkel/voicechat/voice/common/SoundPacket;[S)V",
		at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;"))
	private ClientLevel blueprintplanner$realLevel(Minecraft mc, Operation<ClientLevel> original) {
		ClientLevel level = original.call(mc);
		// null while the planner connects (its side has no level yet) and Minecraft's fields are briefly the planner's
		if (level == null || PlannerIsolation.isPlannerLevel(level)) {
			ClientLevel real = PlannerIsolation.realLevel();
			if (real != null) {
				return real;
			}
		}
		return level;
	}

	/**
	 * The real world is not ticked while the planner is shown, so other players stay where they were when you entered
	 * and walk only in their interpolation target. Distance and direction of their voice are taken from there.
	 */
	@WrapOperation(method = "writeToSpeaker(Lde/maxhenkel/voicechat/voice/common/SoundPacket;[S)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 blueprintplanner$latestRealEye(Entity entity, Operation<Vec3> original) {
		Vec3 eye = original.call(entity);
		if (PlannerManager.isActive() && entity.level() == PlannerIsolation.realLevel()) {
			InterpolationHandler interpolation = entity.getInterpolation();
			if (interpolation != null && interpolation.hasActiveInterpolation()) {
				return interpolation.target().position().add(0, entity.getEyeHeight(), 0);
			}
		}
		return eye;
	}
}
