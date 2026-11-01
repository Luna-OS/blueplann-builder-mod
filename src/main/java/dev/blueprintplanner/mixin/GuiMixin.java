package dev.blueprintplanner.mixin;

import dev.blueprintplanner.planner.ContextSwitcher;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
	/** The world in the background must not open or close screens over the visible one. */
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$noScreensFromBackground(@Nullable Screen screen, CallbackInfo ci) {
		if (ContextSwitcher.inTemporarySwitch()) {
			ci.cancel();
		}
	}
}
