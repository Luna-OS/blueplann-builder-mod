package dev.blueprintplanner.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

/** Global client settings, stored in {@code config/blueprintplanner.json}. */
public final class PlannerConfig {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static PlannerConfig instance = new PlannerConfig();

	// --- Planner ---
	/** Leave the planner as soon as the real player loses health. */
	public boolean ejectOnDamage = true;
	/** Minimum health loss (half hearts) that triggers the ejection. 0 = any damage. */
	public float ejectMinDamage = 0.0F;
	/** Only allow entering while the real player stands on the ground (prevents "flying" kicks on servers). */
	public boolean requireOnGround = true;
	/** Copy the real terrain around the player into the planner the first time an area is visited. */
	public boolean importSurroundings = true;
	public int importRadius = 24;
	public int importDepth = 8;
	public int importHeight = 24;
	/** Radius of the checkerboard build plate used when terrain import is off. */
	public int plateRadius = 32;
	/** Freeze the planner world while you are back in the real world. */
	public boolean freezeWhenAway = true;
	/** Permanent night vision inside the planner. */
	public boolean fullbright = true;
	/** Chat messages typed in the planner are sent to the real server (commands go to the planner). */
	public boolean chatToRealServer = true;
	/** Render the signal strength above redstone dust inside the planner. */
	public boolean showRedstonePower = false;
	/** Hide the planner from other mods, so they keep treating the real world as the only one (recommended). */
	public boolean isolateFromOtherMods = true;
	/** Look of the planner sky, see {@link dev.blueprintplanner.planner.PlannerSky}. */
	public String plannerSky = "BLUEPRINT";
	/** Chunk radius the planner server keeps loaded around you. 0 = like the client's render distance. */
	public int plannerViewDistance = 0;
	/** Chunk radius in which redstone, water etc. keep running. 0 = like the client's simulation distance. */
	public int plannerSimulationDistance = 0;

	// --- Blueprints ---
	public int ghostRenderDistance = 48;
	public int ghostMaxBlocks = 6000;
	public boolean ghostShowWrongBlocks = true;
	public boolean ghostShowCorrectBlocks = false;
	public boolean easyPlace = false;
	/** A stick in the main hand moves holograms (pick up / drop with right click, see PlacementTool). */
	public boolean placementStick = true;
	public int pasteCommandsPerTick = 10;

	public static PlannerConfig get() {
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("blueprintplanner.json");
	}

	public static void load() {
		Path file = file();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				PlannerConfig loaded = GSON.fromJson(reader, PlannerConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (Exception e) {
				LOGGER.warn("Could not read {}, using defaults", file, e);
			}
		}
		instance.clamp();
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(file().getParent());
			try (Writer writer = Files.newBufferedWriter(file())) {
				GSON.toJson(instance, writer);
			}
		} catch (Exception e) {
			LOGGER.warn("Could not save blueprint planner config", e);
		}
	}

	private void clamp() {
		this.importRadius = Math.clamp(this.importRadius, 4, 96);
		this.importDepth = Math.clamp(this.importDepth, 0, 64);
		this.importHeight = Math.clamp(this.importHeight, 4, 128);
		this.plateRadius = Math.clamp(this.plateRadius, 8, 128);
		this.ghostRenderDistance = Math.clamp(this.ghostRenderDistance, 8, 256);
		this.ghostMaxBlocks = Math.clamp(this.ghostMaxBlocks, 256, 100000);
		this.pasteCommandsPerTick = Math.clamp(this.pasteCommandsPerTick, 1, 200);
		this.ejectMinDamage = Math.max(0.0F, this.ejectMinDamage);
	}
}
