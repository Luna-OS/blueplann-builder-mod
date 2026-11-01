package dev.blueprintplanner.compat;

import dev.blueprintplanner.config.PlannerConfig;
import dev.blueprintplanner.mixin.ClientLevelAccessor;
import dev.blueprintplanner.planner.ContextSwitcher;
import dev.blueprintplanner.planner.PlannerManager;
import dev.blueprintplanner.planner.PlannerServer;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ServerboundConfigurationChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Keeps other mods from mistaking the planner for the real world.
 *
 * <p>The planner is a second {@link PlannerServer} plus a second client connection inside the same game. Mods
 * that listen to "server started", "world loaded" or "joined a server" would otherwise think the player switched
 * worlds (Distant Horizons for example replaced its whole LOD world and crashed). Fabric event listeners of other
 * mods therefore do not run for the planner server, nor for the connection events of the planner connection.
 * Mods that hook in with mixins instead get dedicated guards in {@code dev.blueprintplanner.compat.mixin}.
 */
public final class PlannerIsolation {
	/** Listeners from these packages always run: Fabric API itself, vanilla and this mod. */
	private static final String[] TRUSTED_PACKAGES = {"net.fabricmc.", "net.minecraft.", "com.mojang.", "dev.blueprintplanner."};

	private static final Set<Thread> SERVER_THREADS = ConcurrentHashMap.newKeySet();
	/** Set while Fabric fires a connection event of the planner connection (on whatever thread that happens). */
	private static final ThreadLocal<Boolean> PLANNER_CONNECTION_EVENT = new ThreadLocal<>();
	/** Set while the network thread dispatches a planner packet, or while the planner connection is being closed. */
	private static final ThreadLocal<Boolean> PLANNER_CONNECTION_WORK = new ThreadLocal<>();
	/** Planner server work outside its main thread: world loading on worker threads, its network threads. */
	private static final ThreadLocal<Boolean> PLANNER_SERVER_WORK = new ThreadLocal<>();
	/**
	 * The "Mod shield" setting, fixed for one planner session: switching it while the planner runs would leave other
	 * mods with unpaired events (a stop without a start).
	 */
	private static volatile boolean sessionIsolated = true;
	/** The real world as seen from other threads; the render thread keeps it current. */
	private static volatile @Nullable ClientLevel realLevel;
	private static volatile @Nullable ClientPacketListener realListener;

	private PlannerIsolation() {
	}

	/** A planner session starts (world loading, server, connection): the "Mod shield" setting is fixed until it ends. */
	public static void beginSession() {
		sessionIsolated = PlannerConfig.get().isolateFromOtherMods;
	}

	/** Called by the planner server on its own thread for as long as it runs. */
	public static void enterServerThread() {
		SERVER_THREADS.add(Thread.currentThread());
	}

	public static void leaveServerThread() {
		SERVER_THREADS.remove(Thread.currentThread());
	}

	/** True on the planner server's main thread and for planner server work on other threads. */
	public static boolean onPlannerServerThread() {
		return !SERVER_THREADS.isEmpty() && SERVER_THREADS.contains(Thread.currentThread()) || PLANNER_SERVER_WORK.get() != null;
	}

	/** Runs work of the planner server that happens on a shared thread (see {@link #onPlannerServerThread()}). */
	public static <T> T runAsPlannerServer(Supplier<T> work) {
		if (PLANNER_SERVER_WORK.get() != null) {
			return work.get();
		}
		PLANNER_SERVER_WORK.set(Boolean.TRUE);
		try {
			return work.get();
		} finally {
			PLANNER_SERVER_WORK.remove();
		}
	}

	/** Executor for loading the planner world: its tasks count as planner server work (mods' reload hooks stay out). */
	public static Executor plannerServerExecutor(Executor executor) {
		return task -> executor.execute(() -> runAsPlannerServer(() -> {
			task.run();
			return null;
		}));
	}

	/**
	 * True while the client is running planner code: handling planner packets, ticking the planner connection or
	 * showing the planner. Only meaningful on the render thread.
	 */
	public static boolean inPlannerClientContext() {
		return ContextSwitcher.installed() == ContextSwitcher.Side.PLANNER && Minecraft.getInstance().isSameThread();
	}

	/** Cheap check for hot paths: true while any part of the planner (server or connection) exists. */
	public static boolean plannerExists() {
		return !SERVER_THREADS.isEmpty() || PlannerManager.plannerConnection() != null;
	}

	/**
	 * True while a packet of the planner connection is handled: on the network thread (where other mods' packet
	 * hooks run first) and on the render thread. Also true while the planner connection is being closed.
	 */
	public static boolean handlingPlannerPacket() {
		return PLANNER_CONNECTION_WORK.get() != null || PlannerManager.isHandlingPlannerPacket() && Minecraft.getInstance().isSameThread();
	}

	/** Runs work that belongs to the planner connection (see {@link #handlingPlannerPacket()}). */
	public static void runForPlannerConnection(Runnable work) {
		if (PLANNER_CONNECTION_WORK.get() != null) {
			work.run();
			return;
		}
		PLANNER_CONNECTION_WORK.set(Boolean.TRUE);
		try {
			work.run();
		} finally {
			PLANNER_CONNECTION_WORK.remove();
		}
	}

