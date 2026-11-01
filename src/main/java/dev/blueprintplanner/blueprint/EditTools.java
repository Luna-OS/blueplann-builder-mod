package dev.blueprintplanner.blueprint;

import dev.blueprintplanner.planner.PlannerManager;
import dev.blueprintplanner.planner.PlannerServer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Simple WorldEdit-style tools for the planner world, driven by buttons instead of commands.
 * Everything runs on the planner server thread; every change can be undone.
 */
public final class EditTools {
	public static final long MAX_VOLUME = 4_000_000L;
	private static final int MAX_UNDO = 30;

	private static final Deque<Snapshot> UNDO = new ArrayDeque<>();
	private static final Deque<Snapshot> REDO = new ArrayDeque<>();
	private static volatile @Nullable Blueprint clipboard;

	private record Snapshot(BlockPos min, Blueprint blocks) {
	}

	private EditTools() {
	}

	public static @Nullable Blueprint clipboard() {
		return clipboard;
	}

	public static void setClipboard(@Nullable Blueprint blueprint) {
		clipboard = blueprint;
	}

	public static boolean canUndo() {
		return !UNDO.isEmpty();
	}

	public static boolean canRedo() {
		return !REDO.isEmpty();
	}

	// ------------------------------------------------------------------ operations

	public static void fill(BlockState state) {
		modifySelection("blueprintplanner.tools.done.fill", (pos, old) -> state);
	}

	public static void replace(Block from, BlockState to) {
		modifySelection("blueprintplanner.tools.done.replace", (pos, old) -> old.is(from) ? to : null);
	}

	public static void clear() {
		modifySelection("blueprintplanner.tools.done.clear", (pos, old) -> old.isAir() ? null : Blocks.AIR.defaultBlockState());
	}

	/** Four vertical side faces. */
	public static void walls(BlockState state) {
		BoundingBox box = Selection.get().box();
		if (box == null) {
			return;
		}
		modifySelection("blueprintplanner.tools.done.walls", (pos, old) ->
			pos.getX() == box.minX() || pos.getX() == box.maxX() || pos.getZ() == box.minZ() || pos.getZ() == box.maxZ() ? state : null);
	}

	/** All six faces (hollow box). */
	public static void shell(BlockState state) {
		BoundingBox box = Selection.get().box();
		if (box == null) {
			return;
		}
		modifySelection("blueprintplanner.tools.done.shell", (pos, old) ->
			pos.getX() == box.minX() || pos.getX() == box.maxX() || pos.getZ() == box.minZ() || pos.getZ() == box.maxZ()
				|| pos.getY() == box.minY() || pos.getY() == box.maxY() ? state : null);
	}

	/** Only the twelve edges. */
	public static void frame(BlockState state) {
		BoundingBox box = Selection.get().box();
		if (box == null) {
			return;
		}
		modifySelection("blueprintplanner.tools.done.frame", (pos, old) -> {
			int edges = (pos.getX() == box.minX() || pos.getX() == box.maxX() ? 1 : 0)
				+ (pos.getY() == box.minY() || pos.getY() == box.maxY() ? 1 : 0)
				+ (pos.getZ() == box.minZ() || pos.getZ() == box.maxZ() ? 1 : 0);
			return edges >= 2 ? state : null;
		});
	}

	public static void copy(boolean cut) {
		BoundingBox box = selectionOrWarn();
		PlannerServer server = PlannerManager.server();
		if (box == null || server == null) {
			return;
		}
		server.execute(() -> {
			ServerLevel level = server.overworld();
			Blueprint copied = Blueprint.capture(level, box, "clipboard");
			clipboard = copied;
			if (cut) {
				pushUndo(level, box);
				setBlocks(level, box, (pos, old) -> old.isAir() ? null : Blocks.AIR.defaultBlockState());
			}
			feedback(Component.translatable(cut ? "blueprintplanner.tools.done.cut" : "blueprintplanner.tools.done.copy", copied.nonAirCount()));
		});
	}

	/** Pastes the clipboard so that its lower north-west corner lands on {@code at}. */
	public static void paste(BlockPos at, boolean includeAir) {
		Blueprint blueprint = clipboard;
		if (blueprint == null) {
			feedback(Component.translatable("blueprintplanner.tools.no_clipboard").withStyle(ChatFormatting.RED));
			return;
		}
		pasteBlueprint(blueprint, at, includeAir, true);
	}

