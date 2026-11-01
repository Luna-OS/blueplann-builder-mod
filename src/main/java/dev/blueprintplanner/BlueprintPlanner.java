package dev.blueprintplanner;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blueprintplanner.blueprint.EasyPlace;
import dev.blueprintplanner.blueprint.Paster;
import dev.blueprintplanner.blueprint.PlacementManager;
import dev.blueprintplanner.blueprint.PlacementTool;
import dev.blueprintplanner.blueprint.SelectionTool;
import dev.blueprintplanner.compat.CompatKeys;
import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.config.PlannerConfig;
import dev.blueprintplanner.gui.BlueprintScreen;
import dev.blueprintplanner.gui.KeyConflicts;
import dev.blueprintplanner.gui.PlannerHud;
import dev.blueprintplanner.gui.PlannerSettingsScreen;
import dev.blueprintplanner.gui.ToolsScreen;
import dev.blueprintplanner.planner.PlannerManager;
import dev.blueprintplanner.planner.PlannerServer;
import dev.blueprintplanner.render.OverlayRenderer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class BlueprintPlanner implements ClientModInitializer {
	public static final String MOD_ID = "blueprintplanner";
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	/** All key mappings of this mod. */
	public static final List<KeyMapping> KEYS = new ArrayList<>();
	public static KeyMapping togglePlanner;
	public static KeyMapping openSettings;
	public static KeyMapping openTools;
	public static KeyMapping openLibrary;
	public static KeyMapping cornerOne;
	public static KeyMapping cornerTwo;
	public static KeyMapping rotatePlacement;
	public static KeyMapping toggleEasyPlace;
	public static KeyMapping layerUp;
	public static KeyMapping layerDown;
	public static KeyMapping layerToggle;

	@Override
	public void onInitializeClient() {
		PlannerConfig.load();

		togglePlanner = key("toggle", InputConstants.KEY_B);
		openSettings = key("settings", InputConstants.KEY_N);
		openTools = key("tools", InputConstants.KEY_M);
		openLibrary = key("library", InputConstants.KEY_U);
		cornerOne = key("corner1", InputConstants.KEY_LBRACKET);
		cornerTwo = key("corner2", InputConstants.KEY_RBRACKET);
		rotatePlacement = key("rotate", InputConstants.KEY_K);
		toggleEasyPlace = key("easyplace", InputConstants.KEY_J);
		layerUp = key("layer_up", InputConstants.KEY_PAGEUP);
		layerDown = key("layer_down", InputConstants.KEY_PAGEDOWN);
		layerToggle = key("layer_toggle", InputConstants.KEY_HOME);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (server instanceof PlannerServer plannerServer) {
				plannerServer.onPlayerJoin(handler.player);
			}
		});

		// first, so a carried hologram takes the mouse buttons before the other tools see them
		PlacementTool.register();
		SelectionTool.register();
		EasyPlace.register();

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "status"), PlannerHud::extract);
		LevelExtractionEvents.END_EXTRACTION.register(OverlayRenderer::extractGhosts);
		LevelRenderEvents.COLLECT_SUBMITS.register(OverlayRenderer::submitGhosts);

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof PauseScreen && PlannerManager.isActive()) {
				Screens.getWidgets(screen).add(Button.builder(Component.translatable("blueprintplanner.pause.leave"), b -> {
					client.gui.setScreen(null);
					PlannerManager.exit(Component.translatable("blueprintplanner.exit.manual"));
				}).bounds(4, 4, 130, 20).build());
				Screens.getWidgets(screen).add(Button.builder(Component.translatable("blueprintplanner.pause.settings"),
					b -> client.gui.setScreen(new PlannerSettingsScreen(screen))).bounds(4, 28, 130, 20).build());
			}
		});

		ClientTickEvents.START_CLIENT_TICK.register(PlacementTool::onStartTick);
		ClientTickEvents.END_CLIENT_TICK.register(BlueprintPlanner::onEndTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			PlacementTool.cancel(false);
			PlacementManager.save();
			PlannerManager.shutdown(true);
		});
	}

	private static KeyMapping key(String name, int keyCode) {
		KeyMapping mapping = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.blueprintplanner." + name, keyCode, CATEGORY));
		KEYS.add(mapping);
		return mapping;
	}

	private static void onEndTick(Minecraft mc) {
		boolean inPlanner = PlannerManager.isActive();
		PlannerManager.onClientTick(mc);
		PlannerIsolation.refreshRealWorld();
		PlacementManager.tick(mc);
		Paster.tick();
		KeyConflicts.tick(mc);

		while (togglePlanner.consumeClick()) {
			PlannerManager.toggle();
		}
		while (openSettings.consumeClick()) {
			mc.gui.setScreen(new PlannerSettingsScreen(null));
		}
		while (openTools.consumeClick()) {
			mc.gui.setScreen(new ToolsScreen());
		}
		while (openLibrary.consumeClick()) {
			mc.gui.setScreen(new BlueprintScreen(null));
		}
		while (cornerOne.consumeClick()) {
			SelectionTool.setCornerFromCrosshair(true);
		}
		while (cornerTwo.consumeClick()) {
			SelectionTool.setCornerFromCrosshair(false);
		}
		while (rotatePlacement.consumeClick()) {
			PlacementTool.rotate();
		}
		while (layerToggle.consumeClick()) {
			PlacementManager.setLayerMode(!PlacementManager.layerMode(), PlacementManager.defaultLayer(mc));
			PlannerManager.message(Component.translatable(PlacementManager.layerMode() ? "blueprintplanner.layer.on" : "blueprintplanner.layer.off", PlacementManager.layer()), true);
		}
		while (layerUp.consumeClick()) {
			PlacementManager.moveLayer(mc, 1);
		}
		while (layerDown.consumeClick()) {
			PlacementManager.moveLayer(mc, -1);
		}
		while (toggleEasyPlace.consumeClick()) {
			PlannerConfig config = PlannerConfig.get();
			config.easyPlace = !config.easyPlace;
			PlannerConfig.save();
			PlannerManager.message(Component.translatable(config.easyPlace ? "blueprintplanner.easyplace.on" : "blueprintplanner.easyplace.off"), true);
		}
		if (inPlanner) {
			// also on the tick the planner was left (with a key that another mod shares)
			CompatKeys.dropPlannerPresses(mc);
		}
	}
}
