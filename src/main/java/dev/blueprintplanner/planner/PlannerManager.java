package dev.blueprintplanner.planner;

import com.mojang.logging.LogUtils;
import dev.blueprintplanner.compat.CompatWarnings;
import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.config.PlannerConfig;
import dev.blueprintplanner.mixin.ClientCommonPacketListenerAccessor;
import dev.blueprintplanner.mixin.ClientHandshakePacketListenerAccessor;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.LoggingLevelLoadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Lifecycle of the planner: opening the planner world, starting the hidden server, connecting to it,
 * switching between real world and planner, ejecting on damage and shutting everything down again.
 */
public final class PlannerManager {
	private static final Logger LOGGER = LogUtils.getLogger();

	public enum Phase {
		OFF,
		LOADING_WORLD,
		STARTING_SERVER,
		CONNECTING,
		RUNNING
	}

	/** Every connection ever opened to a planner server, so late callbacks are still recognized after shutdown. */
	// read from other threads too (compat hooks of other mods)
	private static final Set<Connection> PLANNER_CONNECTIONS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private static Phase phase = Phase.OFF;
	private static @Nullable String worldKey;
	private static @Nullable CompletableFuture<PlannerWorlds.Opened> opening;
	private static @Nullable PlannerServer server;
	private static @Nullable PlannerServer stoppingServer;
	private static volatile @Nullable Connection connection;
	/** Set when the planner server crashed, shown once the planner has closed. */
	private static @Nullable String pendingCrash;
	/**
	 * Why the planner server dropped the planner player (set on the server side). The client side often only sees
	 * "Disconnected", for example when another mod kicks the player while it joins.
	 */
	private static volatile @Nullable Component serverSideReason;
	private static @Nullable EntryInfo pendingEntry;
	/** The last entry: its height is where new build plate chunks go while you walk around. */
	private static @Nullable EntryInfo lastEntry;
	/** Chunks already handed to the planner server while walking (so they are not captured twice). */
	private static final LongSet QUEUED_CHUNKS = new LongOpenHashSet();
	private static int terrainTimer;
	private static boolean enterWhenReady;
	private static float lastRealHealth = -1.0F;
	private static @Nullable Component pendingExitReason;
	private static @Nullable StaticTagSnapshot tagSnapshot;
	/** Side whose packet is being handled right now (null outside of packet handling). */
	private static ContextSwitcher.@Nullable Side handlingSide;

	private PlannerManager() {
	}

	// ------------------------------------------------------------------ queries

	public static Phase phase() {
		return phase;
	}

	public static boolean isActive() {
		return ContextSwitcher.active() == ContextSwitcher.Side.PLANNER;
	}

	public static boolean isLoading() {
		return phase == Phase.LOADING_WORLD || phase == Phase.STARTING_SERVER || phase == Phase.CONNECTING;
	}

	public static @Nullable PlannerServer server() {
		return server;
	}

	public static @Nullable Connection plannerConnection() {
		return connection;
	}

	public static boolean isHandlingPlannerPacket() {
		return handlingSide == ContextSwitcher.Side.PLANNER;
	}

	/** The real-world player, regardless of which side is currently installed. */
	public static @Nullable LocalPlayer realPlayer() {
		Minecraft mc = Minecraft.getInstance();
		if (ContextSwitcher.installed() == ContextSwitcher.Side.REAL) {
			return mc.player;
		}
		return ContextSwitcher.contextOf(ContextSwitcher.Side.REAL).player;
	}

	public static @Nullable ClientLevel realLevel() {
		Minecraft mc = Minecraft.getInstance();
		if (ContextSwitcher.installed() == ContextSwitcher.Side.REAL) {
			return mc.level;
		}
		return ContextSwitcher.contextOf(ContextSwitcher.Side.REAL).level;
	}

	public static @Nullable ClientLevel plannerLevel() {
		if (connection == null) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		if (ContextSwitcher.installed() == ContextSwitcher.Side.PLANNER) {
			return mc.level;
		}
		return ContextSwitcher.contextOf(ContextSwitcher.Side.PLANNER).level;
	}

	/** The real-world packet listener (null if not connected). */
	public static @Nullable ClientPacketListener realListener() {
		LocalPlayer player = realPlayer();
		return player != null ? player.connection : null;
	}

	public static boolean isPlannerConnection(@Nullable Connection candidate) {
		return candidate != null && (candidate == connection || PLANNER_CONNECTIONS.contains(candidate));
	}