	/** Render thread: refreshes the real-world references other threads read. */
	public static void refreshRealWorld() {
		realLevel = PlannerManager.realLevel();
		realListener = PlannerManager.realListener();
	}

	/** The real world's level from any thread (worker threads must not read the swapped Minecraft fields). */
	public static @Nullable ClientLevel realLevel() {
		return Minecraft.getInstance().isSameThread() ? PlannerManager.realLevel() : realLevel;
	}

	/** The real world's connection from any thread. */
	public static @Nullable ClientPacketListener realListener() {
		return Minecraft.getInstance().isSameThread() ? PlannerManager.realListener() : realListener;
	}

	/** True for planner levels, both the server side and the client side. Safe to call from any thread. */
	public static boolean isPlannerLevel(@Nullable Object level) {
		if (level instanceof ServerLevel serverLevel) {
			return serverLevel.getServer() instanceof PlannerServer;
		}
		if (level instanceof ClientLevel clientLevel) {
			ClientPacketListener connection = ((ClientLevelAccessor) clientLevel).blueprintplanner$getConnection();
			return connection != null && PlannerManager.isPlannerConnection(connection.getConnection());
		}
		return false;
	}

	// ------------------------------------------------------------------ Fabric events (called from FabricEventMixin)

	/** Whether {@code event} should only reach trusted listeners right now. */
	public static boolean shouldIsolate(Event<?> event) {
		// runs for every event invocation in the game, so the cheap checks come first
		boolean planner = onPlannerServerThread()
			|| PLANNER_CONNECTION_EVENT.get() != null && ConnectionEvents.ALL.contains(event)
			|| PacketEvents.ALL.contains(event) && handlingPlannerPacket();
		return planner && sessionIsolated && !GlobalStateEvents.ALL.contains(event);
	}

	/** Called by Fabric's network addons around firing a connection event (see FabricClientNetworkAddonMixin). */
	public static <T> T fireConnectionEvent(@Nullable Connection connection, Supplier<T> fire) {
		if (!PlannerManager.isPlannerConnection(connection) || PLANNER_CONNECTION_EVENT.get() != null) {
			return fire.get();
		}
		PLANNER_CONNECTION_EVENT.set(Boolean.TRUE);
		try {
			return fire.get();
		} finally {
			PLANNER_CONNECTION_EVENT.remove();
		}
	}

	/** Returns {@code listeners} without the untrusted ones, or the same array if all of them are trusted. */
	@SuppressWarnings("unchecked")
	public static <T> T[] trustedListeners(T[] listeners) {
		int trusted = 0;
		for (T listener : listeners) {
			if (isTrusted(listener)) {
				trusted++;
			}
		}
		if (trusted == listeners.length) {
			return listeners;
		}
		T[] result = (T[]) Array.newInstance(listeners.getClass().getComponentType(), trusted);
		int i = 0;
		for (T listener : listeners) {
			if (isTrusted(listener)) {
				result[i++] = listener;
			}
		}
		return result;
	}

	private static boolean isTrusted(Object listener) {
		// lambdas are named after the class that defines them, so this also covers method references
		String name = listener.getClass().getName();
		for (String trusted : TRUSTED_PACKAGES) {
			if (name.startsWith(trusted)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Events through which mods repair state shared by the whole game. A planner datapack load rebinds the default
	 * components of the (global) items to vanilla ones and fires MODIFY so that mods put their changes back; without
	 * it the real world's items lose them.
	 */
	private static final class GlobalStateEvents {
		static final Set<Event<?>> ALL = Collections.newSetFromMap(new IdentityHashMap<>());

		static {
			Collections.addAll(ALL, DefaultItemComponentEvents.MODIFY);
		}
	}

	/**
	 * Fired while a packet is handled: tags and synced recipes of the planner connection describe the planner's world,
	 * mods keep the real world's (JEI rebuilt itself from planner recipes, for example).
	 */
	private static final class PacketEvents {
		static final Set<Event<?>> ALL = Collections.newSetFromMap(new IdentityHashMap<>());

		static {
			Collections.addAll(ALL, CommonLifecycleEvents.TAGS_LOADED, ClientRecipeSynchronizedEvent.EVENT);
		}
	}

	/** Events that mods use to track "the" server connection; the planner connection must stay invisible there. */
	private static final class ConnectionEvents {
		static final Set<Event<?>> ALL = Collections.newSetFromMap(new IdentityHashMap<>());

		static {
			Collections.addAll(ALL,
				ClientLoginConnectionEvents.INIT, ClientLoginConnectionEvents.QUERY_START, ClientLoginConnectionEvents.DISCONNECT,
				ClientConfigurationConnectionEvents.INIT, ClientConfigurationConnectionEvents.START,
				ClientConfigurationConnectionEvents.COMPLETE, ClientConfigurationConnectionEvents.READY,
				ClientConfigurationConnectionEvents.DISCONNECT,
				ClientPlayConnectionEvents.INIT, ClientPlayConnectionEvents.JOIN, ClientPlayConnectionEvents.DISCONNECT,
				// mods use the channel lists to decide whether "the server" supports them
				ServerboundConfigurationChannelEvents.REGISTER, ServerboundConfigurationChannelEvents.UNREGISTER,
				ServerboundPlayChannelEvents.REGISTER, ServerboundPlayChannelEvents.UNREGISTER);
		}
	}
}
