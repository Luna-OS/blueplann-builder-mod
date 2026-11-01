package dev.blueprintplanner.planner;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import dev.blueprintplanner.compat.PlannerIsolation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.core.HolderSet;
import org.slf4j.Logger;

/** Creates and opens the planner worlds in {@code .minecraft/blueprintplanner/worlds}. */
public final class PlannerWorlds {
	private static final Logger LOGGER = LogUtils.getLogger();

	private PlannerWorlds() {
	}

	public record Opened(LevelStorageSource.LevelStorageAccess access, PackRepository packRepository, WorldStem stem) {
	}

	public static Path rootDir() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("blueprintplanner");
	}

	/** One planner world per real server/world and dimension. */
	public static String keyFor(Minecraft mc) {
		String base;
		IntegratedServer sp = mc.getSingleplayerServer();
		ServerData serverData = mc.getCurrentServer();
		if (sp != null) {
			base = "sp_" + sp.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
		} else if (serverData != null) {
			base = (serverData.isRealm() ? "realm_" : "mp_") + serverData.ip;
		} else {
			base = "unknown";
		}
		String dimension = mc.level != null ? mc.level.dimension().identifier().toString() : "overworld";
		if (dimension.equals(Level.OVERWORLD.identifier().toString())) {
			dimension = "";
		}
		String key = dimension.isEmpty() ? base : base + "_" + dimension;
		return sanitize(key);
	}

	public static String sanitize(String key) {
		String cleaned = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
		return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
	}

	/** Opens (or creates) the planner world. Must be called on the main thread; completes on the main thread. */
	public static CompletableFuture<Opened> open(String key) {
		Minecraft mc = Minecraft.getInstance();
		LevelStorageSource source = LevelStorageSource.createDefault(rootDir().resolve("worlds"));
		LevelStorageSource.LevelStorageAccess access;
		try {
			access = source.validateAndCreateAccess(key);
		} catch (Exception e) {
			return CompletableFuture.failedFuture(e);
		}

		try {
			PackRepository packRepository = ServerPacksSource.createPackRepository(access);
			CompletableFuture<WorldStem> stem;
			boolean exists = Files.exists(access.getLevelPath(LevelResource.LEVEL_DATA_FILE));
			if (exists) {
				Dynamic<?> unfixed = access.getUnfixedDataTag(false);
				int dataVersion = NbtUtils.getDataVersion(unfixed);
				if (DataFixers.getFileFixer().requiresFileFixing(dataVersion)) {
					access.safeClose();
					backupOutdated(source, key);
					return open(key);
				}
				Dynamic<?> levelData = DataFixTypes.LEVEL.updateToCurrentVersion(DataFixers.getDataFixer(), unfixed, dataVersion);
				stem = loadExisting(mc, access, packRepository, levelData);
			} else {
				stem = createFresh(mc, packRepository);
			}
			return stem.thenApply(worldStem -> {
				if (!exists) {
					access.saveDataTag(worldStem.worldDataAndGenSettings().data());
				}
				return new Opened(access, packRepository, worldStem);
			}).whenComplete((opened, error) -> {
				if (error != null) {
					access.safeClose();
				}
			});
		} catch (Exception e) {
			access.safeClose();
			return CompletableFuture.failedFuture(e);
		}
	}

	private static void backupOutdated(LevelStorageSource source, String key) {
		try {
			Path dir = source.getLevelPath(key);
			String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
			Files.move(dir, dir.resolveSibling(key + "_old_" + suffix));
			LOGGER.warn("Planner world {} was created by an incompatible version; moved it aside", key);
		} catch (Exception e) {
			LOGGER.error("Could not move outdated planner world {}", key, e);
		}
	}

	private static WorldLoader.InitConfig initConfig(WorldLoader.PackConfig packConfig) {
		return new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.INTEGRATED, LevelBasedPermissionSet.GAMEMASTER);
	}

	private static CompletableFuture<WorldStem> createFresh(Minecraft mc, PackRepository packRepository) {
		WorldDataConfiguration dataConfiguration = WorldDataConfiguration.DEFAULT;
		LevelSettings settings = new LevelSettings(
			"Blueprint Planner", GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, dataConfiguration
		);
		WorldOptions options = new WorldOptions(0L, false, false);
		WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packRepository, dataConfiguration, false, false);
		return WorldLoader.load(initConfig(packConfig), context -> {
			HolderLookup.Provider registries = context.datapackWorldRegistries();
			WorldDimensions dimensions = new WorldDimensions(Map.of(
				LevelStem.OVERWORLD,
				new LevelStem(registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD), voidGenerator(registries))
			));
			WorldDimensions.Complete complete = dimensions.bake(context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM));
			return new WorldLoader.DataLoadOutput<>(
				new LevelDataAndDimensions.WorldDataAndGenSettings(
					new PrimaryLevelData(settings, complete.specialWorldProperty(), complete.lifecycle()), new WorldGenSettings(options, dimensions)
				),
				complete.dimensionsRegistryAccess()
			);
		}, WorldStem::new, PlannerIsolation.plannerServerExecutor(Util.backgroundExecutor()),
			PlannerIsolation.plannerServerExecutor(mc));
	}

	private static FlatLevelSource voidGenerator(HolderLookup.Provider registries) {
		FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(
			Optional.of(HolderSet.empty()), registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS), List.of()
		).withBiomeAndLayers(List.of(new FlatLayerInfo(1, Blocks.AIR)), Optional.of(HolderSet.empty()), registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
		return new FlatLevelSource(settings);
	}

	private static CompletableFuture<WorldStem> loadExisting(
		Minecraft mc, LevelStorageSource.LevelStorageAccess access, PackRepository packRepository, Dynamic<?> levelData
	) {
		WorldLoader.PackConfig packConfig = LevelStorageSource.getPackConfig(levelData, packRepository, false);
		return WorldLoader.load(initConfig(packConfig), context -> {
			Registry<LevelStem> datapackDimensions = context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM);
			LevelDataAndDimensions data = LevelStorageSource.getLevelDataAndDimensions(
				access, levelData, context.dataConfiguration(), datapackDimensions, context.datapackWorldRegistries()
			);
			return new WorldLoader.DataLoadOutput<>(data.worldDataAndGenSettings(), data.dimensions().dimensionsRegistryAccess());
		}, WorldStem::new, PlannerIsolation.plannerServerExecutor(Util.backgroundExecutor()),
			PlannerIsolation.plannerServerExecutor(mc));
	}
}
