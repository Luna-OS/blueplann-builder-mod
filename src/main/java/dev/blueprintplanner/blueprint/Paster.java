package dev.blueprintplanner.blueprint;

import dev.blueprintplanner.config.PlannerConfig;
import dev.blueprintplanner.planner.PlannerManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Builds a placement in the real world instantly: directly in singleplayer, with commands on servers where you are op. */
public final class Paster {
	private static final Deque<String> QUEUE = new ArrayDeque<>();
	private static int queuedTotal;

	private Paster() {
	}

	public static boolean canPaste() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = PlannerManager.realPlayer();
		return mc.getSingleplayerServer() != null
			|| player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	public static void paste(Placement placement) {
		Minecraft mc = Minecraft.getInstance();
		Blueprint blueprint = placement.blueprint();
		ClientLevel realLevel = PlannerManager.realLevel();
		if (blueprint == null || realLevel == null) {
			return;
		}
		// in layer mode only the shown layer is built
		Integer onlyY = PlacementManager.layerMode() && PlacementManager.selected() == placement ? PlacementManager.layer() : null;
		IntegratedServer sp = mc.getSingleplayerServer();
		if (sp != null) {
			var dimension = realLevel.dimension();
			BlockPos origin = placement.origin();
			boolean includeAir = placement.includeAir;
			sp.execute(() -> {
				ServerLevel level = sp.getLevel(dimension);
				if (level != null) {
					blueprint.pasteInto(level, origin, includeAir, false, onlyY);
				}
			});
			PlannerManager.message(Component.translatable("blueprintplanner.paste.done", blueprint.nonAirCount()), true);
			return;
		}
		if (!canPaste()) {
			PlannerManager.message(Component.translatable("blueprintplanner.paste.no_permission").withStyle(ChatFormatting.RED), false);
			return;
		}
		List<String> commands = buildCommands(blueprint, placement.origin(), placement.includeAir, realLevel, onlyY);
		if (commands.isEmpty()) {
			PlannerManager.message(Component.translatable("blueprintplanner.materials.complete"), true);
			return;
		}
		QUEUE.addAll(commands);
		queuedTotal = QUEUE.size();
		PlannerManager.message(Component.translatable("blueprintplanner.paste.started", commands.size()), false);
	}

	public static void cancel() {
		QUEUE.clear();
	}

	public static boolean isRunning() {
		return !QUEUE.isEmpty();
	}

	public static void tick() {
		if (QUEUE.isEmpty()) {
			return;
		}
		ClientPacketListener connection = PlannerManager.realListener();
		if (connection == null) {
			QUEUE.clear();
			return;
		}
		int perTick = PlannerConfig.get().pasteCommandsPerTick;
		for (int i = 0; i < perTick && !QUEUE.isEmpty(); i++) {
			connection.sendCommand(QUEUE.poll());
		}
		int done = queuedTotal - QUEUE.size();
		if (QUEUE.isEmpty()) {
			PlannerManager.message(Component.translatable("blueprintplanner.paste.finished", queuedTotal), true);
		} else if (!PlannerManager.isActive()) {
			PlannerManager.message(Component.translatable("blueprintplanner.paste.progress", done, queuedTotal), true);
		}
	}

	/**
	 * Bottom-up, full blocks before attached ones, identical neighbours along X merged into /fill.
	 * Blocks that are already correct in the world are skipped.
	 */
	static List<String> buildCommands(Blueprint blueprint, BlockPos origin, boolean includeAir, ClientLevel level, @Nullable Integer onlyY) {
		record Cell(int x, int y, int z, BlockState state, CompoundTag be) {
		}
		List<Cell> cells = new ArrayList<>();
		BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
		blueprint.forEach((x, y, z, state, be) -> {
			if (state.isAir() && !includeAir || onlyY != null && origin.getY() + y != onlyY) {
				return;
			}
			worldPos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
			if (be == null && level.isLoaded(worldPos) && level.getBlockState(worldPos) == state) {
				return;
			}
			cells.add(new Cell(x, y, z, state, be));
		});
		cells.sort(Comparator.<Cell>comparingInt(c -> c.y)
			.thenComparingInt(c -> c.state.isAir() ? 0 : c.state.canOcclude() ? 1 : 2)
			.thenComparingInt(c -> c.z)
			.thenComparingInt(c -> c.x));

		List<String> commands = new ArrayList<>();
		int i = 0;
		while (i < cells.size()) {
			Cell start = cells.get(i);
			int end = i;
			while (start.be == null && end + 1 < cells.size()) {
				Cell next = cells.get(end + 1);
				if (next.y != start.y || next.z != start.z || next.x != cells.get(end).x + 1 || next.state != start.state || next.be != null) {
					break;
				}
				end++;
			}
			int wx = origin.getX() + start.x;
			int wy = origin.getY() + start.y;
			int wz = origin.getZ() + start.z;
			String state = BlockStateParser.serialize(start.state);
			if (end > i) {
				commands.add("fill %d %d %d %d %d %d %s".formatted(wx, wy, wz, origin.getX() + cells.get(end).x, wy, wz, state));
			} else if (start.be != null && !start.be.isEmpty()) {
				commands.add("setblock %d %d %d %s%s".formatted(wx, wy, wz, state, start.be));
			} else {
				commands.add("setblock %d %d %d %s".formatted(wx, wy, wz, state));
			}
			i = end + 1;
		}
		return commands;
	}
}
