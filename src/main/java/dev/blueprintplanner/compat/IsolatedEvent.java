package dev.blueprintplanner.compat;

import org.jspecify.annotations.Nullable;

/** Implemented by Fabric's event class: an invoker that only calls trusted listeners (see {@link PlannerIsolation}). */
public interface IsolatedEvent<T> {
	/** Null if every listener is trusted, so the normal invoker can be used. */
	@Nullable T blueprintplanner$isolatedInvoker();
}
