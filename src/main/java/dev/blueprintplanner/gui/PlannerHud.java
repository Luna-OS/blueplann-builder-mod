package dev.blueprintplanner.gui;

import dev.blueprintplanner.BlueprintPlanner;
import dev.blueprintplanner.blueprint.PlacementManager;
import dev.blueprintplanner.blueprint.PlacementTool;
import dev.blueprintplanner.blueprint.Selection;
import dev.blueprintplanner.planner.PlannerManager;
import dev.blueprintplanner.planner.PlannerServer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.TickRateManager;

/** Small status line in the top left corner while the planner is open or loading. */
public final class PlannerHud {
	private PlannerHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		int y = 4;
		if (PlannerManager.isLoading()) {
			drawLine(graphics, mc, Component.translatable("blueprintplanner.hud.loading"), y, 0xFFFFDD55);
			return;
		}
		if (!PlannerManager.isActive()) {
			if (PlacementManager.layerMode()) {
				String label = PlacementManager.layerLabel();
				drawLine(graphics, mc, Component.translatable("blueprintplanner.hud.layer", PlacementManager.layer(), label != null ? label : "-",
					BlueprintPlanner.layerDown.getTranslatedKeyMessage(), BlueprintPlanner.layerUp.getTranslatedKeyMessage(),
					BlueprintPlanner.layerToggle.getTranslatedKeyMessage()), y, 0xFFFFAA00);
				y += 11;
			}
			drawToolHelp(graphics, mc, y);
			return;
		}

		PlannerServer server = PlannerManager.server();
		Component state;
		if (mc.level != null) {
			TickRateManager ticks = mc.level.tickRateManager();
			String rate = ticks.tickrate() == Math.floor(ticks.tickrate()) ? String.valueOf((int) ticks.tickrate()) : String.format("%.1f", ticks.tickrate());
			if (ticks.isFrozen()) {
				state = Component.translatable("blueprintplanner.hud.frozen", rate);
			} else if (server != null && server.tickRateManager().isSprinting()) {
				state = Component.translatable("blueprintplanner.hud.sprinting");
			} else {
				state = Component.translatable("blueprintplanner.hud.running", rate);
			}
		} else {
			state = Component.empty();
		}
		drawLine(graphics, mc, Component.translatable("blueprintplanner.hud.title").append(" ").append(state), y, 0xFF55FFFF);
		y += 11;
		drawLine(graphics, mc, Component.translatable("blueprintplanner.hud.keys",
			BlueprintPlanner.togglePlanner.getTranslatedKeyMessage(), BlueprintPlanner.openSettings.getTranslatedKeyMessage(),
			BlueprintPlanner.openTools.getTranslatedKeyMessage()), y, 0xFFAAAAAA);
		y += 11;
		Selection selection = Selection.get();
		if (selection.isComplete()) {
			drawLine(graphics, mc, Component.translatable("blueprintplanner.hud.selection",
				selection.sizeX(), selection.sizeY(), selection.sizeZ(), selection.volume()), y, 0xFFFFAA00);
			y += 11;
		}
		drawToolHelp(graphics, mc, y);
	}

	/** What the mouse buttons do while a hologram is carried or the move stick is in hand. */
	private static void drawToolHelp(GuiGraphicsExtractor graphics, Minecraft mc, int y) {
		boolean first = true;
		for (Component line : PlacementTool.hudLines(mc)) {
			drawLine(graphics, mc, line, y, first ? 0xFFFFDD33 : 0xFFDDDDDD);
			first = false;
			y += 11;
		}
	}

	private static void drawLine(GuiGraphicsExtractor graphics, Minecraft mc, Component text, int y, int color) {
		int width = mc.font.width(text);
		graphics.fill(2, y - 2, 6 + width, y + 9, 0x90000000);
		graphics.text(mc.font, text, 4, y, color);
	}
}
