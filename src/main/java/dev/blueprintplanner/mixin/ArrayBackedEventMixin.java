package dev.blueprintplanner.mixin;

import dev.blueprintplanner.compat.IsolatedEvent;
import dev.blueprintplanner.compat.PlannerIsolation;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Builds a second invoker without other mods' listeners whenever Fabric rebuilds the normal one. */
@Mixin(targets = "net.fabricmc.fabric.impl.base.event.ArrayBackedEvent", remap = false)
public abstract class ArrayBackedEventMixin<T> implements IsolatedEvent<T> {
	@Shadow
	@Final
	private Function<T[], T> invokerFactory;
	@Shadow
	private T[] handlers;

	@Unique
	private volatile @Nullable T blueprintplanner$isolatedInvoker;

	@Inject(method = "update", at = @At("TAIL"))
	private void blueprintplanner$updateIsolatedInvoker(CallbackInfo ci) {
		T[] trusted = PlannerIsolation.trustedListeners(this.handlers);
		this.blueprintplanner$isolatedInvoker = trusted == this.handlers ? null : this.invokerFactory.apply(trusted);
	}

	@Override
	public @Nullable T blueprintplanner$isolatedInvoker() {
		return this.blueprintplanner$isolatedInvoker;
	}
}