	public static boolean isPlannerLevel(@Nullable ClientLevel level) {
		return level != null && level == plannerLevel();
	}

	/** True for the level of whichever side is not visible right now. */
	public static boolean isBackgroundLevel(@Nullable ClientLevel level) {
		if (level == null || connection == null) {
			return false;
		}
		return isActive() ? level == realLevel() : level == plannerLevel();
	}

	/** Which side a client packet listener belongs to, or null if it is not a client listener. */
	public static ContextSwitcher.@Nullable Side sideOf(PacketListener listener) {
		Connection listenerConnection;
		if (listener instanceof ClientCommonPacketListenerImpl common) {
			listenerConnection = ((ClientCommonPacketListenerAccessor) common).blueprintplanner$getConnection();
		} else if (listener instanceof ClientHandshakePacketListenerImpl handshake) {
			listenerConnection = ((ClientHandshakePacketListenerAccessor) handshake).blueprintplanner$getConnection();
		} else {
			return null;
		}
		return isPlannerConnection(listenerConnection) ? ContextSwitcher.Side.PLANNER : ContextSwitcher.Side.REAL;
	}

	// ------------------------------------------------------------------ user actions

	public static void toggle() {
		if (isActive()) {
			exit(Component.translatable("blueprintplanner.exit.manual"));
		} else {
			requestEnter();
		}
	}

	public static void requestEnter() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (mc.level == null || player == null || isActive()) {
			LOGGER.info("Planner: enter ignored (level={}, player={}, active={})", mc.level != null, player != null, isActive());
			return;
		}
		if (isLoading()) {
			message(Component.translatable("blueprintplanner.loading"), false);
			return;
		}
		if (player.isDeadOrDying()) {
			return;
		}
		IntegratedServer singleplayer = mc.getSingleplayerServer();
		if (singleplayer != null && singleplayer.getClass() != IntegratedServer.class) {
			// replay viewers (Flashback etc.) run their own integrated server subclass with replay-wide hooks
			message(Component.translatable("blueprintplanner.enter.replay").withStyle(ChatFormatting.RED), true);
			return;
		}
		CompatWarnings.onEnter();
		if (PlannerConfig.get().requireOnGround && !player.onGround() && !player.getAbilities().flying && !player.isPassenger() && !player.isInWater()) {
			LOGGER.info("Planner: enter refused, real player is not on the ground");
			message(Component.translatable("blueprintplanner.enter.not_on_ground").withStyle(ChatFormatting.RED), true);
			return;
		}

