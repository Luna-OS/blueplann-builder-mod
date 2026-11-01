package dev.blueprintplanner.planner;

import com.mojang.logging.LogUtils;
import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.config.PlannerConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.CrashReport;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.Services;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The hidden vanilla server that simulates the planner world. Because it is a real {@link IntegratedServer},
 * redstone, pistons, hoppers, observers etc. behave exactly like in vanilla.
 */
public class PlannerServer extends IntegratedServer {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

	private final PlannerWorldState state;
	private volatile @Nullable EntryInfo pendingEntry;
	private volatile boolean away;

	public PlannerServer(
		Thread serverThread,
		Minecraft minecraft,
		LevelStorageSource.LevelStorageAccess access,
		PackRepository packRepository,
		WorldStem worldStem,
		Services services,
		LevelLoadListener loadListener,
		EntryInfo entry
	) {
		super(serverThread, minecraft, access, packRepository, worldStem, Optional.empty(), services, loadListener);
		this.state = PlannerWorldState.load(access.getLevelPath(LevelResource.ROOT));
		this.pendingEntry = entry;
	}

	/** "Open to LAN" from inside the planner would open the planner world to others (and let mods react to it). */
	@Override
	public boolean publishServer(MinecraftServer.MultiplayerScope scope, boolean allowCommands, int port) {
		return false;
	}

	@Override
	public boolean publishServer(MinecraftServer.MultiplayerScope scope, int port) {
		return false;
	}

	@Override
	protected void runServer() {
		PlannerIsolation.enterServerThread();
		try {
			super.runServer();
		} finally {
			PlannerIsolation.leaveServerThread();
		}
	}

	/**
	 * Vanilla hands crashes of the integrated server to the client, which then crashes as well. A crash of the
	 * planner (for example caused by another mod) must not take the real world down with it.
	 */
	@Override
	protected void onServerCrash(CrashReport report) {
		String reason = report.getException().toString();
		Minecraft.getInstance().execute(() -> PlannerManager.onPlannerServerCrashed(reason));
	}

	@Override
	protected boolean initServer() {
		LOGGER.info("Starting blueprint planner server {}", SharedConstants.getCurrentVersion().name());
		this.setUsesAuthentication(false);
		this.initializeKeyPair();
		this.loadLevel();
		this.setMotd("Blueprint Planner");

		if (!this.state.initialized) {
			GameRules rules = this.getGameRules();
			rules.set(GameRules.ADVANCE_TIME, false, this);
			rules.set(GameRules.ADVANCE_WEATHER, false, this);
			rules.set(GameRules.SPAWN_MOBS, false, this);
			rules.set(GameRules.SPAWN_MONSTERS, false, this);
			rules.set(GameRules.SPAWN_PATROLS, false, this);
			rules.set(GameRules.SPAWN_PHANTOMS, false, this);
			rules.set(GameRules.SPAWN_WANDERING_TRADERS, false, this);
			rules.set(GameRules.SPAWN_WARDENS, false, this);
			rules.set(GameRules.KEEP_INVENTORY, true, this);
			rules.set(GameRules.FALL_DAMAGE, false, this);
			rules.set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, this);
			rules.set(GameRules.LOCATOR_BAR, false, this);
			this.runCommand("time set 6000");
			this.runCommand("weather clear");
			this.state.initialized = true;
			this.state.save();
		}

