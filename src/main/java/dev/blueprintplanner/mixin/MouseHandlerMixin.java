package dev.blueprintplanner.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blueprintplanner.blueprint.PlacementTool;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.ScrollWheelHandler;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	/** Sneak + mouse wheel moves the hologram instead of switching the hotbar slot (only reached without a screen). */
	@WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ScrollWheelHandler;onMouseScroll(DD)Lorg/joml/Vector2i;"))
	private Vector2i blueprintplanner$moveHologram(ScrollWheelHandler handler, double x, double y, Operation<Vector2i> original) {
		Vector2i steps = original.call(handler, x, y);
		if (steps.y != 0 && PlacementTool.onScroll(steps.y)) {
			return new Vector2i(0, 0);
		}
		return steps;
	}
}
