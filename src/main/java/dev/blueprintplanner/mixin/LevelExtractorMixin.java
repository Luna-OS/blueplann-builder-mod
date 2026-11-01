package dev.blueprintplanner.mixin;

import dev.blueprintplanner.render.OverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.gizmos.Gizmos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	/** Our boxes and labels are collected right before the frame's gizmos are handed to the renderer. */
	@Inject(method = "extractGizmos", at = @At("HEAD"))
	private void blueprintplanner$emitGizmos(CallbackInfo ci) {
		try (Gizmos.TemporaryCollection ignored = Minecraft.getInstance().levelExtractor.collectPerFrameMainThreadGizmos()) {
			OverlayRenderer.emitGizmos();
		}
	}
}
