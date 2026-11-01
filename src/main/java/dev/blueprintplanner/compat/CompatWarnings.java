package dev.blueprintplanner.compat;

import dev.blueprintplanner.planner.PlannerManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Hints about mods that cannot work together with the planner, shown once per game session. */
public final class CompatWarnings {
	private static boolean litematicaWarned;

	private CompatWarnings() {
	}

	public static void onEnter() {
		if (!litematicaWarned && FabricLoader.getInstance().isModLoaded("litematica")) {
			litematicaWarned = true;
			// MaLiLib treats the planner login as a world change and Litematica then overwrites the real world's placements
			PlannerManager.message(Component.translatable("blueprintplanner.compat.litematica").withStyle(ChatFormatting.GOLD), false);
		}
	}
}
