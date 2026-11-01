package probe;

import dev.blueprintplanner.planner.PlannerManager;
import dev.blueprintplanner.planner.PlannerServer;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;

/**
 * Stands in for "some other mod": it lives outside the trusted packages, so the planner isolation must hide the
 * planner server and the planner connection from it, while it still sees the real worlds.
 */
public final class ForeignModProbe {
	public static final AtomicInteger REAL_SERVER_STARTS = new AtomicInteger();
	public static final AtomicInteger PLANNER_SERVER_EVENTS = new AtomicInteger();
	public static final AtomicInteger REAL_JOINS = new AtomicInteger();
	public static final AtomicInteger PLANNER_JOINS = new AtomicInteger();
	public static final AtomicInteger PLANNER_DISCONNECTS = new AtomicInteger();
	/** Command trees built by any server; the planner's world loading must not show up here. */
	public static final AtomicInteger COMMAND_REGISTRATIONS = new AtomicInteger();

	private ForeignModProbe() {
	}

	/** The default item component changed through DefaultItemComponentEvents.MODIFY is still there. */
	public static boolean featherKeepsModdedStackSize() {
		return Items.FEATHER.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 64) == 16;
	}

	public static void register() {
		// like a mod that changes a vanilla item: the planner's datapack load must not take this away from the real world
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(Items.FEATHER, builder -> builder.set(DataComponents.MAX_STACK_SIZE, 16)));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> COMMAND_REGISTRATIONS.incrementAndGet());
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			if (server instanceof PlannerServer) {
				PLANNER_SERVER_EVENTS.incrementAndGet();
			} else {
				REAL_SERVER_STARTS.incrementAndGet();
			}
		});
		ServerLevelEvents.LOAD.register((server, level) -> {
			if (server instanceof PlannerServer) {
				PLANNER_SERVER_EVENTS.incrementAndGet();
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server instanceof PlannerServer) {
				PLANNER_SERVER_EVENTS.incrementAndGet();
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (server instanceof PlannerServer) {
				PLANNER_SERVER_EVENTS.incrementAndGet();
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (server instanceof PlannerServer) {
				PLANNER_SERVER_EVENTS.incrementAndGet();
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (PlannerManager.isPlannerConnection(handler.getConnection())) {
				PLANNER_JOINS.incrementAndGet();
			} else {
				REAL_JOINS.incrementAndGet();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (PlannerManager.isPlannerConnection(handler.getConnection())) {
				PLANNER_DISCONNECTS.incrementAndGet();
			}
		});
	}

	public static String describe() {
		return "realServerStarts=%d plannerServerEvents=%d realJoins=%d plannerJoins=%d plannerDisconnects=%d commandRegistrations=%d".formatted(
			REAL_SERVER_STARTS.get(), PLANNER_SERVER_EVENTS.get(), REAL_JOINS.get(), PLANNER_JOINS.get(), PLANNER_DISCONNECTS.get(),
			COMMAND_REGISTRATIONS.get());
	}
}