	/** Pastes any blueprint into the planner world (with undo). */
	public static void pasteBlueprint(Blueprint blueprint, BlockPos at, boolean includeAir, boolean selectResult) {
		PlannerServer server = PlannerManager.server();
		if (server == null) {
			return;
		}
		BoundingBox target = new BoundingBox(at.getX(), at.getY(), at.getZ(),
			at.getX() + blueprint.sizeX() - 1, at.getY() + blueprint.sizeY() - 1, at.getZ() + blueprint.sizeZ() - 1);
		server.execute(() -> {
			ServerLevel level = server.overworld();
			pushUndo(level, target);
			blueprint.pasteInto(level, at, includeAir, false);
			feedback(Component.translatable("blueprintplanner.tools.done.paste", blueprint.nonAirCount()));
		});
		if (selectResult) {
			Selection.get().set(target);
		}
	}

	public static void rotateClipboard() {
		Blueprint blueprint = clipboard;
		if (blueprint != null) {
			clipboard = blueprint.transformed(Rotation.CLOCKWISE_90, Mirror.NONE);
			feedback(Component.translatable("blueprintplanner.tools.done.rotate"));
		}
	}

	public static void mirrorClipboard() {
		Blueprint blueprint = clipboard;
		if (blueprint != null) {
			clipboard = blueprint.transformed(Rotation.NONE, Mirror.FRONT_BACK);
			feedback(Component.translatable("blueprintplanner.tools.done.mirror"));
		}
	}

	/** Moves the selected blocks {@code distance} blocks in {@code direction}; the selection follows. */
	public static void move(Direction direction, int distance) {
		BoundingBox box = selectionOrWarn();
		PlannerServer server = PlannerManager.server();
		if (box == null || server == null || distance == 0) {
			return;
		}
		BoundingBox moved = box.moved(direction.getStepX() * distance, direction.getStepY() * distance, direction.getStepZ() * distance);
		BoundingBox affected = BoundingBox.encapsulatingBoxes(List.of(box, moved)).orElse(box);
		server.execute(() -> {
			ServerLevel level = server.overworld();
			pushUndo(level, affected);
			Blueprint blocks = Blueprint.capture(level, box, "move");
			setBlocks(level, box, (pos, old) -> old.isAir() ? null : Blocks.AIR.defaultBlockState());
			blocks.pasteInto(level, new BlockPos(moved.minX(), moved.minY(), moved.minZ()), true, false);
			feedback(Component.translatable("blueprintplanner.tools.done.move", distance));
		});
		Selection.get().set(moved);
	}

	/** Repeats the selection {@code times} times in {@code direction} (great for redstone lines). */
	public static void stack(Direction direction, int times) {
		BoundingBox box = selectionOrWarn();
		PlannerServer server = PlannerManager.server();
		if (box == null || server == null || times <= 0) {
			return;
		}
		int stepX = direction.getStepX() * box.getXSpan();
		int stepY = direction.getStepY() * box.getYSpan();
		int stepZ = direction.getStepZ() * box.getZSpan();
		BoundingBox last = box.moved(stepX * times, stepY * times, stepZ * times);
		BoundingBox affected = BoundingBox.encapsulatingBoxes(List.of(box, last)).orElse(box);
		if ((long) affected.getXSpan() * affected.getYSpan() * affected.getZSpan() > MAX_VOLUME) {
			feedback(Component.translatable("blueprintplanner.tools.too_big", MAX_VOLUME).withStyle(ChatFormatting.RED));
			return;
		}
		server.execute(() -> {
			ServerLevel level = server.overworld();
			pushUndo(level, affected);
			Blueprint blocks = Blueprint.capture(level, box, "stack");
			for (int i = 1; i <= times; i++) {
				blocks.pasteInto(level, new BlockPos(box.minX() + stepX * i, box.minY() + stepY * i, box.minZ() + stepZ * i), true, false);
			}
			feedback(Component.translatable("blueprintplanner.tools.done.stack", times));
		});
	}

