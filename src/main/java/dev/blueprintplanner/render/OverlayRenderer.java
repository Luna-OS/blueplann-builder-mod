package dev.blueprintplanner.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.blueprintplanner.blueprint.Blueprint;
import dev.blueprintplanner.blueprint.EditTools;
import dev.blueprintplanner.blueprint.Placement;
import dev.blueprintplanner.blueprint.PlacementManager;
import dev.blueprintplanner.blueprint.PlacementTool;
import dev.blueprintplanner.blueprint.Selection;
import dev.blueprintplanner.blueprint.StateCompare;
import dev.blueprintplanner.config.PlannerConfig;
import dev.blueprintplanner.planner.PlannerManager;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Selection box, placement holograms (ghost blocks + error boxes), clipboard preview and redstone power labels. */
public final class OverlayRenderer {
	private static final float GHOST_SCALE = 0.8F;
	/** A placement that follows the crosshair. */
	private static final int CARRY_COLOR = 0xFFFFDD33;
	private static final int CARRY_FILL = 0x20FFDD33;
	private static final List<MovingBlockRenderState> GHOSTS = new ArrayList<>();

	private OverlayRenderer() {
	}

	// ------------------------------------------------------------------ gizmos (main thread, every frame)

	public static void emitGizmos() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		PlacementTool.onFrame(mc);

		Selection selection = Selection.get();
		if (selection.first() != null) {
			Gizmos.cuboid(selection.first(), 0.02F, GizmoStyle.strokeAndFill(0xFFFF5555, 2.0F, 0x30FF5555));
		}
		if (selection.second() != null) {
			Gizmos.cuboid(selection.second(), 0.02F, GizmoStyle.strokeAndFill(0xFF5599FF, 2.0F, 0x305599FF));
		}
		BoundingBox box = selection.box();
		if (box != null) {
			Gizmos.cuboid(aabb(box).inflate(0.01), GizmoStyle.stroke(0xFFFFAA00, 2.5F));
		}

		Placement carried = PlacementTool.carried();
		if (PlannerManager.isActive()) {
			if (carried != null && carried.box() != null) {
				Gizmos.cuboid(aabb(carried.box()).inflate(0.02), GizmoStyle.strokeAndFill(CARRY_COLOR, 2.5F, CARRY_FILL));
			}
			emitClipboardPreview(mc);
			if (PlannerConfig.get().showRedstonePower) {
				emitRedstonePower(mc);
			}
			return;
		}

		Placement selected = PlacementManager.selected();
		Placement hovered = PlacementTool.hovered();
		for (Placement placement : PlacementManager.placements()) {
			if (!placement.visible) {
				continue;
			}
			BoundingBox placementBox = placement.box();
			if (placementBox == null) {
				continue;
			}
			AABB outline = aabb(placementBox).inflate(0.02);
			if (PlacementManager.layerMode() && placement == selected && placement != carried) {
				// the shown layer as a flat frame, the rest of the hologram only faintly
				int layer = PlacementManager.layer();
				if (layer >= placementBox.minY() && layer <= placementBox.maxY()) {
					AABB slice = new AABB(placementBox.minX(), layer, placementBox.minZ(), placementBox.maxX() + 1, layer + 1, placementBox.maxZ() + 1).inflate(0.03);
					Gizmos.cuboid(slice, GizmoStyle.strokeAndFill(0xFFFFAA00, 2.5F, 0x14FFAA00));
				}
				Gizmos.cuboid(outline, GizmoStyle.stroke(0x6055FFFF, 1.0F));
				continue;
			}
			if (placement == carried) {
				Gizmos.cuboid(outline, GizmoStyle.strokeAndFill(CARRY_COLOR, 2.5F, CARRY_FILL));
			} else if (placement == hovered) {
				Gizmos.cuboid(outline, GizmoStyle.strokeAndFill(0xFFFFFFFF, 2.5F, 0x18FFFFFF));
			} else {
				int color = placement == selected ? 0xFF55FFFF : 0xFF3388AA;
				Gizmos.cuboid(outline, GizmoStyle.stroke(color, placement == selected ? 2.5F : 1.5F));
			}
		}

