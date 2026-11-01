package dev.blueprintplanner.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.blueprintplanner.compat.IsolatedEvent;
import dev.blueprintplanner.compat.PlannerIsolation;
import net.fabricmc.fabric.api.event.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Other mods' listeners do not run for the planner server and the planner connection (see {@link PlannerIsolation}). */
@Mixin(value = Event.class, remap = false)
public abstract class FabricEventMixin<T> {
	@ModifyReturnValue(method = "invoker", at = @At("RETURN"))
	private T blueprintplanner$isolatePlanner(T invoker) {
		if (this instanceof IsolatedEvent<?> isolated) {
			@SuppressWarnings("unchecked")
			T trusted = (T) isolated.blueprintplanner$isolatedInvoker();
			if (trusted != null && PlannerIsolation.shouldIsolate((Event<?>) (Object) this)) {
				return trusted;
			}
		}
		return invoker;
	}
}
