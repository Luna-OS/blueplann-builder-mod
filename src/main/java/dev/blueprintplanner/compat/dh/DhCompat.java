package dev.blueprintplanner.compat.dh;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import dev.blueprintplanner.compat.PlannerIsolation;
import org.jspecify.annotations.Nullable;

/**
 * Distant Horizons keeps exactly one "DH world" and derives it from server starts and connections. The planner
 * must stay invisible to it: otherwise DH swaps its LOD world for the planner (crash "Assertion failed" in
 * DhClientServerWorld) and writes planner terrain into the LOD database of the real world.
 */
public final class DhCompat {
	private DhCompat() {
	}

	public static boolean isPlanner(@Nullable ILevelWrapper wrapper) {
		return wrapper != null && PlannerIsolation.isPlannerLevel(wrapper.getWrappedMcObject());
	}
}
