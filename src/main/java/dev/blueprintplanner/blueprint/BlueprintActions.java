package dev.blueprintplanner.blueprint;

import com.mojang.logging.LogUtils;
import dev.blueprintplanner.planner.PlannerManager;
import dev.blueprintplanner.planner.PlannerServer;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** High level blueprint operations used by the screens and key bindings. */
public final class BlueprintActions {
	private static final Logger LOGGER = LogUtils.getLogger();

	private BlueprintActions() {
	}

	/**
	 * Saves the current selection. Inside the planner the blocks are read on the planner server (complete
	 * block entity data); in the real world they are read from what the client knows.
	 */
	public static void saveSelection(String name, boolean createPlacement, Consumer<@Nullable Path> done) {
		Minecraft mc = Minecraft.getInstance();
		BoundingBox box = Selection.get().box();
		if (box == null) {
			PlannerManager.message(Component.translatable("blueprintplanner.tools.no_selection").withStyle(ChatFormatting.RED), true);
			done.accept(null);
			return;
		}
		String author = mc.getUser().getName();
		String world = Objects.requireNonNullElse(PlacementManager.worldKey(), "");
		PlannerServer server = PlannerManager.server();
		if (PlannerManager.isActive() && server != null) {
			server.execute(() -> {
				Blueprint blueprint = Blueprint.capture(server.overworld(), box, name);
				mc.execute(() -> finishSave(blueprint, author, world, createPlacement, done));
			});
		} else if (mc.level != null) {
			finishSave(Blueprint.capture(mc.level, box, name), author, world, createPlacement, done);
		}
	}

	private static void finishSave(Blueprint blueprint, String author, String world, boolean createPlacement, Consumer<@Nullable Path> done) {
		blueprint.setAuthor(author);
		blueprint.setWorld(world);
		try {
			Path file = BlueprintLibrary.save(blueprint);
			PlannerManager.message(Component.translatable("blueprintplanner.save.done", blueprint.name(), blueprint.nonAirCount()), false);
			if (createPlacement && blueprint.origin() != null) {
				PlacementManager.add(new Placement(file.getFileName().toString(), blueprint, blueprint.origin()));
			}
			done.accept(file);
		} catch (Exception e) {
			LOGGER.error("Could not save blueprint", e);
			PlannerManager.message(Component.translatable("blueprintplanner.save.failed", e.getMessage()).withStyle(ChatFormatting.RED), false);
			done.accept(null);
		}
	}

	/** Shows a library blueprint as a hologram in the real world. */
	public static @Nullable Placement placeInRealWorld(BlueprintLibrary.Entry entry) {
		Blueprint blueprint = BlueprintLibrary.loadByFileName(entry.fileName());
		if (blueprint == null) {
			PlannerManager.message(Component.translatable("blueprintplanner.load.failed", entry.displayName()).withStyle(ChatFormatting.RED), false);
			return null;
		}
		Placement placement = new Placement(entry.fileName(), blueprint, defaultOrigin(blueprint));
		PlacementManager.add(placement);
		PlannerManager.message(Component.translatable("blueprintplanner.place.done", blueprint.name()), true);
		return placement;
	}

	/**
	 * Puts a library blueprint into the selected area: in the real world as a new hologram, inside the planner
	 * it is pasted there (undoable).
	 */
	public static void placeIntoSelection(BlueprintLibrary.Entry entry) {
		Blueprint blueprint = BlueprintLibrary.loadByFileName(entry.fileName());
		if (blueprint == null) {
			PlannerManager.message(Component.translatable("blueprintplanner.load.failed", entry.displayName()).withStyle(ChatFormatting.RED), false);
			return;
		}
		Placement placement = new Placement(entry.fileName(), blueprint, BlockPos.ZERO);
		if (!fitIntoSelection(placement)) {
			return;
		}
		if (PlannerManager.isActive()) {
			Blueprint turned = placement.blueprint();
			if (turned != null) {
				EditTools.pasteBlueprint(turned, placement.origin(), false, true);
			}
		} else {
			PlacementManager.add(placement);
		}
	}

	/**
	 * Moves a placement into the selection: standing on its floor, centered on the other two axes. If it only fits
	 * when turned by 90°, it is turned. With only one corner set, that block is the spot.
	 */
	public static boolean fitIntoSelection(Placement placement) {
		Selection selection = Selection.get();
		BoundingBox area = selection.box();
		if (area == null) {
			BlockPos corner = selection.first() != null ? selection.first() : selection.second();
			if (corner == null) {
				PlannerManager.message(Component.translatable("blueprintplanner.tools.no_selection").withStyle(ChatFormatting.RED), true);
				return false;
			}
			area = BoundingBox.fromCorners(corner, corner);
		}
		Blueprint blueprint = placement.blueprint();
		if (blueprint == null) {
			return false;
		}
		boolean fits = blueprint.sizeX() == area.getXSpan() && blueprint.sizeZ() == area.getZSpan();
		boolean fitsTurned = blueprint.sizeX() == area.getZSpan() && blueprint.sizeZ() == area.getXSpan();
		if (!fits && fitsTurned) {
			placement.rotateClockwise();
			blueprint = placement.blueprint();
		}
		placement.setOrigin(new BlockPos(area.minX() + Math.floorDiv(area.getXSpan() - blueprint.sizeX(), 2), area.minY(),
			area.minZ() + Math.floorDiv(area.getZSpan() - blueprint.sizeZ(), 2)));
		PlacementManager.changed();
		BlockPos origin = placement.origin();
		boolean exact = blueprint.sizeX() == area.getXSpan() && blueprint.sizeY() == area.getYSpan() && blueprint.sizeZ() == area.getZSpan();
		PlannerManager.message(exact
			? Component.translatable("blueprintplanner.tool.into_selection", placement.name, origin.getX(), origin.getY(), origin.getZ())
			: Component.translatable("blueprintplanner.tool.into_selection_size", placement.name, blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ(),
				area.getXSpan(), area.getYSpan(), area.getZSpan()), true);
		return true;
	}

	/** Where the blueprint was saved from, if that was in this world (planner and real world share coordinates). */
	public static @Nullable BlockPos homeOrigin(Blueprint blueprint) {
		return blueprint.origin() != null && !blueprint.world().isEmpty() && blueprint.world().equals(PlacementManager.worldKey()) ? blueprint.origin() : null;
	}

	/** Original position if the blueprint was made in this world, otherwise in front of the player. */
	public static BlockPos defaultOrigin(Blueprint blueprint) {
		Minecraft mc = Minecraft.getInstance();
		BlockPos home = homeOrigin(blueprint);
		if (home != null) {
			return home;
		}
		if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			return hit.getBlockPos().relative(hit.getDirection());
		}
		return mc.player != null ? mc.player.blockPosition().relative(mc.player.getDirection(), 2) : BlockPos.ZERO;
	}
}
