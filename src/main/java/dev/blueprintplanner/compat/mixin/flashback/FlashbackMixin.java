package dev.blueprintplanner.compat.mixin.flashback;

import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A recording started inside the planner would be bound to the planner's world and registries. */
@Mixin(targets = "com.moulberry.flashback.Flashback", remap = false)
public abstract class FlashbackMixin {
	@Inject(method = "startRecordingReplay()V", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$notInPlanner(CallbackInfo ci) {
		if (PlannerManager.isActive() || PlannerIsolation.inPlannerClientContext()) {
			PlannerManager.message(Component.translatable("blueprintplanner.compat.flashback").withStyle(ChatFormatting.RED), true);
			ci.cancel();
		}
	}
}
