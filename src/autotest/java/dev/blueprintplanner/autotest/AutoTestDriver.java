package dev.blueprintplanner.autotest;

import com.mojang.logging.LogUtils;
import dev.blueprintplanner.BlueprintPlanner;
import dev.blueprintplanner.blueprint.BlueprintActions;
import dev.blueprintplanner.blueprint.BlueprintLibrary;
import dev.blueprintplanner.blueprint.EditTools;
import dev.blueprintplanner.blueprint.MaterialList;
import dev.blueprintplanner.blueprint.Paster;
import dev.blueprintplanner.blueprint.Placement;
import dev.blueprintplanner.blueprint.PlacementManager;
import dev.blueprintplanner.blueprint.PlacementTool;
import dev.blueprintplanner.blueprint.Selection;
import dev.blueprintplanner.blueprint.StateCompare;
import dev.blueprintplanner.config.PlannerConfig;
import com.mojang.blaze3d.platform.InputConstants;
import dev.blueprintplanner.gui.BlueprintScreen;
import dev.blueprintplanner.gui.KeyConflicts;
import dev.blueprintplanner.gui.PlannerSettingsScreen;
import dev.blueprintplanner.gui.ToolsScreen;
import dev.blueprintplanner.planner.PlannerManager;
import dev.blueprintplanner.planner.PlannerServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.lwjgl.sdl.SDLKeyboard;
import org.slf4j.Logger;
import probe.BobbyProbe;
import probe.CompatTargetsProbe;
import probe.FlashbackProbe;
import probe.ForeignModProbe;
import probe.RealStateProbe;

/**
 * End-to-end test that drives a real client against a real dedicated server (started by
 * {@code scripts/autotest.sh}). Only active with {@code -Dblueprintplanner.autotest=true}.
 */
public class AutoTestDriver implements ClientModInitializer {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String SERVER = "localhost:25599";

	private record Step(String name, Consumer<Minecraft> start, Predicate<Minecraft> done, int timeout) {
	}

	private final List<Step> steps = new ArrayList<>();
	private final List<String> results = new ArrayList<>();
	private int index = -1;
	private int ticksInStep;
	private boolean failed;
	private int screenshotCounter;
	private int pendingKeyRelease = -1;

