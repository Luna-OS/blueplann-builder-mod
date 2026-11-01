package dev.blueprintplanner.compat;

import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Key presses of mods that are switched off inside the planner are thrown away there; otherwise they pile up and all
 * fire after leaving (Xaero's minimap: "add waypoint" screens, temporary waypoints at the real position).
 */
public final class CompatKeys {
	/** Mod ids (and key category namespaces) of the minimap editions whose keys are dropped in the planner. */
	private static final Set<String> XAERO_MINIMAPS = Set.of("xaerominimap", "xaerominimapfair", "xaerobetterpvp");
	private static final boolean XAERO_MINIMAP = XAERO_MINIMAPS.stream().anyMatch(FabricLoader.getInstance()::isModLoaded);

	private CompatKeys() {
	}

	/** End of a client tick that the planner was shown in. */
	public static void dropPlannerPresses(Minecraft mc) {
		if (!XAERO_MINIMAP) {
			return;
		}
		for (KeyMapping mapping : mc.options.keyMappings) {
			if (XAERO_MINIMAPS.contains(mapping.getCategory().id().getNamespace())) {
				while (mapping.consumeClick()) {
					// drop it
				}
			}
		}
	}
}
