package dev.blueprintplanner.planner;

import dev.blueprintplanner.config.PlannerConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Where and how the player entered the planner, plus (optionally) a copy of the real blocks around them
 * taken from the client's chunk cache.
 */
public record EntryInfo(double x, double y, double z, float yRot, float xRot, @Nullable Terrain terrain, int plateRadius) {
	public static EntryInfo capture(Player player, Level realLevel) {
		PlannerConfig config = PlannerConfig.get();
		Terrain terrain = null;
		if (config.importSurroundings) {
			BlockPos center = player.blockPosition();
			int radius = config.importRadius;
			terrain = Terrain.capture(realLevel, (center.getX() - radius) >> 4, (center.getZ() - radius) >> 4,
				(center.getX() + radius) >> 4, (center.getZ() + radius) >> 4, center.getY(), config.importDepth, config.importHeight);
		}
		return new EntryInfo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), terrain, config.plateRadius);
	}

	public BlockPos blockPos() {
		return BlockPos.containing(this.x, this.y, this.z);
	}

	/**
	 * Block states of whole chunks of the real world. The box reaches from {@code depth} below the lowest ground
	 * (or the player, if lower) to {@code height} above the highest, so a hill, the ground under a flying player or
	 * a cave all come along. Chunks the client does not have are left out of {@link #chunks()}.
	 */
	public record Terrain(BlockPos min, int sizeX, int sizeY, int sizeZ, BlockState[] states, LongList chunks) {
		public static @Nullable Terrain capture(Level level, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int referenceY, int depth, int height) {
			LongList chunks = new LongArrayList();
			int lowest = referenceY;
			int highest = referenceY;
			for (int cx = minChunkX; cx <= maxChunkX; cx++) {
				for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
					ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
					if (chunk == null) {
						continue;
					}
					chunks.add(ChunkPos.pack(cx, cz));
					for (int x = 0; x < 16; x++) {
						for (int z = 0; z < 16; z++) {
							int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
							lowest = Math.min(lowest, surface);
							highest = Math.max(highest, surface);
						}
					}
				}
			}
			if (chunks.isEmpty()) {
				return null;
			}
			int minY = Math.max(level.getMinY(), lowest - depth);
			int maxY = Math.min(level.getMaxY(), highest + height);
			BlockPos min = new BlockPos(minChunkX << 4, minY, minChunkZ << 4);
			int sizeX = (maxChunkX - minChunkX + 1) * 16;
			int sizeY = Math.max(1, maxY - minY + 1);
			int sizeZ = (maxChunkZ - minChunkZ + 1) * 16;
			BlockState[] states = new BlockState[sizeX * sizeY * sizeZ];
			java.util.Arrays.fill(states, Blocks.AIR.defaultBlockState());
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (long key : chunks) {
				int cx = ChunkPos.getX(key);
				int cz = ChunkPos.getZ(key);
				ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue;
				}
				for (int y = 0; y < sizeY; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							pos.set((cx << 4) + x, minY + y, (cz << 4) + z);
							states[(y * sizeZ + pos.getZ() - min.getZ()) * sizeX + pos.getX() - min.getX()] = chunk.getBlockState(pos);
						}
					}
				}
			}
			return new Terrain(min, sizeX, sizeY, sizeZ, states, chunks);
		}

		public BlockState get(int x, int y, int z) {
			return this.states[(y * this.sizeZ + z) * this.sizeX + x];
		}

		public int maxY() {
			return this.min.getY() + this.sizeY - 1;
		}
	}
}
