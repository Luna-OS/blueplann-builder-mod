package dev.blueprintplanner.compat.mixin.axiom;

import dev.blueprintplanner.compat.PlannerIsolation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Defence in depth: no Axiom edit or history entry may come from the planner (they would replay into the real world). */
@Mixin(targets = "com.moulberry.axiom.world_modification.Dispatcher", remap = false)
public abstract class AxiomDispatcherMixin {
	@Inject(
		method = {
			"push(Lcom/moulberry/axiom/world_modification/HistoryEntry;)V",
			"apply(Lcom/moulberry/axiom/world_modification/BlockOrBiomeBuffer;Z)V",
			"applyBlockBuffer(Lcom/moulberry/axiom/world_modification/BlockBuffer;Z)V"
		},
		at = @At("HEAD"),
		cancellable = true
	)
	private static void blueprintplanner$noPlannerEdits(CallbackInfo ci) {
		if (PlannerIsolation.isPlannerLevel(Minecraft.getInstance().level)) {
			ci.cancel();
		}
	}

	/**
	 * The planner login runs Axiom's "dimension changed" handling (Axiom's own class cannot be hooked, it loads too
	 * early); it must not wipe pending real-world edits and chunk requests.
	 */
	@Inject(method = "reset()V", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$keepRealDispatches(CallbackInfo ci) {
		if (PlannerIsolation.inPlannerClientContext()) {
			ci.cancel();
		}
	}

	/** Pending real-world dispatches wait until the real world is shown again. */
	@Inject(method = "runTasks()V", at = @At("HEAD"), cancellable = true)
	private static void blueprintplanner$pauseInPlanner(CallbackInfo ci) {
		if (PlannerIsolation.inPlannerClientContext()) {
			ci.cancel();
		}
	}
}
