package dev.blueprintplanner.blueprint;

import com.mojang.logging.LogUtils;
import dev.blueprintplanner.planner.PlannerWorlds;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Blueprint files in {@code .minecraft/blueprintplanner/blueprints} (vanilla structure {@code .nbt} format). */
public final class BlueprintLibrary {
	private static final Logger LOGGER = LogUtils.getLogger();

	public record Entry(String fileName, Path path, long modified, long sizeBytes) {
		public String displayName() {
			return this.fileName.endsWith(".nbt") ? this.fileName.substring(0, this.fileName.length() - 4) : this.fileName;
		}
	}

	private BlueprintLibrary() {
	}

	public static Path directory() {
		Path dir = PlannerWorlds.rootDir().resolve("blueprints");
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			LOGGER.warn("Could not create blueprint directory {}", dir, e);
		}
		return dir;
	}

	public static List<Entry> list() {
		List<Entry> entries = new ArrayList<>();
		try (Stream<Path> files = Files.list(directory())) {
			files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nbt")).forEach(p -> {
				try {
					entries.add(new Entry(p.getFileName().toString(), p, Files.getLastModifiedTime(p).toMillis(), Files.size(p)));
				} catch (IOException ignored) {
				}
			});
		} catch (IOException e) {
			LOGGER.warn("Could not list blueprints", e);
		}
		entries.sort(Comparator.comparingLong(Entry::modified).reversed());
		return entries;
	}

	public static String fileNameFor(String name) {
		String cleaned = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
		if (cleaned.isEmpty()) {
			cleaned = "blueprint";
		}
		return cleaned + ".nbt";
	}

	public static Path save(Blueprint blueprint) throws IOException {
		Path file = directory().resolve(fileNameFor(blueprint.name()));
		NbtIo.writeCompressed(blueprint.toNbt(), file);
		return file;
	}

	public static Blueprint load(Path file) throws IOException {
		CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		String fallback = file.getFileName().toString().replaceFirst("\\.nbt$", "");
		return Blueprint.fromNbt(tag, fallback);
	}

	public static @Nullable Blueprint loadByFileName(String fileName) {
		Path file = directory().resolve(fileName);
		if (!Files.exists(file)) {
			return null;
		}
		try {
			return load(file);
		} catch (Exception e) {
			LOGGER.warn("Could not load blueprint {}", file, e);
			return null;
		}
	}

	public static void delete(Entry entry) {
		try {
			Files.deleteIfExists(entry.path());
		} catch (IOException e) {
			LOGGER.warn("Could not delete blueprint {}", entry.path(), e);
		}
	}

	public static boolean exists(String name) {
		return Files.exists(directory().resolve(fileNameFor(name)));
	}
}
