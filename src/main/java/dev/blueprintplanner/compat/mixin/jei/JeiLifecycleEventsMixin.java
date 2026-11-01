package dev.blueprintplanner.compat.mixin.jei;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "mezz.jei.fabric.events.JeiLifecycleEvents", remap = false)
public abstract class JeiLifecycleEventsMixin {
	/** JEI's own events (recipes updated, game stop) come from vanilla packets; the planner's do not count. */
	@ModifyReturnValue(method = "createRunnableInvoker([Ljava/lang/Runnable;)Ljava/lang/Runnable;", at = @At("RETURN"))
	private static Runnable blueprintplanner$notForPlannerPackets(Runnable invoker) {
		return () -> {
			if (!PlannerIsolation.handlingPlannerPacket()) {
				invoker.run();
			}
		};
	}
}