		if (!PlannerConfig.get().ghostShowWrongBlocks) {
			return;
		}
		for (PlacementManager.Status status : PlacementManager.statuses()) {
			switch (status.result()) {
				case WRONG_BLOCK -> Gizmos.cuboid(status.pos(), 0.01F, GizmoStyle.strokeAndFill(0xFFFF2222, 2.0F, 0x50FF2222));
				case WRONG_STATE -> Gizmos.cuboid(status.pos(), 0.01F, GizmoStyle.strokeAndFill(0xFFFFAA00, 2.0F, 0x40FFAA00));
				case EXTRA -> Gizmos.cuboid(status.pos(), 0.01F, GizmoStyle.strokeAndFill(0xFFFF55FF, 2.0F, 0x40FF55FF));
				case CORRECT -> Gizmos.cuboid(status.pos(), 0.005F, GizmoStyle.fill(0x2022FF22));
				default -> {
				}
			}
		}
	}

	private static void emitClipboardPreview(Minecraft mc) {
		Blueprint clipboard = EditTools.clipboard();
		if (clipboard == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		if (!mc.player.getMainHandItem().isEmpty() && !isSelectionTool(mc)) {
			return;
		}
		BlockPos at = hit.getBlockPos().relative(hit.getDirection());
		AABB preview = new AABB(at.getX(), at.getY(), at.getZ(), at.getX() + clipboard.sizeX(), at.getY() + clipboard.sizeY(), at.getZ() + clipboard.sizeZ());
		Gizmos.cuboid(preview.inflate(0.01), GizmoStyle.strokeAndFill(0x8055FF55, 1.5F, 0x1055FF55));
	}

	private static boolean isSelectionTool(Minecraft mc) {
		return dev.blueprintplanner.blueprint.SelectionTool.isTool(mc.player.getMainHandItem());
	}

	private static void emitRedstonePower(Minecraft mc) {
		BlockPos center = mc.player.blockPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int r = 10;
		for (int x = -r; x <= r; x++) {
			for (int y = -6; y <= 6; y++) {
				for (int z = -r; z <= r; z++) {
					pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
					BlockState state = mc.level.getBlockState(pos);
					if (state.is(Blocks.REDSTONE_WIRE)) {
						int power = state.getValue(BlockStateProperties.POWER);
						int color = power == 0 ? 0xFF888888 : 0xFFFF0000 | (power * 12) << 8;
						Gizmos.billboardText(String.valueOf(power), Vec3.atLowerCornerWithOffset(pos, 0.5, 0.25, 0.5), TextGizmo.Style.forColorAndCentered(color).withScale(0.4F))
							.setAlwaysOnTop();
					} else if (state.is(Blocks.COMPARATOR) || state.is(Blocks.REPEATER)) {
						String label = state.is(Blocks.REPEATER) ? state.getValue(BlockStateProperties.DELAY) + "t" : state.getValue(BlockStateProperties.MODE_COMPARATOR).getSerializedName().substring(0, 1);
						Gizmos.billboardText(label, Vec3.atLowerCornerWithOffset(pos, 0.5, 0.4, 0.5), TextGizmo.Style.forColorAndCentered(0xFFFFFF55).withScale(0.35F))
							.setAlwaysOnTop();
					}
				}
			}
		}
	}

	private static AABB aabb(BoundingBox box) {
		return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
	}

	// ------------------------------------------------------------------ ghost blocks

	/** Main thread, after the level was extracted: decide which ghost blocks to draw this frame. */
	public static void extractGhosts(LevelExtractionContext context) {
		GHOSTS.clear();
		if (PlannerManager.isActive()) {
			return;
		}
		ClientLevel level = context.level();
		for (PlacementManager.Status status : PlacementManager.statuses()) {
			if (status.result() != StateCompare.Result.MISSING || status.planned().isAir()) {
				continue;
			}
			MovingBlockRenderState ghost = new MovingBlockRenderState();
			ghost.randomSeedPos = status.pos();
			ghost.blockPos = status.pos();
			ghost.blockState = status.planned();
			ghost.biome = level.getBiome(status.pos());
			ghost.cardinalLighting = level.cardinalLighting();
			ghost.lightEngine = level.getLightEngine();
			GHOSTS.add(ghost);
		}
	}

	public static void submitGhosts(LevelRenderContext context) {
		if (GHOSTS.isEmpty()) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		for (MovingBlockRenderState ghost : GHOSTS) {
			BlockPos pos = ghost.blockPos;
			poseStack.pushPose();
			poseStack.translate(pos.getX() - camera.x + 0.5, pos.getY() - camera.y + 0.5, pos.getZ() - camera.z + 0.5);
			poseStack.scale(GHOST_SCALE, GHOST_SCALE, GHOST_SCALE);
			poseStack.translate(-0.5, -0.5, -0.5);
			context.submitNodeCollector().submitMovingBlock(poseStack, ghost, 0);
			poseStack.popPose();
		}
	}
}
