package dev.blueprintplanner.util;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

/**
 * The section renderer learns about loaded chunks only through "chunk added" deltas of the visible level.
 * After switching levels we replay those deltas for every chunk that is already loaded, otherwise the
 * level we switch back to would stay invisible.
 */
public final class ChunkRenderHelper {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static MethodHandle storageGetter;
	private static MethodHandle chunksGetter;
	private static MethodHandle onChunkAdded;
	private static boolean failed;

	private ChunkRenderHelper() {
	}

	private static boolean init() {
		if (storageGetter != null) {
			return true;
		}
		if (failed) {
			return false;
		}
		try {
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			Field storageField = ClientChunkCache.class.getDeclaredField("storage");
			storageField.setAccessible(true);
			Class<?> storageClass = storageField.getType();
			Field chunksField = storageClass.getDeclaredField("chunks");
			chunksField.setAccessible(true);
			Method added = storageClass.getDeclaredMethod("onChunkAdded", LevelChunk.class);
			added.setAccessible(true);
			storageGetter = lookup.unreflectGetter(storageField).asType(MethodType.methodType(Object.class, ClientChunkCache.class));
			chunksGetter = lookup.unreflectGetter(chunksField).asType(MethodType.methodType(AtomicReferenceArray.class, Object.class));
			onChunkAdded = lookup.unreflect(added).asType(MethodType.methodType(void.class, Object.class, LevelChunk.class));
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			failed = true;
			LOGGER.error("Blueprint Planner: could not access the client chunk storage, chunks may not render after switching worlds", e);
			return false;
		}
	}

	public static void reannounceLoadedChunks(ClientLevel level) {
		if (!init()) {
			return;
		}
		try {
			Object storage = storageGetter.invokeExact(level.getChunkSource());
			@SuppressWarnings("unchecked")
			AtomicReferenceArray<LevelChunk> chunks = (AtomicReferenceArray<LevelChunk>) chunksGetter.invokeExact(storage);
			for (int i = 0; i < chunks.length(); i++) {
				LevelChunk chunk = chunks.get(i);
				if (chunk != null) {
					onChunkAdded.invokeExact(storage, chunk);
				}
			}
		} catch (Throwable t) {
			LOGGER.warn("Could not re-announce loaded chunks", t);
		}
	}

	/** Every chunk the client currently holds for {@code level}. */
	public static void forEachLoadedChunk(ClientLevel level, Consumer<LevelChunk> action) {
		if (!init()) {
			return;
		}
		AtomicReferenceArray<LevelChunk> chunks;
		try {
			Object storage = storageGetter.invokeExact(level.getChunkSource());
			@SuppressWarnings("unchecked")
			AtomicReferenceArray<LevelChunk> loaded = (AtomicReferenceArray<LevelChunk>) chunksGetter.invokeExact(storage);
			chunks = loaded;
		} catch (Throwable t) {
			LOGGER.warn("Could not read loaded chunks", t);
			return;
		}
		for (int i = 0; i < chunks.length(); i++) {
			LevelChunk chunk = chunks.get(i);
			if (chunk != null) {
				action.accept(chunk);
			}
		}
	}
}
