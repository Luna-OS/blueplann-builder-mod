package dev.blueprintplanner.compat.bobby;

import de.johni0702.minecraft.bobby.FakeChunkManager;
import de.johni0702.minecraft.bobby.ext.ClientChunkCacheExt;
import java.util.ArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** Only loaded when Bobby is installed. */
public final class BobbyCompat {
	private BobbyCompat() {
	}

	/**
	 * Bobby's fake chunks are not part of the vanilla chunk storage, so a world switch would leave them invisible
	 * (vanilla renderer) until Bobby reloads them. Bobby itself re-announces them the same way.
	 */
	public static void reannounceFakeChunks(ClientLevel level) {
		FakeChunkManager manager = ((ClientChunkCacheExt) level.getChunkSource()).bobby_getFakeChunkManager();
		if (manager == null) {
			return;
		}
		for (LevelChunk chunk : new ArrayList<>(manager.getFakeChunks())) {
			manager.markAsLoaded(chunk.getPos().x(), chunk.getPos().z(), chunk);
		}
	}
}
