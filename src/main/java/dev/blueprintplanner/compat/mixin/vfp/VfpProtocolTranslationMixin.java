package dev.blueprintplanner.compat.mixin.vfp;

import dev.blueprintplanner.compat.PlannerIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ViaFabricPlus keeps one global target version (the real server's). The planner always talks the native protocol
 * over a memory channel without Via; with an old target version its version-specific code would crash there.
 */
@Mixin(targets = "com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslationImpl", remap = false)
public abstract class VfpProtocolTranslationMixin {
	@Unique
	private static Object blueprintplanner$nativeVersion;

	@Inject(method = "targetVersion()Lcom/viaversion/viaversion/api/protocol/version/ProtocolVersion;", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$nativeForPlanner(CallbackInfoReturnable<Object> cir) {
		if (!PlannerIsolation.plannerExists()) {
			return;
		}
		// render thread with the planner installed, the planner server, or the planner's memory channel threads
		if (PlannerIsolation.inPlannerClientContext() || PlannerIsolation.onPlannerServerThread()
			|| Thread.currentThread().getName().startsWith("Netty Local IO")) {
			Object nativeVersion = blueprintplanner$nativeVersion;
			if (nativeVersion == null) {
				try {
					nativeVersion = this.getClass().getField("NATIVE_VERSION").get(null);
					blueprintplanner$nativeVersion = nativeVersion;
				} catch (ReflectiveOperationException e) {
					return;
				}
			}
			cir.setReturnValue(nativeVersion);
		}
	}
}
