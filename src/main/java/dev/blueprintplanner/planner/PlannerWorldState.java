package dev.blueprintplanner.planner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

/** Planner specific data stored next to the planner world ({@code blueprintplanner.json}). */
public final class PlannerWorldState {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public boolean initialized;
	public float tickRate = 20.0F;
	public boolean frozen;
	/** Chunks that were already prepared (terrain imported or plate generated). */
	public List<Long> preparedChunks = new ArrayList<>();

	private transient Path file;
	private transient LongOpenHashSet prepared;

	public static PlannerWorldState load(Path worldDir) {
		Path file = worldDir.resolve("blueprintplanner.json");
		PlannerWorldState state = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				state = GSON.fromJson(reader, PlannerWorldState.class);
			} catch (Exception e) {
				LOGGER.warn("Could not read planner world state {}", file, e);
			}
		}
		if (state == null) {
			state = new PlannerWorldState();
		}
		state.file = file;
		state.prepared = new LongOpenHashSet();
		if (state.preparedChunks != null) {
			state.preparedChunks.forEach(state.prepared::add);
		}
		return state;
	}

	public synchronized boolean isPrepared(long chunk) {
		return this.prepared.contains(chunk);
	}

	public synchronized void markPrepared(long chunk) {
		this.prepared.add(chunk);
	}

	public synchronized void unmarkPrepared(long chunk) {
		this.prepared.remove(chunk);
	}

	public synchronized void save() {
		this.preparedChunks = new ArrayList<>(this.prepared);
		try {
			Files.createDirectories(this.file.getParent());
			try (Writer writer = Files.newBufferedWriter(this.file)) {
				GSON.toJson(this, writer);
			}
		} catch (Exception e) {
			LOGGER.warn("Could not save planner world state", e);
		}
	}
}
