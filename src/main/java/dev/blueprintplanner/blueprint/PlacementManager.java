package dev.blueprintplanner.blueprint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import dev.blueprintplanner.config.PlannerConfig;
import dev.blueprintplanner.planner.PlannerManager;
import dev.blueprintplanner.planner.PlannerWorlds;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Blueprint holograms in the real world, saved per server/world and dimension in
 * {@code .minecraft/blueprintplanner/placements/<world>.json}.
 */
public final class PlacementManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static @Nullable String worldKey;
	private static List<Placement> placements = new ArrayList<>();
	private static int selected = -1;
	private static List<Status> statuses = List.of();
	private static int refreshTimer;
	/** Layer mode: only this height of the holograms is shown, so a build can be done floor by floor. */
	private static boolean layerMode;
	private static int layer;

	/** Verification result of one planned block near the player. */
	public record Status(BlockPos pos, BlockState planned, BlockState actual, StateCompare.Result result) {
	}

	private PlacementManager() {
	}

	/** Key of the real world the placements belong to (also used to tag new blueprints). */
	public static @Nullable String worldKey() {
		return worldKey;
	}

	public static List<Placement> placements() {
		return placements;
	}

	public static @Nullable Placement selected() {
		return selected >= 0 && selected < placements.size() ? placements.get(selected) : null;
	}

	public static void select(int index) {
		selected = index;
		refreshTimer = 0;
	}

	public static int selectedIndex() {
		return selected;
	}

	public static List<Status> statuses() {
		return statuses;
	}

	// ------------------------------------------------------------------ layer mode

	public static boolean layerMode() {
		return layerMode;
	}

	/** The shown height while in layer mode. */
	public static int layer() {
		return layer;
	}

	/** The height to show when nothing else is wanted: the feet of the player, kept inside the selected hologram. */
	public static int defaultLayer(Minecraft mc) {
		int y = mc.player != null ? mc.player.blockPosition().getY() : 0;
		return clampToSelected(y);
	}

	private static int clampToSelected(int y) {
		Placement selected = selected();
		var box = selected != null ? selected.box() : null;
		return box != null ? Math.clamp(y, box.minY(), box.maxY()) : y;
	}

	public static void setLayerMode(boolean on, int y) {
		layerMode = on;
		layer = clampToSelected(y);
		refreshTimer = 0;
	}

	/** Moves the shown layer; switches layer mode on first if it was off. */
	public static void moveLayer(Minecraft mc, int dy) {
		if (!layerMode) {
			setLayerMode(true, defaultLayer(mc));
			return;
		}
		setLayerMode(true, layer + dy);
	}

	/** The layer's position inside the selected hologram, e.g. "3/12"; null without a selected hologram. */
	public static @Nullable String layerLabel() {
		Placement selected = selected();
		var box = selected != null ? selected.box() : null;
		if (box == null) {
			return null;
		}
		return (layer - box.minY() + 1) + "/" + box.getYSpan();
	}

	public static void add(Placement placement) {
		placements.add(placement);
		selected = placements.size() - 1;
		changed();
	}

	public static void remove(Placement placement) {
		placements.remove(placement);
		if (selected >= placements.size()) {
			selected = placements.size() - 1;
		}
		changed();
	}

	public static void changed() {
		refreshTimer = 0;
		save();
	}

	/** A placement is being dragged around: refresh its ghost blocks soon, save when it is dropped. */
	public static void moved() {
		refreshTimer = Math.min(refreshTimer, 1);
	}

	// ------------------------------------------------------------------ ticking

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			if (worldKey != null) {
				save();
				worldKey = null;
				placements = new ArrayList<>();
				selected = -1;
				statuses = List.of();
			}
			return;
		}
		if (!PlannerManager.isActive()) {
			String key = PlannerWorlds.keyFor(mc);
			if (!key.equals(worldKey)) {
				save();
				worldKey = key;
				load();
			}
		}
		if (--refreshTimer <= 0) {
			refreshTimer = 10;
			refreshStatuses(mc);
		}
	}

	private static void refreshStatuses(Minecraft mc) {
		ClientLevel level = PlannerManager.realLevel();
		if (level == null || mc.player == null || PlannerManager.isActive()) {
			statuses = List.of();
			return;
		}
		PlannerConfig config = PlannerConfig.get();
		double maxDistSq = (double) config.ghostRenderDistance * config.ghostRenderDistance;
		BlockPos center = mc.player.blockPosition();
		List<Status> result = new ArrayList<>();
		for (Placement placement : placements) {
			if (!placement.visible || !placement.isLoaded()) {
				continue;
			}
			for (Placement.Target target : placement.targets()) {
				if (layerMode && target.pos().getY() != layer) {
					continue;
				}
				if (target.pos().distSqr(center) > maxDistSq || !level.isLoaded(target.pos())) {
					continue;
				}
				BlockState actual = level.getBlockState(target.pos());
				StateCompare.Result compare = StateCompare.compare(target.state(), actual);
				if (compare == StateCompare.Result.CORRECT && !config.ghostShowCorrectBlocks) {
					continue;
				}
				result.add(new Status(target.pos(), target.state(), actual, compare));
			}
		}
		result.sort(Comparator.comparingDouble(s -> s.pos().distSqr(center)));
		if (result.size() > config.ghostMaxBlocks) {
			result = new ArrayList<>(result.subList(0, config.ghostMaxBlocks));
		}
		statuses = result;
	}

	/** The plan entry at a world position (first visible placement wins). */
	public static @Nullable BlockState plannedAt(BlockPos pos) {
		for (Placement placement : placements) {
			if (placement.visible) {
				BlockState state = placement.targetAt(pos);
				if (state != null && !state.isAir()) {
					return state;
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ persistence

	private static @Nullable Path file() {
		if (worldKey == null) {
			return null;
		}
		return PlannerWorlds.rootDir().resolve("placements").resolve(worldKey + ".json");
	}

	private static void load() {
		placements = new ArrayList<>();
		selected = -1;
		Path file = file();
		if (file == null || !Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			List<Placement> loaded = GSON.fromJson(reader, new TypeToken<List<Placement>>() {}.getType());
			if (loaded != null) {
				placements = new ArrayList<>(loaded);
				selected = placements.isEmpty() ? -1 : 0;
			}
		} catch (Exception e) {
			LOGGER.warn("Could not read placements {}", file, e);
		}
	}

	public static void save() {
		Path file = file();
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			List<Placement> toSave = PlacementTool.forSaving(placements);
			if (toSave.isEmpty()) {
				Files.deleteIfExists(file);
				return;
			}
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(toSave, writer);
			}
		} catch (Exception e) {
			LOGGER.warn("Could not save placements {}", file, e);
		}
	}
}
