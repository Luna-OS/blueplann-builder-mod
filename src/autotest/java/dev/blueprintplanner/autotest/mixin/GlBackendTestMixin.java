package dev.blueprintplanner.autotest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Test only: the software GLX of Xvfb offers no sRGB-capable framebuffer, so do not require one. */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlBackend")
public abstract class GlBackendTestMixin {
	@WrapOperation(method = "createWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_GL_SetAttribute(II)Z"))
	private boolean blueprintplannerautotest$noSrgbRequirement(int attribute, int value, Operation<Boolean> original) {
		if (attribute == 22 && System.getenv("BLUEPRINTPLANNER_HEADLESS") != null) {
			return true;
		}
		return original.call(attribute, value);
	}
}
