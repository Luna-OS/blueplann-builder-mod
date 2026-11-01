package probe;

import de.johni0702.minecraft.bobby.ext.ClientChunkCacheExt;
import dev.blueprintplanner.planner.PlannerManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;

/** Bobby keeps its fake-chunk cache for the real world, but not for the planner. */
public final class BobbyProbe {
	private BobbyProbe() {
	}

	public static boolean plannerHasNoBobbyCache() {
		if (!FabricLoader.getInstance().isModLoaded("bobby")) {
			return true;
		}
		return Check.run();
	}

	private static final class Check {
		static boolean run() {
			ClientLevel planner = PlannerManager.plannerLevel();
			ClientLevel real = PlannerManager.realLevel();
			return planner != null && real != null
				&& ((ClientChunkCacheExt) planner.getChunkSource()).bobby_getFakeChunkManager() == null
				&& ((ClientChunkCacheExt) real.getChunkSource()).bobby_getFakeChunkManager() != null;
		}
	}
}