	// shared test state
	private final AtomicReference<BlockPos> base = new AtomicReference<>();
	private BlockPos lamp;
	private BlockPos repeater;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("blueprintplanner.autotest")) {
			return;
		}
		PlannerConfig.get().importRadius = 12;
		ForeignModProbe.register();
		this.buildScenario();
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	// ------------------------------------------------------------------ scenario

	private void buildScenario() {
		this.waitUntil("game loaded", mc -> mc.isGameLoadFinished() && mc.gui.overlay() == null, 20 * 300);
		this.step("join server", mc -> connect(mc), mc -> mc.level != null && mc.player != null && mc.gui.screen() == null, 20 * 120);
		this.waitTicks(100);
		this.screenshot("real-world");
		if (FlashbackProbe.loaded()) {
			this.action("start Flashback recording", mc -> FlashbackProbe.start());
			this.waitUntil("Flashback is recording", mc -> Boolean.FALSE.equals(FlashbackProbe.paused()), 200);
		}

		// enter with the real key binding
		this.check("a mod's item component change is applied", mc -> ForeignModProbe.featherKeepsModdedStackSize());
		this.action("remember other mods' real-world state", mc -> RealStateProbe.snapshot());
		if (FabricLoader.getInstance().isModLoaded("xaerominimap")) {
			// Xaero's Minimap opens its waypoint screen on B, which swallows the planner key press
			this.check("key conflict with Xaero's Minimap is detected", mc -> KeyConflicts.find().stream()
				.anyMatch(conflict -> conflict.ours() == BlueprintPlanner.togglePlanner));
			this.action("move the planner key off the conflict", mc -> {
				BlueprintPlanner.togglePlanner.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F8));
				KeyMapping.resetMapping();
			});
		}
		this.step("press planner key", mc -> this.pressKey(mc, BlueprintPlanner.togglePlanner), mc -> PlannerManager.isActive(), 20 * 90);
		this.waitUntil("planner player is creative", mc -> mc.player != null && mc.player.getAbilities().instabuild, 400);
		this.check("real player kept in survival", mc -> PlannerManager.realPlayer() != null && PlannerManager.realPlayer() != mc.player
			&& !PlannerManager.realPlayer().getAbilities().instabuild);
		this.check("other mods do not see the planner (multiplayer)", mc -> isolationHolds(1, 0));
		if (FlashbackProbe.loaded()) {
			this.waitUntil("Flashback recording pauses in the planner", mc -> Boolean.TRUE.equals(FlashbackProbe.paused()), 100);
		}
		this.check("no Bobby cache for the planner", mc -> BobbyProbe.plannerHasNoBobbyCache());
		this.check("other mods keep their real-world state in the planner", RealStateProbe::unchanged);
		this.check("the planner's datapack load keeps the mod's item component change", mc -> ForeignModProbe.featherKeepsModdedStackSize());
		if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) {
			this.action("world map key in the planner", mc -> {
				KeyMapping openMap = mapping(mc, "gui.xaero_open_map");
				openMap.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F9));
				KeyMapping.resetMapping();
				this.pressKey(mc, openMap);
			});
			this.waitTicks(10);
			this.check("world map refused in the planner, no crash", mc -> PlannerManager.isActive()
				&& (mc.gui.screen() == null || !mc.gui.screen().getClass().getName().equals("xaero.map.gui.GuiMap")));
		}
		if (FabricLoader.getInstance().isModLoaded("xaerominimap")) {
			this.action("click a shared waypoint link in the planner", mc -> mc.player.connection.sendUnattendedCommand("xaero_waypoint_add:Test:T:1:2:3:0:false:0:Internal-overworld-waypoints", null));
			this.action("press Xaero's waypoint key in the planner", mc -> this.pressKey(mc, mapping(mc, "gui.xaero_new_waypoint")));
			this.waitTicks(10);
			this.action("op yourself in the planner", mc -> PlannerManager.server().execute(() -> PlannerManager.server().runCommand("op Tester")));
			this.waitTicks(60);
			this.check("planner still open after Xaero actions", mc -> PlannerManager.isActive() && mc.gui.screen() == null);
		}
		this.check("planner has its own sky, the real world keeps its sky", mc -> {
			Vector3fc planner = mc.level.environmentAttributes().getValue(EnvironmentAttributes.SKY_COLOR, mc.player.position(), null);
			Vector3fc real = PlannerManager.realLevel().environmentAttributes().getValue(EnvironmentAttributes.SKY_COLOR, mc.player.position(), null);
			return planner.equals(ARGB.vector3fFromRGB24(0x1E4E8C)) && !real.equals(planner);
		});
		this.action("set planner chunk radius", mc -> {
			PlannerConfig.get().plannerViewDistance = 5;
			PlannerConfig.get().plannerSimulationDistance = 3;
		});
		this.waitTicks(10);
		this.check("planner chunk radius applied", mc -> PlannerManager.server().getPlayerList().getViewDistance() == 5
			&& PlannerManager.server().getPlayerList().getSimulationDistance() == 3);
		this.action("reset planner chunk radius", mc -> {
			PlannerConfig.get().plannerViewDistance = 0;
			PlannerConfig.get().plannerSimulationDistance = 0;
		});
		this.waitTicks(60);
		this.screenshot("planner-entered");
		this.terrainScenario();

		// redstone with frozen ticks and stepping
		this.action("build redstone line", mc -> {
			BlockPos b = mc.player.blockPosition().offset(3, 0, 2);
			this.base.set(b);
			this.repeater = b.east();
			this.lamp = b.east(2);
			PlannerServer s = PlannerManager.server();
			s.setFrozen(true);
			s.execute(() -> {
				s.runCommand("fill %d %d %d %d %d %d minecraft:smooth_stone".formatted(b.getX(), b.getY() - 1, b.getZ(), b.getX() + 2, b.getY() - 1, b.getZ()));
				s.runCommand("setblock %d %d %d minecraft:repeater[facing=west,delay=4]".formatted(this.repeater.getX(), this.repeater.getY(), this.repeater.getZ()));
				s.runCommand("setblock %d %d %d minecraft:redstone_lamp".formatted(this.lamp.getX(), this.lamp.getY(), this.lamp.getZ()));
				s.runCommand("setblock %d %d %d minecraft:redstone_block".formatted(b.getX(), b.getY(), b.getZ()));
			});
		});
		this.waitUntil("lamp placed", mc -> mc.level.getBlockState(this.lamp).is(Blocks.REDSTONE_LAMP), 200);
		this.waitTicks(20);
		this.check("lamp stays off while frozen", mc -> !isLit(mc.level.getBlockState(this.lamp)));
		this.action("step 20 ticks", mc -> PlannerManager.server().step(20));
		this.waitUntil("lamp lit after stepping", mc -> isLit(mc.level.getBlockState(this.lamp)), 300);
		this.action("unfreeze + show power", mc -> {
			PlannerManager.server().setFrozen(false);
			PlannerConfig.get().showRedstonePower = true;
		});
		this.waitTicks(20);
		this.screenshot("planner-redstone");

		// WorldEdit-like tools
		this.action("select + fill", mc -> {
			BlockPos b = this.base.get().offset(0, 0, 3);
			Selection.get().setFirst(b);
			Selection.get().setSecond(b.offset(4, 2, 2));
			EditTools.fill(Blocks.GLASS.defaultBlockState());
		});
		this.waitUntil("fill visible", mc -> mc.level.getBlockState(this.base.get().offset(4, 2, 5)).is(Blocks.GLASS), 200);
		this.action("open tools screen", mc -> mc.gui.setScreen(new ToolsScreen()));
		this.waitTicks(10);
		this.screenshot("tools-screen");
		this.action("close + undo", mc -> {
			mc.gui.setScreen(null);
			EditTools.undo();
		});
		this.waitUntil("undo visible", mc -> mc.level.getBlockState(this.base.get().offset(4, 2, 5)).isAir(), 200);
		this.action("copy line + stack", mc -> {
			Selection.get().setFirst(this.base.get());
			Selection.get().setSecond(this.lamp);
			EditTools.stack(Direction.SOUTH, 2);
		});
		this.waitUntil("stack visible", mc -> mc.level.getBlockState(this.lamp.south(2)).is(Blocks.REDSTONE_LAMP), 200);
		this.action("undo stack", mc -> EditTools.undo());
		this.waitUntil("stack undone", mc -> mc.level.getBlockState(this.lamp.south(2)).isAir(), 200);
		this.action("open settings screen", mc -> mc.gui.setScreen(new PlannerSettingsScreen(null)));
		this.waitTicks(10);
		this.screenshot("settings-screen");
		this.action("show options page", mc -> ((PlannerSettingsScreen) mc.gui.screen()).switchPage(1));
		this.waitTicks(10);
		this.screenshot("settings-options");
		this.action("close settings", mc -> mc.gui.setScreen(null));

		// blueprint from the planner, shown as hologram in the real world
		this.action("save blueprint", mc -> {
			Selection.get().setFirst(this.base.get().below());
			Selection.get().setSecond(this.lamp);
			BlueprintActions.saveSelection("autotest_line", true, file -> {
			});
		});
		this.waitUntil("blueprint saved + placed", mc -> BlueprintLibrary.exists("autotest_line") && !PlacementManager.placements().isEmpty(), 200);
		this.step("leave planner with key", mc -> this.pressKey(mc, BlueprintPlanner.togglePlanner), mc -> !PlannerManager.isActive(), 200);
		if (FlashbackProbe.loaded()) {
			this.waitUntil("Flashback recording resumes in the real world", mc -> Boolean.FALSE.equals(FlashbackProbe.paused()), 100);
		}
		this.waitTicks(40);
		this.check("other mods keep their real-world state after the planner", RealStateProbe::unchanged);
		this.check("no key press of the planner time fires afterwards", mc -> mc.gui.screen() == null);
		this.check("hologram shows missing blocks", mc -> PlacementManager.statuses().stream().anyMatch(s -> s.result() == StateCompare.Result.MISSING));
		this.action("look at hologram", mc -> lookAt(mc, Vec3.atCenterOf(this.repeater)));
		this.waitTicks(20);
		this.screenshot("real-hologram");
		this.layerScenario();
		this.action("open blueprint screen", mc -> mc.gui.setScreen(new BlueprintScreen(null)));
		this.waitTicks(10);
		this.screenshot("blueprint-screen");
		this.action("show placements tab", mc -> ((BlueprintScreen) mc.gui.screen()).switchTab(1));
		this.waitTicks(10);
		this.screenshot("placements-screen");
		this.action("close blueprint screen", mc -> {
			((BlueprintScreen) mc.gui.screen()).switchTab(0);
			mc.gui.setScreen(null);
		});
		this.moveToolScenario();
		this.check("material list", mc -> {
			Placement p = PlacementManager.selected();
			List<MaterialList.Line> lines = MaterialList.compute(p, mc.level, mc.player.getInventory());
			LOGGER.info("[AutoTest] material list: {}", lines);
			return lines.stream().anyMatch(l -> l.item() == Blocks.REPEATER.asItem() && l.missing() == 1);
		});

		// easy place a repeater into the hologram (orientation must match the plan: facing=west)
		this.action("give items", mc -> {
			PlannerManager.realListener().sendCommand("give Tester minecraft:repeater 4");
			PlannerManager.realListener().sendCommand("give Tester minecraft:smooth_stone 16");
			PlannerManager.realListener().sendCommand("fill %d %d %d %d %d %d minecraft:smooth_stone".formatted(
				this.base.get().getX(), this.base.get().getY() - 1, this.base.get().getZ(), this.lamp.getX(), this.lamp.getY() - 1, this.lamp.getZ()));
		});
		this.waitUntil("items received", mc -> mc.player.getInventory().countItem(Blocks.REPEATER.asItem()) > 0, 200);
		// looking south a plain placement would face north: Easy Place has to turn the player itself
		this.action("look south", mc -> {
			mc.player.setYRot(0.0F);
			mc.player.setXRot(30.0F);
		});
		this.waitTicks(3);
		this.action("easy place repeater", mc -> {
			PlannerConfig.get().easyPlace = true;
			int slot = Math.max(0, findHotbar(mc, Blocks.REPEATER.asItem()));
			mc.player.getInventory().setSelectedSlot(slot);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(this.repeater.below()).add(0, 0.5, 0), Direction.UP, this.repeater.below(), false);
			LOGGER.info("[AutoTest] easy place: pos {} yaw {} pitch {} slot {} planned {} hand {}", mc.player.position(), mc.player.getYRot(), mc.player.getXRot(),
				slot, PlacementManager.plannedAt(this.repeater), mc.player.getMainHandItem());
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
		});
		this.waitTicks(20);
		this.action("log easy place result", mc -> LOGGER.info("[AutoTest] easy place result: {}", mc.level.getBlockState(this.repeater)));
		this.waitUntil("repeater placed facing west (server confirmed)", mc -> {
			BlockState state = mc.level.getBlockState(this.repeater);
			return state.is(Blocks.REPEATER) && state.getValue(RepeaterBlock.FACING) == Direction.WEST;
		}, 200);

		// damage ejects
		this.step("enter planner again", mc -> PlannerManager.toggle(), mc -> PlannerManager.isActive(), 400);
		this.waitTicks(20);
		this.step("take damage in real world", mc -> PlannerManager.realListener().sendCommand("damage Tester 2"), mc -> !PlannerManager.isActive(), 200);
		this.check("back in survival", mc -> !mc.player.getAbilities().instabuild);
		this.waitTicks(20);
		this.screenshot("ejected");

		// paste with commands (the test player is op)
		this.action("paste placement", mc -> Paster.paste(PlacementManager.selected()));
		this.waitUntil("pasted lamp in real world", mc -> !PlannerManager.isActive() && !Paster.isRunning()
			&& PlannerManager.realLevel().getBlockState(this.lamp).is(Blocks.REDSTONE_LAMP), 400);
		this.waitTicks(20);
		this.screenshot("real-pasted");

		// state is kept while away
		this.step("enter planner (kept state)", mc -> PlannerManager.toggle(), mc -> PlannerManager.isActive(), 400);
		this.waitUntil("lamp still lit", mc -> isLit(mc.level.getBlockState(this.lamp)), 200);
		// the planner server may still correct the player's position right after entering
		this.waitTicks(40);
		this.plannerAimPasteScenario();
		this.step("leave planner", mc -> PlannerManager.toggle(), mc -> !PlannerManager.isActive(), 200);

		// disconnect while inside the planner, then reconnect: planner world must persist
		this.step("enter planner before disconnect", mc -> PlannerManager.toggle(), mc -> PlannerManager.isActive(), 400);
		this.waitTicks(20);
		if (FlashbackProbe.loaded()) {
			this.action("stop Flashback recording", mc -> FlashbackProbe.cancel());
		}
		this.step("disconnect from server", mc -> mc.disconnectFromWorld(Component.literal("autotest")),
			mc -> mc.level == null && PlannerManager.phase() == PlannerManager.Phase.OFF, 400);
		this.waitTicks(40);
		this.step("reconnect", AutoTestDriver::connect, mc -> mc.level != null && mc.player != null && mc.gui.screen() == null, 20 * 60);
		this.waitTicks(60);
		this.step("enter planner after reconnect", mc -> this.pressKey(mc, BlueprintPlanner.togglePlanner), mc -> PlannerManager.isActive(), 20 * 90);
		this.waitUntil("planner world persisted", mc -> mc.level.getBlockState(this.lamp).is(Blocks.REDSTONE_LAMP)
			&& mc.level.getBlockState(this.repeater).is(Blocks.REPEATER), 400);
		this.waitTicks(40);
		this.screenshot("planner-persisted");
		this.step("leave planner at the end", mc -> PlannerManager.toggle(), mc -> !PlannerManager.isActive(), 200);

		// singleplayer: two servers in one client
		this.step("disconnect for singleplayer", mc -> mc.disconnectFromWorld(Component.literal("autotest")),
			mc -> mc.level == null && PlannerManager.phase() == PlannerManager.Phase.OFF, 400);
		this.step("open singleplayer world", mc -> mc.createWorldOpenFlows().openWorld("sptest", () -> mc.gui.setScreen(new TitleScreen())),
			mc -> mc.level != null && mc.player != null && mc.gui.screen() == null, 20 * 120);
		this.waitTicks(60);
		this.step("enter planner in singleplayer", mc -> this.pressKey(mc, BlueprintPlanner.togglePlanner), mc -> PlannerManager.isActive(), 20 * 90);
		this.waitTicks(60);
		this.screenshot("singleplayer-planner");
		this.check("other mods do not see the planner (singleplayer)", mc -> isolationHolds(3, 1));
		this.step("leave planner in singleplayer", mc -> PlannerManager.toggle(), mc -> !PlannerManager.isActive(), 200);
		this.waitTicks(20);
		this.screenshot("singleplayer-real");
		this.check("all compat hooks found their targets", mc -> {
			List<String> problems = CompatTargetsProbe.check();
			problems.forEach(problem -> LOGGER.error("[AutoTest] compat hook problem: {}", problem));
			return problems.isEmpty();
		});
	}

	/** The copied surroundings are whole chunks with the ground under you, and they grow while you walk. */
	private void terrainScenario() {
		AtomicReference<BlockPos> far = new AtomicReference<>();
		this.check("ground under the player was copied (whole columns, down to bedrock)", mc -> {
			BlockPos feet = mc.player.blockPosition();
			return mc.level.getBlockState(feet.below()).is(Blocks.GRASS_BLOCK) && mc.level.getBlockState(new BlockPos(feet.getX(), mc.level.getMinY(), feet.getZ())).is(Blocks.BEDROCK);
		});
		this.check("the copied chunk has no half-chunk edge", mc -> {
			BlockPos feet = mc.player.blockPosition();
			int cx = feet.getX() >> 4;
			int cz = feet.getZ() >> 4;
			return mc.level.getBlockState(new BlockPos((cx << 4) + 15, feet.getY() - 1, (cz << 4) + 15)).is(Blocks.GRASS_BLOCK)
				&& mc.level.getBlockState(new BlockPos(cx << 4, feet.getY() - 1, cz << 4)).is(Blocks.GRASS_BLOCK);
		});
		this.action("walk far away in the planner", mc -> {
			BlockPos target = mc.player.blockPosition().offset(PlannerConfig.get().importRadius + 40, 0, 0);
			far.set(target);
			PlannerManager.server().execute(() -> PlannerManager.server().runCommand("tp Tester %d %d %d".formatted(target.getX(), target.getY(), target.getZ())));
		});
		this.waitUntil("terrain copied where you walked", mc -> mc.player.blockPosition().distSqr(far.get()) < 9
			&& mc.level.getBlockState(far.get().below()).is(Blocks.GRASS_BLOCK), 200);
		this.action("look at the copied ground", mc -> mc.player.setXRot(55.0F));
		this.waitTicks(5);
		this.screenshot("planner-terrain-walked");
		this.action("walk back", mc -> {
			BlockPos back = far.get().offset(-PlannerConfig.get().importRadius - 40, 0, 0);
			PlannerManager.server().execute(() -> PlannerManager.server().runCommand("tp Tester %d %d %d".formatted(back.getX(), back.getY(), back.getZ())));
		});
		this.waitUntil("back at the start", mc -> mc.player.blockPosition().distSqr(far.get()) > 400, 200);
		this.waitTicks(10);
	}

	/** Layer mode: only one height of the hologram is shown, listed and built. */
	private void layerScenario() {
		this.action("layer mode on (key)", mc -> this.pressKey(mc, BlueprintPlanner.layerToggle));
		this.waitUntil("layer mode active", mc -> PlacementManager.layerMode(), 20);
		this.action("show the bottom layer", mc -> PlacementManager.setLayerMode(true, this.base.get().getY() - 1));
		this.waitUntil("only the bottom layer is shown", mc -> !PlacementManager.statuses().isEmpty()
			&& PlacementManager.statuses().stream().allMatch(s -> s.pos().getY() == this.base.get().getY() - 1), 40);
		this.check("material list of the layer has only the floor", mc -> {
			List<MaterialList.Line> lines = MaterialList.compute(PlacementManager.selected(), mc.level, mc.player.getInventory(), PlacementManager.layer());
			return lines.size() == 1 && lines.get(0).item() == Blocks.SMOOTH_STONE.asItem();
		});
		this.screenshot("layer-mode");
		this.action("layer up (key)", mc -> this.pressKey(mc, BlueprintPlanner.layerUp));
		this.waitUntil("upper layer shown", mc -> PlacementManager.layer() == this.base.get().getY()
			&& !PlacementManager.statuses().isEmpty() && PlacementManager.statuses().stream().allMatch(s -> s.pos().getY() == this.base.get().getY()), 40);
		this.action("layer mode off (key)", mc -> this.pressKey(mc, BlueprintPlanner.layerToggle));
		this.waitUntil("whole hologram shown again", mc -> !PlacementManager.layerMode()
			&& PlacementManager.statuses().stream().anyMatch(s -> s.pos().getY() == this.base.get().getY() - 1)
			&& PlacementManager.statuses().stream().anyMatch(s -> s.pos().getY() == this.base.get().getY()), 40);
	}

	/** The stick moves holograms; library blueprints are placed by aiming or into the selection. */
	private void moveToolScenario() {
		AtomicReference<BlockPos> original = new AtomicReference<>();
		AtomicReference<BlockPos> expected = new AtomicReference<>();
		AtomicReference<Integer> slot = new AtomicReference<>();
		this.action("give stick", mc -> {
			original.set(PlacementManager.selected().origin());
			PlannerManager.realListener().sendCommand("give Tester minecraft:stick");
		});
		this.waitUntil("stick received", mc -> findHotbar(mc, Items.STICK) >= 0, 200);
		this.action("hold stick, look at hologram", mc -> {
			mc.player.getInventory().setSelectedSlot(findHotbar(mc, Items.STICK));
			lookAt(mc, Vec3.atCenterOf(this.repeater));
		});
		this.waitTicks(5);
		this.check("stick highlights the hologram under the crosshair", mc -> PlacementTool.hovered() == PlacementManager.selected()
			&& !PlacementTool.hudLines(mc).isEmpty());
		this.screenshot("stick-hover");
		this.action("right click: pick up", mc -> click(mc.options.keyUse));
		this.waitUntil("hologram follows the crosshair", mc -> PlacementTool.carried() == PlacementManager.selected(), 20);
		this.action("aim at the ground", mc -> {
			BlockPos ground = groundAt(mc, 4, 8);
			expected.set(ground.above().offset(-1, 0, 0)); // 3 wide, 1 deep: centered on the crosshair
			lookAt(mc, Vec3.atCenterOf(ground).add(0, 0.48, 0));
		});
		this.waitUntil("hologram moved to the aimed block", mc -> PlacementTool.carried().origin().equals(expected.get()), 40);
		this.check("a carried hologram is saved at its old spot", mc -> PlacementTool.forSaving(PlacementManager.placements()).get(0).origin().equals(original.get()));
		this.screenshot("stick-carry");
		this.action("sneak", mc -> {
			slot.set(mc.player.getInventory().getSelectedSlot());
			mc.options.keyShift.setDown(true);
		});
		this.waitUntil("sneaking", mc -> mc.player.isShiftKeyDown(), 40);
		this.action("aim again + mouse wheel up", mc -> {
			lookAt(mc, Vec3.atCenterOf(expected.get().below().offset(1, 0, 0)).add(0, 0.48, 0));
			mc.mouseHandler.onScroll(mc.getWindow().handle(), 0, 1);
		});
		this.waitTicks(3);
		this.check("sneak + wheel lifts the hologram, hotbar slot unchanged", mc -> PlacementTool.carried().origin().getY() == expected.get().getY() + 1
			&& mc.player.getInventory().getSelectedSlot() == slot.get());
		this.action("stop sneaking", mc -> mc.options.keyShift.setDown(false));
		this.action("left click: cancel", mc -> click(mc.options.keyAttack));
		this.waitUntil("hologram is back", mc -> PlacementTool.carried() == null && PlacementManager.selected().origin().equals(original.get()), 20);
		this.action("pick up again, aim, drop", mc -> {
			click(mc.options.keyUse);
			BlockPos ground = groundAt(mc, -3, 6);
			expected.set(ground.above().offset(-1, 0, 0));
			lookAt(mc, Vec3.atCenterOf(ground).add(0, 0.48, 0));
		});
		this.waitUntil("carried to the new spot", mc -> PlacementTool.carried() != null && PlacementTool.carried().origin().equals(expected.get()), 40);
		this.action("right click: drop", mc -> click(mc.options.keyUse));
		this.waitUntil("dropped there", mc -> PlacementTool.carried() == null && PlacementManager.selected().origin().equals(expected.get()), 20);
		this.action("sneak (not carrying)", mc -> mc.options.keyShift.setDown(true));
		this.waitUntil("sneaking again", mc -> mc.player.isShiftKeyDown(), 40);
		this.action("mouse wheel up: push", mc -> mc.mouseHandler.onScroll(mc.getWindow().handle(), 0, 1));
		this.check("pushed one block along the view", mc -> {
			Direction direction = Direction.getApproximateNearest(mc.player.getViewVector(1.0F));
			return PlacementManager.selected().origin().equals(expected.get().relative(direction));
		});
		this.action("stop sneaking + put the hologram back", mc -> {
			mc.options.keyShift.setDown(false);
			PlacementManager.selected().setOrigin(original.get());
			PlacementManager.changed();
		});

		// library: "Place" makes the new hologram follow the crosshair, left click throws it away again
		this.action("open library", mc -> mc.gui.setScreen(new BlueprintScreen(null)));
		this.waitTicks(5);
		this.action("library: place by aiming (like the button)", mc -> {
			mc.gui.setScreen(null);
			PlacementTool.startPlacing(lineEntry());
		});
		this.waitUntil("new hologram follows the crosshair", mc -> PlacementManager.placements().size() == 2 && PlacementTool.carried() == PlacementManager.selected(), 20);
		this.waitTicks(3);
		this.action("left click: cancel placing", mc -> click(mc.options.keyAttack));
		this.waitUntil("new hologram removed", mc -> PlacementTool.carried() == null && PlacementManager.placements().size() == 1
			&& PlacementManager.selected().origin().equals(original.get()), 20);
		this.action("library: place, then middle click", mc -> {
			PlacementTool.startPlacing(lineEntry());
			click(mc.options.keyPickItem);
		});
		this.waitUntil("dropped at the spot it was saved from", mc -> PlacementTool.carried() == null && PlacementManager.placements().size() == 2
			&& PlacementManager.selected().origin().equals(original.get()), 20);
		this.action("remove the copy", mc -> {
			PlacementManager.remove(PlacementManager.placements().get(1));
			PlacementManager.select(0);
		});

		// library: "Into area" puts it on the floor of the selection, centered, turned when only that fits
		this.action("library: into selection", mc -> {
			BlockPos corner = mc.player.blockPosition().offset(-8, 0, -8);
			expected.set(corner.offset(1, 0, 2));
			Selection.get().setFirst(corner);
			Selection.get().setSecond(corner.offset(4, 2, 4));
			BlueprintActions.placeIntoSelection(lineEntry());
		});
		this.check("placed centered on the selection floor", mc -> PlacementManager.placements().size() == 2
			&& PlacementManager.selected().origin().equals(expected.get()) && PlacementManager.selected().rotation == Rotation.NONE);
		this.action("library: into a selection it only fits turned", mc -> {
			BlockPos corner = mc.player.blockPosition().offset(-8, 0, 8);
			expected.set(corner);
			Selection.get().setFirst(corner);
			Selection.get().setSecond(corner.offset(0, 1, 2));
			BlueprintActions.placeIntoSelection(lineEntry());
		});
		this.check("turned to fit the selection", mc -> PlacementManager.placements().size() == 3
			&& PlacementManager.selected().origin().equals(expected.get()) && PlacementManager.selected().rotation == Rotation.CLOCKWISE_90
			&& PlacementManager.selected().box().getXSpan() == 1 && PlacementManager.selected().box().getZSpan() == 3);
		this.waitTicks(5);
		this.screenshot("into-selection");
		this.action("remove test holograms", mc -> {
			PlacementManager.remove(PlacementManager.placements().get(2));
			PlacementManager.remove(PlacementManager.placements().get(1));
			PlacementManager.select(0);
			Selection.get().clear();
			mc.player.getInventory().setSelectedSlot(0);
		});
		this.check("original hologram untouched", mc -> PlacementManager.placements().size() == 1 && PlacementManager.selected().origin().equals(original.get()));
	}

	/** Inside the planner "→ Planner" pastes the blueprint where you click. */
	private void plannerAimPasteScenario() {
		AtomicReference<BlockPos> expected = new AtomicReference<>();
		AtomicReference<Vec3> aim = new AtomicReference<>();
		this.action("planner: paste by aiming", mc -> {
			BlockPos ground = groundAt(mc, -4, 4);
			expected.set(ground.above().offset(-1, 0, 0));
			aim.set(Vec3.atCenterOf(ground).add(0, 0.48, 0));
			lookAt(mc, aim.get());
			PlacementTool.startPasting(lineEntry());
			LOGGER.info("[AutoTest] aim paste: player {} ground {} aim {}", mc.player.position(), ground, PlacementTool.aim(mc, ClipContext.Fluid.SOURCE_ONLY));
		});
		// aim again every tick: a late position sync from the planner server can turn the player
		this.waitUntil("paste preview follows the crosshair", mc -> {
			lookAt(mc, aim.get());
			return PlacementTool.carryingIntoPlanner() && PlacementTool.carried().origin().equals(expected.get());
		}, 40);
		this.screenshot("planner-aim-paste");
		this.action("right click: paste", mc -> {
			lookAt(mc, aim.get());
			click(mc.options.keyUse);
		});
		this.waitUntil("pasted where aimed", mc -> PlacementTool.carried() == null
			&& mc.level.getBlockState(expected.get()).is(Blocks.SMOOTH_STONE) && mc.level.getBlockState(expected.get().above()).is(Blocks.REDSTONE_BLOCK), 200);
		this.action("undo aimed paste", mc -> EditTools.undo());
		this.waitUntil("aimed paste undone", mc -> mc.level.getBlockState(expected.get().above()).isAir(), 200);
	}

	/** The top block of the column at this offset from the player (the player may stand higher or fly). */
	private static BlockPos groundAt(Minecraft mc, int dx, int dz) {
		BlockPos column = mc.player.blockPosition().offset(dx, 0, dz);
		return new BlockPos(column.getX(), mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()) - 1, column.getZ());
	}

	private static BlueprintLibrary.Entry lineEntry() {
		return BlueprintLibrary.list().stream().filter(e -> e.displayName().equals("autotest_line")).findFirst().orElseThrow();
	}

	/** A mouse click as the game sees it (the key mapping's click counter). */
	private static void click(KeyMapping mapping) {
		KeyMapping.click(KeyMappingHelper.getBoundKeyOf(mapping));
	}

	/** The probe saw the real joins/server starts, but nothing of the planner. */
	private static boolean isolationHolds(int realJoins, int realServerStarts) {
		LOGGER.info("[AutoTest] foreign mod probe: {}", ForeignModProbe.describe());
		return ForeignModProbe.PLANNER_SERVER_EVENTS.get() == 0 && ForeignModProbe.PLANNER_JOINS.get() == 0
			&& ForeignModProbe.PLANNER_DISCONNECTS.get() == 0
			&& ForeignModProbe.REAL_JOINS.get() == realJoins && ForeignModProbe.REAL_SERVER_STARTS.get() == realServerStarts
			// only the real singleplayer world builds a command tree for other mods
			&& ForeignModProbe.COMMAND_REGISTRATIONS.get() == realServerStarts;
	}

	// ------------------------------------------------------------------ DSL

	private void step(String name, Consumer<Minecraft> start, Predicate<Minecraft> done, int timeout) {
		this.steps.add(new Step(name, start, done, timeout));
	}

	private void action(String name, Consumer<Minecraft> action) {
		this.steps.add(new Step(name, action, mc -> true, 1));
	}

	private void waitUntil(String name, Predicate<Minecraft> done, int timeout) {
		this.steps.add(new Step(name, mc -> {
		}, done, timeout));
	}

	private void waitTicks(int ticks) {
		int[] counter = {0};
		this.steps.add(new Step("wait " + ticks, mc -> counter[0] = 0, mc -> ++counter[0] >= ticks, ticks + 5));
	}

	private void check(String name, Predicate<Minecraft> condition) {
		this.steps.add(new Step("check: " + name, mc -> {
		}, condition, 1));
	}

	private void screenshot(String name) {
		this.steps.add(new Step("screenshot " + name, mc -> {
			String file = String.format("%02d_%s.png", ++this.screenshotCounter, name);
			Screenshot.grab(mc.gameDirectory, file, mc.gameRenderer.mainRenderTarget(), 1, message -> {
			});
		}, mc -> true, 1));
	}

	private static KeyMapping mapping(Minecraft mc, String name) {
		for (KeyMapping mapping : mc.options.keyMappings) {
			if (mapping.getName().equals(name)) {
				return mapping;
			}
		}
		throw new IllegalStateException("no key mapping " + name);
	}

	private void pressKey(Minecraft mc, KeyMapping mapping) {
		int scancode = KeyMappingHelper.getBoundKeyOf(mapping).getValue();
		mc.keyboardHandler.keyPress(mc.getWindow().handle(), 1, new KeyEvent(scancode, SDLKeyboard.SDL_GetKeyFromScancode(scancode, (short) 0, false), 0));
		this.pendingKeyRelease = scancode;
	}

	// ------------------------------------------------------------------ runner

	private void tick(Minecraft mc) {
		if (this.pendingKeyRelease >= 0) {
			int scancode = this.pendingKeyRelease;
			this.pendingKeyRelease = -1;
			mc.keyboardHandler.keyPress(mc.getWindow().handle(), 0, new KeyEvent(scancode, SDLKeyboard.SDL_GetKeyFromScancode(scancode, (short) 0, false), 0));
		}
		if (this.index >= this.steps.size()) {
			return;
		}
		try {
			if (this.index < 0 || this.stepFinished(mc)) {
				this.index++;
				this.ticksInStep = 0;
				if (this.index >= this.steps.size()) {
					this.finish(mc);
					return;
				}
				Step step = this.steps.get(this.index);
				LOGGER.info("[AutoTest] >> {}", step.name());
				step.start().accept(mc);
			}
		} catch (Throwable t) {
			this.fail(mc, this.currentName() + " threw " + t);
			LOGGER.error("[AutoTest] exception", t);
		}
	}

	private boolean stepFinished(Minecraft mc) {
		Step step = this.steps.get(this.index);
		this.ticksInStep++;
		boolean done;
		try {
			done = step.done().test(mc);
		} catch (RuntimeException e) {
			// a broken check must not be evaluated again every tick
			LOGGER.error("[AutoTest] exception", e);
			this.fail(mc, step.name() + " threw " + e);
			return true;
		}
		if (done) {
			this.results.add("PASS " + step.name() + " (" + this.ticksInStep + " ticks)");
			return true;
		}
		if (this.ticksInStep >= step.timeout()) {
			this.fail(mc, step.name() + " (timeout after " + step.timeout() + " ticks)");
			return true;
		}
		return false;
	}

	private String currentName() {
		return this.index >= 0 && this.index < this.steps.size() ? this.steps.get(this.index).name() : "?";
	}

	private void fail(Minecraft mc, String message) {
		this.failed = true;
		this.results.add("FAIL " + message);
		LOGGER.error("[AutoTest] FAIL {}", message);
		String file = String.format("%02d_FAIL.png", ++this.screenshotCounter);
		Screenshot.grab(mc.gameDirectory, file, mc.gameRenderer.mainRenderTarget(), 1, m -> {
		});
	}

	private void finish(Minecraft mc) {
		String summary = (this.failed ? "AUTOTEST FAILED" : "AUTOTEST PASSED") + "\n" + String.join("\n", this.results) + "\n";
		LOGGER.info("[AutoTest] {}", summary);
		try {
			Files.writeString(Path.of(mc.gameDirectory.getPath(), "autotest-result.txt"), summary);
		} catch (IOException e) {
			LOGGER.error("Could not write result", e);
		}
		mc.stop();
	}

	// ------------------------------------------------------------------ helpers

	private static void connect(Minecraft mc) {
		ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(SERVER), new ServerData("autotest", SERVER, ServerData.Type.OTHER), false, null);
	}

	private static boolean isLit(BlockState state) {
		return state.is(Blocks.REDSTONE_LAMP) && state.getValue(RedstoneLampBlock.LIT);
	}

	private static int findHotbar(Minecraft mc, net.minecraft.world.item.Item item) {
		for (int i = 0; i < 9; i++) {
			if (mc.player.getInventory().getItem(i).is(item)) {
				return i;
			}
		}
		return -1;
	}

	private static void lookAt(Minecraft mc, Vec3 target) {
		Vec3 eye = mc.player.getEyePosition();
		Vec3 d = target.subtract(eye);
		double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
		mc.player.setYRot((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0));
		mc.player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, horizontal)));
	}
}