		EntryInfo entry = EntryInfo.capture(player, mc.level);
		lastEntry = entry;
		String key = PlannerWorlds.keyFor(mc);
		if (phase == Phase.RUNNING && key.equals(worldKey) && server != null) {
			enterRunning(entry);
			return;
		}
		if (phase != Phase.OFF) {
			shutdown(false);
		}
		start(key, entry);
	}

	public static void exit(Component reason) {
		if (!isActive()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ContextSwitcher.switchTo(ContextSwitcher.Side.REAL);
		if (mc.gui.screen() != null) {
			mc.gui.setScreen(null);
		}
		if (server != null) {
			server.setAway(true);
		}
		message(reason, true);
	}

	/** Exit from inside packet handlers: performed at the end of the current client tick. */
	public static void requestExit(Component reason) {
		if (isActive() && pendingExitReason == null) {
			pendingExitReason = reason;
		}
	}

	// ------------------------------------------------------------------ lifecycle

	private static void start(String key, EntryInfo entry) {
		Minecraft mc = Minecraft.getInstance();
		LOGGER.info("Planner: opening planner world '{}'", key);
		PlannerIsolation.beginSession();
		serverSideReason = null;
		worldKey = key;
		pendingEntry = entry;
		enterWhenReady = true;
		phase = Phase.LOADING_WORLD;
		message(Component.translatable("blueprintplanner.loading"), true);
		if (stoppingServer != null && !stoppingServer.isShutdown()) {
			// the previous planner server still holds the world lock; open once it is gone
			return;
		}
		beginOpening(mc);
	}

	private static void beginOpening(Minecraft mc) {
		stoppingServer = null;
		tagSnapshot = mc.getSingleplayerServer() == null ? StaticTagSnapshot.capture() : null;
		opening = PlannerWorlds.open(worldKey);
	}

	private static void enterRunning(EntryInfo entry) {
		Minecraft mc = Minecraft.getInstance();
		server.requestEntry(entry);
		server.setAway(false);
		ContextSwitcher.switchTo(ContextSwitcher.Side.PLANNER);
		if (mc.gui.screen() != null) {
			mc.gui.setScreen(null);
		}
		message(Component.translatable("blueprintplanner.entered"), true);
	}

	public static void onClientTick(Minecraft mc) {
		switch (phase) {
			case LOADING_WORLD -> tickLoadingWorld(mc);
			case STARTING_SERVER -> tickStartingServer(mc);
			default -> {
			}
		}

		trackRealHealth();
		if (pendingExitReason != null) {
			Component reason = pendingExitReason;
			pendingExitReason = null;
			exit(reason);
		}

		tickBackgroundConnection(mc);
		tickTerrainLoading(mc);
	}

	/**
	 * While you walk around the planner, chunks you reach are copied from the real world (as far as your client has
	 * them loaded) or get a build plate, so the copied area does not end in a cliff.
	 */
	private static void tickTerrainLoading(Minecraft mc) {
		PlannerServer plannerServer = server;
		ClientLevel real = realLevel();
		if (!isActive() || plannerServer == null || mc.player == null || real == null || --terrainTimer > 0) {
			return;
		}
		terrainTimer = 10;
		PlannerConfig config = PlannerConfig.get();
		BlockPos center = mc.player.blockPosition();
		int radius = Math.max(1, (config.importRadius + 15) / 16);
		int ccx = center.getX() >> 4;
		int ccz = center.getZ() >> 4;
		LongList wanted = new LongArrayList();
		for (int cx = ccx - radius; cx <= ccx + radius; cx++) {
			for (int cz = ccz - radius; cz <= ccz + radius; cz++) {
				long key = ChunkPos.pack(cx, cz);
				if (!plannerServer.worldState().isPrepared(key) && !QUEUED_CHUNKS.contains(key)) {
					wanted.add(key);
				}
			}
		}
		if (wanted.isEmpty()) {
			return;
		}
		if (config.importSurroundings) {
			for (long key : wanted) {
				int cx = ChunkPos.getX(key);
				int cz = ChunkPos.getZ(key);
				EntryInfo.Terrain terrain = EntryInfo.Terrain.capture(real, cx, cz, cx, cz, center.getY(), config.importDepth, config.importHeight);
				if (terrain != null) {
					QUEUED_CHUNKS.add(key);
					plannerServer.importLater(terrain);
				}
			}
		} else if (lastEntry != null) {
			QUEUED_CHUNKS.addAll(wanted);
			plannerServer.platesLater(wanted, lastEntry.blockPos().getY() - 1);
		}
	}

	private static void tickLoadingWorld(Minecraft mc) {
		if (opening == null) {
			if (stoppingServer == null || stoppingServer.isShutdown()) {
				beginOpening(mc);
			}
			return;
		}
		if (!opening.isDone()) {
			return;
		}
		PlannerWorlds.Opened opened;
		try {
			opened = opening.join();
		} catch (Exception e) {
			LOGGER.error("Could not open planner world {}", worldKey, e);
			fail(Component.translatable("blueprintplanner.error.world", e.getMessage()));
			return;
		} finally {
			opening = null;
		}

		if (tagSnapshot != null && realListener() != null) {
			tagSnapshot.restore(realListener().registryAccess());
		}
		tagSnapshot = null;

		try {
			EntryInfo entry = pendingEntry;
			server = MinecraftServer.spin(thread -> new PlannerServer(
				thread, mc, opened.access(), opened.packRepository(), opened.stem(), mc.services(), LoggingLevelLoadListener.forSingleplayer(), entry
			));
			phase = Phase.STARTING_SERVER;
		} catch (Throwable t) {
			LOGGER.error("Could not start planner server", t);
			opened.access().safeClose();
			fail(Component.translatable("blueprintplanner.error.server", String.valueOf(t.getMessage())));
		}
	}

	private static void tickStartingServer(Minecraft mc) {
		if (server == null) {
			phase = Phase.OFF;
			return;
		}
		if (server.isShutdown() || !server.isRunning()) {
			String crash = pendingCrash;
			pendingCrash = null;
			fail(crash != null
				? Component.translatable("blueprintplanner.error.crashed", crash)
				: Component.translatable("blueprintplanner.error.server", "stopped"));
			return;
		}
		if (!server.isReady()) {
			return;
		}

		SocketAddress address = server.getConnection().startMemoryChannel();
		Connection newConnection = Connection.connectToLocalServer(address);
		connection = newConnection;
		PLANNER_CONNECTIONS.add(newConnection);
		newConnection.initiateServerboundPlayConnection(
			address.toString(),
			0,
			new ClientHandshakePacketListenerImpl(newConnection, mc, null, null, false, null, status -> {}, new LevelLoadTracker(), null)
		);
		newConnection.send(new ServerboundHelloPacket(mc.getUser().getName(), mc.getUser().getProfileId()));
		phase = Phase.CONNECTING;
	}

	/** Ticks the connection of the world that is in the background (the active one is ticked by vanilla). */
	private static void tickBackgroundConnection(Minecraft mc) {
		ContextSwitcher.Side backgroundSide = isActive() ? ContextSwitcher.Side.REAL : ContextSwitcher.Side.PLANNER;
		Connection backgroundConnection;
		if (backgroundSide == ContextSwitcher.Side.PLANNER) {
			backgroundConnection = connection;
		} else {
			ClientPacketListener real = realListener();
			backgroundConnection = real != null ? real.getConnection() : null;
		}
		if (backgroundConnection == null) {
			return;
		}

		ContextSwitcher.runIn(backgroundSide, () -> {
			if (backgroundConnection.isConnected()) {
				backgroundConnection.tick();
			} else {
				backgroundConnection.handleDisconnection();
			}
		});
	}

	// ------------------------------------------------------------------ packet routing (called from mixins)

	public static void handlePacket(ContextSwitcher.Side side, Packet<?> packet, Runnable handler) {
		if (side == ContextSwitcher.Side.REAL && isActive() && mustLeavePlannerFor(packet)) {
			exit(Component.translatable("blueprintplanner.exit.world_event"));
		}

		ContextSwitcher.Side previous = handlingSide;
		handlingSide = side;
		try {
			ContextSwitcher.runIn(side, handler);
		} finally {
			handlingSide = previous;
		}

		if (side == ContextSwitcher.Side.PLANNER && packet instanceof ClientboundLoginPacket) {
			onPlannerLoggedIn();
		}
	}

	private static boolean mustLeavePlannerFor(Packet<?> packet) {
		return packet instanceof ClientboundRespawnPacket
			|| packet instanceof ClientboundLoginPacket
			|| packet instanceof ClientboundStartConfigurationPacket
			|| packet instanceof ClientboundFinishConfigurationPacket
			|| packet instanceof ClientboundDisconnectPacket
			|| packet instanceof ClientboundPlayerCombatKillPacket
			|| packet instanceof ClientboundOpenScreenPacket;
	}

	private static void onPlannerLoggedIn() {
		LOGGER.info("Planner: logged in to planner server");
		phase = Phase.RUNNING;
		if (enterWhenReady && server != null) {
			enterWhenReady = false;
			server.setAway(false);
			ContextSwitcher.switchTo(ContextSwitcher.Side.PLANNER);
			Minecraft mc = Minecraft.getInstance();
			if (mc.gui.screen() != null) {
				mc.gui.setScreen(null);
			}
			message(Component.translatable("blueprintplanner.entered"), true);
		} else if (server != null) {
			server.setAway(true);
		}
	}

	/** The server sent a damage event for the real player (any hit, even one fully absorbed). */
	public static void onRealPlayerDamageEvent() {
		if (PlannerConfig.get().ejectMinDamage <= 0.0F) {
			onRealPlayerDamaged();
		}
	}

	/**
	 * Health is synchronised through entity data before the health packet arrives, so the drop is
	 * detected by comparing the real player's health from tick to tick.
	 */
	private static void trackRealHealth() {
		LocalPlayer real = phase != Phase.OFF ? realPlayer() : null;
		if (real == null) {
			lastRealHealth = -1.0F;
			return;
		}
		float health = real.getHealth();
		if (lastRealHealth >= 0.0F && lastRealHealth - health > 0.0F && lastRealHealth - health >= PlannerConfig.get().ejectMinDamage) {
			onRealPlayerDamaged();
		}
		lastRealHealth = health;
	}

	private static void onRealPlayerDamaged() {
		if (!PlannerConfig.get().ejectOnDamage) {
			return;
		}
		if (isActive()) {
			if (pendingExitReason == null) {
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.6F));
			}
			requestExit(Component.translatable("blueprintplanner.exit.damage").withStyle(ChatFormatting.RED));
		} else if (isLoading() && enterWhenReady) {
			enterWhenReady = false;
			message(Component.translatable("blueprintplanner.exit.damage").withStyle(ChatFormatting.RED), true);
		}
	}

	/** A planner chunk failed to generate or load (the server itself keeps running): close the planner. */
	public static void onPlannerChunkCrashed(String reason) {
		LOGGER.error("Planner chunk generation/loading failed: {}", reason);
		if (phase != Phase.OFF) {
			fail(Component.translatable("blueprintplanner.error.crashed", reason));
		}
	}

	/** The planner server crashed; the crash report is already saved by vanilla. */
	/** Reasons that only say "the connection is gone", without why. */
	private static boolean isGenericReason(Component reason) {
		return reason.getContents() instanceof TranslatableContents translatable && switch (translatable.getKey()) {
			case "multiplayer.disconnect.generic", "disconnect.endOfStream", "disconnect.disconnected", "disconnect.lost" -> true;
			default -> false;
		};
	}

	/** Planner server thread or its network thread: the planner player's connection is closing for this reason. */
	public static void onPlannerServerDisconnect(Component reason) {
		if (connection != null) {
			serverSideReason = reason;
		}
	}

	/** After "clear area" / "copy again": those chunks may be captured again. */
	public static void forgetQueuedChunks() {
		QUEUED_CHUNKS.clear();
	}

	public static void onPlannerServerCrashed(String reason) {
		LOGGER.error("Planner server crashed: {}", reason);
		pendingCrash = reason;
	}

	/** The planner connection closed on its own (server crash etc.). */
	public static void onPlannerDisconnected(Component reason) {
		LOGGER.warn("Planner connection closed: {}", reason.getString());
		if (connection == null) {
			return;
		}
		if (isActive()) {
			ContextSwitcher.switchTo(ContextSwitcher.Side.REAL);
			Minecraft.getInstance().gui.setScreen(null);
		}
		ContextSwitcher.clearBackground();
		connection = null;
		stopServer();
		phase = Phase.OFF;
		Component kicked = serverSideReason;
		serverSideReason = null;
		if (pendingCrash != null) {
			message(Component.translatable("blueprintplanner.error.crashed", pendingCrash).withStyle(ChatFormatting.RED), false);
			pendingCrash = null;
		} else if (kicked != null && !isGenericReason(kicked) && isGenericReason(reason)) {
			// the planner server's own reason says more than the client's "Disconnected"
			LOGGER.warn("Planner server dropped the planner player: {}", kicked.getString());
			message(Component.translatable("blueprintplanner.error.kicked", kicked).withStyle(ChatFormatting.RED), false);
		} else {
			message(Component.translatable("blueprintplanner.error.disconnected", reason).withStyle(ChatFormatting.RED), false);
		}
	}

	/** Called before vanilla leaves the real world (disconnect, kick, quitting). */
	public static void onRealWorldLeaving() {
		if (phase == Phase.OFF && server == null) {
			return;
		}
		shutdown(false);
	}

	/** Stops the planner completely and returns to the real world if needed. */
	public static void shutdown(boolean waitForSave) {
		if (isActive()) {
			ContextSwitcher.switchTo(ContextSwitcher.Side.REAL);
		}
		Connection oldConnection = connection;
		connection = null;
		ContextSwitcher.clearBackground();
		if (oldConnection != null) {
			PlannerIsolation.runForPlannerConnection(() -> {
				oldConnection.disconnect(Component.translatable("blueprintplanner.closed"));
				oldConnection.handleDisconnection();
			});
		}
		stopServer();
		if (waitForSave && stoppingServer != null) {
			long deadline = System.currentTimeMillis() + 15000;
			while (!stoppingServer.isShutdown() && System.currentTimeMillis() < deadline) {
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		if (opening != null) {
			opening.thenAccept(opened -> opened.access().safeClose());
			opening = null;
		}
		phase = Phase.OFF;
		worldKey = null;
		pendingCrash = null;
		pendingEntry = null;
		lastEntry = null;
		QUEUED_CHUNKS.clear();
		pendingExitReason = null;
		enterWhenReady = false;
	}

	private static void stopServer() {
		if (server != null) {
			PlannerServer old = server;
			server = null;
			stoppingServer = old;
			old.halt(false);
		}
	}

	private static void fail(Component reason) {
		LOGGER.warn("Planner failed: {}", reason.getString());
		shutdown(false);
		message(reason.copy().withStyle(ChatFormatting.RED), false);
	}

	public static void message(Component text, boolean actionBar) {
		Minecraft mc = Minecraft.getInstance();
		if (actionBar) {
			mc.gui.hud.setOverlayMessage(text, false);
		} else {
			mc.gui.hud.getChat().addClientSystemMessage(text);
		}
	}
}