	public static void undo() {
		PlannerServer server = PlannerManager.server();
		if (server == null) {
			return;
		}
		server.execute(() -> {
			Snapshot snapshot;
			synchronized (UNDO) {
				snapshot = UNDO.pollFirst();
			}
			if (snapshot == null) {
				feedback(Component.translatable("blueprintplanner.tools.nothing_to_undo"));
				return;
			}
			ServerLevel level = server.overworld();
			Snapshot current = new Snapshot(snapshot.min(), Blueprint.capture(level, boxOf(snapshot), "redo"));
			synchronized (UNDO) {
				REDO.addFirst(current);
			}
			snapshot.blocks().pasteInto(level, snapshot.min(), true, false);
			feedback(Component.translatable("blueprintplanner.tools.done.undo"));
		});
	}

	public static void redo() {
		PlannerServer server = PlannerManager.server();
		if (server == null) {
			return;
		}
		server.execute(() -> {
			Snapshot snapshot;
			synchronized (UNDO) {
				snapshot = REDO.pollFirst();
			}
			if (snapshot == null) {
				feedback(Component.translatable("blueprintplanner.tools.nothing_to_redo"));
				return;
			}
			ServerLevel level = server.overworld();
			Snapshot current = new Snapshot(snapshot.min(), Blueprint.capture(level, boxOf(snapshot), "undo"));
			synchronized (UNDO) {
				UNDO.addFirst(current);
			}
			snapshot.blocks().pasteInto(level, snapshot.min(), true, false);
			feedback(Component.translatable("blueprintplanner.tools.done.redo"));
		});
	}

	/** Forget history when the planner world changes. */
	public static void clearHistory() {
		synchronized (UNDO) {
			UNDO.clear();
			REDO.clear();
		}
	}

	// ------------------------------------------------------------------ helpers

	private static BoundingBox boxOf(Snapshot snapshot) {
		BlockPos min = snapshot.min();
		Blueprint b = snapshot.blocks();
		return new BoundingBox(min.getX(), min.getY(), min.getZ(), min.getX() + b.sizeX() - 1, min.getY() + b.sizeY() - 1, min.getZ() + b.sizeZ() - 1);
	}

	private static @Nullable BoundingBox selectionOrWarn() {
		if (!PlannerManager.isActive()) {
			feedback(Component.translatable("blueprintplanner.tools.planner_only").withStyle(ChatFormatting.RED));
			return null;
		}
		Selection selection = Selection.get();
		if (!selection.isComplete()) {
			feedback(Component.translatable("blueprintplanner.tools.no_selection").withStyle(ChatFormatting.RED));
			return null;
		}
		if (selection.volume() > MAX_VOLUME) {
			feedback(Component.translatable("blueprintplanner.tools.too_big", MAX_VOLUME).withStyle(ChatFormatting.RED));
			return null;
		}
		return selection.box();
	}

	private static void modifySelection(String doneKey, BiFunction<BlockPos, BlockState, @Nullable BlockState> function) {
		BoundingBox box = selectionOrWarn();
		PlannerServer server = PlannerManager.server();
		if (box == null || server == null) {
			return;
		}
		server.execute(() -> {
			ServerLevel level = server.overworld();
			pushUndo(level, box);
			int changed = setBlocks(level, box, function);
			feedback(Component.translatable(doneKey, changed));
		});
	}

	private static void pushUndo(ServerLevel level, BoundingBox box) {
		Snapshot snapshot = new Snapshot(new BlockPos(box.minX(), box.minY(), box.minZ()), Blueprint.capture(level, box, "undo"));
		synchronized (UNDO) {
			UNDO.addFirst(snapshot);
			while (UNDO.size() > MAX_UNDO) {
				UNDO.removeLast();
			}
			REDO.clear();
		}
	}

	/** Same update behaviour as vanilla {@code /fill}: set everything, then notify neighbours. */
	private static int setBlocks(ServerLevel level, BoundingBox box, BiFunction<BlockPos, BlockState, @Nullable BlockState> function) {
		record Changed(BlockPos pos, BlockState oldState) {
		}
		List<Changed> changed = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!level.isInWorldBounds(pos)) {
				continue;
			}
			BlockState old = level.getBlockState(pos);
			BlockState next = function.apply(pos, old);
			if (next == null || next == old) {
				continue;
			}
			if (old.hasBlockEntity()) {
				level.removeBlockEntity(pos);
			}
			if (level.setBlock(pos, next, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS)) {
				changed.add(new Changed(pos.immutable(), old));
			}
		}
		for (Changed c : changed) {
			level.updateNeighboursOnBlockSet(c.pos(), c.oldState());
		}
		return changed.size();
	}

	private static void feedback(Component message) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> PlannerManager.message(message, true));
	}
}
