package probe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Real-world state of other mods that must survive the planner: Xaero's minimap and world map sessions and JEI's
 * runtime (a planner login used to end the Xaero sessions and restart JEI).
 */
public final class RealStateProbe {
	private static final String[][] FIELDS = {
		{"xaerominimap", "xaero.common.core.XaeroMinimapCore", "currentSession"},
		{"xaeroworldmap", "xaero.map.core.XaeroWorldMapCore", "currentSession"},
		{"jei", "mezz.jei.common.Internal", "jeiRuntime"},
	};
	private static final List<Object> SNAPSHOT = new ArrayList<>();

	private RealStateProbe() {
	}

	public static void snapshot() {
		SNAPSHOT.clear();
		for (String[] field : FIELDS) {
			SNAPSHOT.add(read(field));
		}
	}

	/** Problems found, empty if every installed mod still has the same real-world object as at the snapshot. */
	public static List<String> changed() {
		List<String> problems = new ArrayList<>();
		for (int i = 0; i < FIELDS.length; i++) {
			Object now = read(FIELDS[i]);
			if (now != SNAPSHOT.get(i)) {
				problems.add(FIELDS[i][1] + "." + FIELDS[i][2] + " changed: " + SNAPSHOT.get(i) + " -> " + now);
			} else if (now == null && FabricLoader.getInstance().isModLoaded(FIELDS[i][0])) {
				problems.add(FIELDS[i][1] + "." + FIELDS[i][2] + " is null");
			}
		}
		return problems;
	}

	/** For the test driver: logs what changed. */
	public static boolean unchanged(net.minecraft.client.Minecraft mc) {
		List<String> problems = changed();
		problems.forEach(problem -> org.slf4j.LoggerFactory.getLogger("AutoTest").error("[AutoTest] real-world state: {}", problem));
		return problems.isEmpty();
	}

	private static Object read(String[] field) {
		if (!FabricLoader.getInstance().isModLoaded(field[0])) {
			return null;
		}
		try {
			Field f = Class.forName(field[1]).getDeclaredField(field[2]);
			f.setAccessible(true);
			return f.get(null);
		} catch (ReflectiveOperationException e) {
			return e;
		}
	}
}
