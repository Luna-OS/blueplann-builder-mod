package dev.blueprintplanner.planner;

import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.compat.bobby.BobbyCompat;
import dev.blueprintplanner.compat.ldl.LdlCompat;
import dev.blueprintplanner.mixin.MinecraftInvoker;
import dev.blueprintplanner.util.ChunkRenderHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Swaps the per-world state of {@link Minecraft} between the real world and the planner world.
 *
 * <p>There are two kinds of switches:
 * <ul>
 *     <li>a <b>full switch</b> ({@link #switchTo}) changes what the player sees and controls; renderers,
 *     particles and sounds are re-pointed to the new level.</li>
 *     <li>a <b>temporary switch</b> ({@link #runIn}) only swaps the fields for the duration of a callback.
 *     It is used to process packets and connection ticks of the world that is currently in the background,
 *     because vanilla packet handlers read and write {@code minecraft.level}/{@code minecraft.player}.</li>
 * </ul>
 */
public final class ContextSwitcher {
	public enum Side { REAL, PLANNER }

	private static final boolean BOBBY = FabricLoader.getInstance().isModLoaded("bobby");
	private static final boolean LAMBDYNLIGHTS = FabricLoader.getInstance().isModLoaded("lambdynlights");

	private static Side active = Side.REAL;
	/** The context that is not currently installed in {@link Minecraft}. */
	private static ClientContext stored = ClientContext.empty();

	private static @Nullable Side temp;
	private static @Nullable ClientContext tempSaved;
	private static int epoch;

	private ContextSwitcher() {
	}

	public static Side active() {
		return active;
	}

	/** Side whose fields are currently installed in {@link Minecraft} (may differ from {@link #active()} during a temporary switch). */
	public static Side installed() {
		return temp != null ? temp : active;
	}

	public static boolean inTemporarySwitch() {
		return temp != null;
	}

	/** Returns the current state of {@code side}, wherever it lives right now. */
	public static ClientContext contextOf(Side side) {
		if (side == installed()) {
			return ClientContext.capture(Minecraft.getInstance());
		}
		if (temp != null && side == active) {
			return tempSaved;
		}
		return stored;
	}

	/** Resets everything to "real world only", dropping any planner state. */
	public static void reset() {
		active = Side.REAL;
		stored = ClientContext.empty();
		temp = null;
		tempSaved = null;
		epoch++;
	}

	public static void runIn(Side side, Runnable runnable) {
		if (side == installed()) {
			runnable.run();
			return;
		}

		if (temp != null) {
			// A nested switch to the active side: install the saved active fields for the duration.
			Minecraft mc = Minecraft.getInstance();
			Side previousTemp = temp;
			ClientContext previousSaved = tempSaved;
			stored = ClientContext.capture(mc);
			previousSaved.applyFields(mc);
			temp = null;
			tempSaved = null;
			int myEpoch = epoch;
			try {
				runnable.run();
			} finally {
				if (epoch == myEpoch) {
					tempSaved = ClientContext.capture(mc);
					stored.applyFields(mc);
					temp = previousTemp;
				}
			}
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		ClientContext saved = ClientContext.capture(mc);
		stored.applyFields(mc);
		temp = side;
		tempSaved = saved;
		int myEpoch = epoch;
		try {
			runnable.run();
		} finally {
			if (epoch == myEpoch) {
				stored = ClientContext.capture(mc);
				saved.applyFields(mc);
				temp = null;
				tempSaved = null;
			}
			// the handler may have replaced the real level (respawn, dimension change)
			PlannerIsolation.refreshRealWorld();
		}
	}

	/** Makes {@code side} the visible and controlled world. */
	public static void switchTo(Side side) {
		Minecraft mc = Minecraft.getInstance();
		if (temp != null) {
			// Normalize: put the active side's fields back before doing a real switch.
			stored = ClientContext.capture(mc);
			tempSaved.applyFields(mc);
			temp = null;
			tempSaved = null;
			epoch++;
		}

		if (side == active) {
			return;
		}

		ClientContext target = stored;
		stored = ClientContext.capture(mc);
		target.applyFields(mc);
		// F3 caches the chunk under the player; both worlds share coordinates, so drop it
		mc.gui.hud.clearCache();
		active = side;
		epoch++;
		PlannerIsolation.refreshRealWorld();

		if (target.level != null) {
			((MinecraftInvoker) mc).blueprintplanner$updateLevelInEngines(target.level, true);
			if (LAMBDYNLIGHTS) {
				LdlCompat.onWorldSwitch(target.level);
			}
			mc.setCameraEntity(target.camera != null ? target.camera : target.player);
			ChunkRenderHelper.reannounceLoadedChunks(target.level);
			if (BOBBY) {
				BobbyCompat.reannounceFakeChunks(target.level);
			}
		}
	}

	/** Called when the background side's state is no longer valid (connection closed). */
	public static void clearBackground() {
		if (temp != null) {
			Minecraft mc = Minecraft.getInstance();
			tempSaved.applyFields(mc);
			temp = null;
			tempSaved = null;
			epoch++;
		}
		stored = ClientContext.empty();
	}
}