		this.tickRateManager().setTickRate(this.state.tickRate);
		this.tickRateManager().setFrozen(this.state.frozen);
		this.saveEverything(false, true, true);
		return true;
	}

	/** The F3 tick chart belongs to the real world (in singleplayer the real server already feeds it). */
	@Override
	public boolean isTickTimeLoggingEnabled() {
		return false;
	}

	public PlannerWorldState worldState() {
		return this.state;
	}

	public void runCommand(String command) {
		this.getCommands().performPrefixedCommand(this.createCommandSourceStack().withSuppressedOutput(), command);
	}

	public @Nullable ServerPlayer plannerPlayer() {
		return this.getPlayerList().getPlayers().isEmpty() ? null : this.getPlayerList().getPlayers().getFirst();
	}

	@Override
	protected void tickServer(BooleanSupplier haveTime) {
		super.tickServer(haveTime);
		ServerPlayer player = this.plannerPlayer();
		if (player != null && player.getY() < player.level().getMinY() - 16) {
			// Never let the planner player die in the void: a respawn while in the background would be messy.
			player.teleportTo(player.level(), player.getX(), player.level().getMinY() + 2, player.getZ(), Set.of(), player.getYRot(), player.getXRot(), true);
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			player.fallDistance = 0;
		}
	}

	/** Called on the server thread when the planner client finished logging in. */
	public void onPlayerJoin(ServerPlayer player) {
		player.setGameMode(GameType.CREATIVE);
		player.getAbilities().mayfly = true;
		player.getAbilities().flying = true;
		player.onUpdateAbilities();
		this.applyFullbright(player);
		EntryInfo entry = this.pendingEntry;
		this.pendingEntry = null;
		if (entry != null) {
			this.enterAt(player, entry);
		}
	}

	public void applyFullbright(ServerPlayer player) {
		if (PlannerConfig.get().fullbright) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
		} else {
			player.removeEffect(MobEffects.NIGHT_VISION);
		}
	}

	/** Re-entering an already running planner: move the player to the real position again. */
	public void requestEntry(EntryInfo entry) {
		this.execute(() -> {
			ServerPlayer player = this.plannerPlayer();
			if (player == null) {
				this.pendingEntry = entry;
			} else {
				this.enterAt(player, entry);
			}
		});
	}

	private void enterAt(ServerPlayer player, EntryInfo entry) {
		ServerLevel level = this.overworld();
		this.prepareArea(level, entry);
		player.teleportTo(level, entry.x(), entry.y(), entry.z(), Set.<Relative>of(), entry.yRot(), entry.xRot(), true);
		player.fallDistance = 0;
		BlockPos below = BlockPos.containing(entry.x(), entry.y() - 0.1, entry.z());
		player.getAbilities().flying = level.getBlockState(below).getCollisionShape(level, below).isEmpty();
		player.onUpdateAbilities();
	}

	/** Copies the real terrain (or generates a build plate) for chunks that were never prepared before. */
	private void prepareArea(ServerLevel level, EntryInfo entry) {
		EntryInfo.Terrain terrain = entry.terrain();
		if (terrain != null) {
			this.importTerrain(level, terrain);
		} else if (!PlannerConfig.get().importSurroundings) {
			BlockPos center = entry.blockPos();
			int radius = entry.plateRadius();
			LongList chunks = new LongArrayList();
			for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
				for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
					chunks.add(ChunkPos.pack(cx, cz));
				}
			}
			this.generatePlate(level, chunks, center.getY() - 1);
		}
		this.state.save();
	}

	/** Terrain captured on the render thread while walking around: imported here on the server thread. */
	public void importLater(EntryInfo.Terrain terrain) {
		this.execute(() -> {
			this.importTerrain(this.overworld(), terrain);
			this.state.save();
		});
	}

	public void platesLater(LongList chunks, int plateY) {
		this.execute(() -> {
			this.generatePlate(this.overworld(), chunks, plateY);
			this.state.save();
		});
	}

	/**
	 * Whole chunks of the real world, plus a floor under the copied part so nothing looks (or flows) into the void
	 * below. Chunks that were prepared before keep what they have.
	 */
	private void importTerrain(ServerLevel level, EntryInfo.Terrain terrain) {
		BlockPos min = terrain.min();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState floor = Blocks.BEDROCK.defaultBlockState();
		int floorY = min.getY() - 1;
		for (long chunkKey : terrain.chunks()) {
			if (this.state.isPrepared(chunkKey)) {
				continue;
			}
			int cx = ChunkPos.getX(chunkKey);
			int cz = ChunkPos.getZ(chunkKey);
			level.getChunk(cx, cz);
			for (int y = 0; y < terrain.sizeY(); y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						pos.set((cx << 4) + x, min.getY() + y, (cz << 4) + z);
						BlockState stateToSet = terrain.get(pos.getX() - min.getX(), y, pos.getZ() - min.getZ());
						if (!stateToSet.isAir() || !level.getBlockState(pos).isAir()) {
							level.setBlock(pos, stateToSet, SET_FLAGS);
						}
					}
				}
			}
			if (floorY >= level.getMinY()) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						pos.set((cx << 4) + x, floorY, (cz << 4) + z);
						if (level.getBlockState(pos).isAir()) {
							level.setBlock(pos, floor, SET_FLAGS);
						}
					}
				}
			}
			this.state.markPrepared(chunkKey);
		}
	}

	/** The checkerboard build plate for chunks that were never prepared before. */
	private void generatePlate(ServerLevel level, LongList chunks, int plateY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (long chunkKey : chunks) {
			if (this.state.isPrepared(chunkKey)) {
				continue;
			}
			int cx = ChunkPos.getX(chunkKey);
			int cz = ChunkPos.getZ(chunkKey);
			level.getChunk(cx, cz);
			BlockState plate = ((cx + cz) & 1) == 0 ? Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState() : Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
			for (int x = cx << 4; x < (cx << 4) + 16; x++) {
				for (int z = cz << 4; z < (cz << 4) + 16; z++) {
					pos.set(x, plateY, z);
					if (level.getBlockState(pos).isAir()) {
						level.setBlock(pos, plate, SET_FLAGS);
					}
				}
			}
			this.state.markPrepared(chunkKey);
		}
	}

	/** Clears the given chunks (everything from the bottom to the top) and forgets that they were prepared. */
	public void resetChunksAround(BlockPos center, int chunkRadius, @Nullable EntryInfo reimport) {
		this.execute(() -> {
			ServerLevel level = this.overworld();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			int ccx = center.getX() >> 4;
			int ccz = center.getZ() >> 4;
			for (int cx = ccx - chunkRadius; cx <= ccx + chunkRadius; cx++) {
				for (int cz = ccz - chunkRadius; cz <= ccz + chunkRadius; cz++) {
					level.getChunk(cx, cz);
					for (int y = level.getMinY(); y < level.getMaxY(); y++) {
						for (int x = cx << 4; x < (cx << 4) + 16; x++) {
							for (int z = cz << 4; z < (cz << 4) + 16; z++) {
								pos.set(x, y, z);
								if (!level.getBlockState(pos).isAir()) {
									level.setBlock(pos, Blocks.AIR.defaultBlockState(), SET_FLAGS);
								}
							}
						}
					}
					// stays empty on purpose; with a new copy the chunks are filled again below
					if (reimport == null) {
						this.state.markPrepared(ChunkPos.pack(cx, cz));
					} else {
						this.state.unmarkPrepared(ChunkPos.pack(cx, cz));
					}
				}
			}
			level.getAllEntities().forEach(entity -> {
				if (!(entity instanceof ServerPlayer) && Math.abs((entity.getBlockX() >> 4) - ccx) <= chunkRadius && Math.abs((entity.getBlockZ() >> 4) - ccz) <= chunkRadius) {
					entity.discard();
				}
			});
			if (reimport != null) {
				this.prepareArea(level, reimport);
			}
			this.state.save();
		});
	}

	/** Planner went to the background (player is back in the real world). */
	public void setAway(boolean away) {
		this.execute(() -> {
			if (this.away == away) {
				return;
			}
			this.away = away;
			if (away) {
				this.state.frozen = this.tickRateManager().isFrozen();
				if (PlannerConfig.get().freezeWhenAway) {
					this.tickRateManager().setFrozen(true);
				}
				this.saveEverything(true, false, false);
				this.state.save();
			} else {
				this.tickRateManager().setFrozen(this.state.frozen);
				ServerPlayer player = this.plannerPlayer();
				if (player != null) {
					this.applyFullbright(player);
				}
			}
		});
	}

	public boolean isAway() {
		return this.away;
	}

	public void setTickRate(float rate) {
		this.execute(() -> {
			this.tickRateManager().setTickRate(rate);
			this.state.tickRate = this.tickRateManager().tickrate();
			this.state.save();
		});
	}

	public void setFrozen(boolean frozen) {
		this.execute(() -> {
			this.tickRateManager().setFrozen(frozen);
			this.state.frozen = frozen;
			this.state.save();
		});
	}

	public void step(int ticks) {
		this.execute(() -> {
			if (!this.tickRateManager().isFrozen()) {
				this.tickRateManager().setFrozen(true);
				this.state.frozen = true;
			}
			this.tickRateManager().stepGameIfPaused(ticks);
		});
	}

	public void sprint(int ticks) {
		this.execute(() -> this.tickRateManager().requestGameToSprint(ticks));
	}

	public <T> void setGameRule(net.minecraft.world.level.gamerules.GameRule<T> rule, T value) {
		this.execute(() -> this.getGameRules().set(rule, value, this));
	}

	@Override
	public void stopServer() {
		this.state.save();
		super.stopServer();
	}
}
