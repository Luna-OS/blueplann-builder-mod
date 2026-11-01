package dev.blueprintplanner.blueprint;

import com.mojang.logging.LogUtils;
import dev.blueprintplanner.config.PlannerConfig;
import dev.blueprintplanner.mixin.BlockItemInvoker;
import dev.blueprintplanner.planner.PlannerManager;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Survival building helper: when you place a block into a hologram position, the block gets the planned
 * orientation automatically (the needed look direction and clicked face are worked out and sent along
 * with the placement). Placing the wrong block into a planned position is blocked.
 */
public final class EasyPlace {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final float[] PITCHES = {0.0F, 70.0F, -70.0F};
	/** Look directions in 22.5° steps: the four main directions first (well inside a facing), then the ones between. */
	private static final int[] YAW_STEPS = {0, 4, 8, 12, 2, 6, 10, 14, 1, 3, 5, 7, 9, 11, 13, 15};
	private static boolean placing;

	private record Solution(float yaw, float pitch, BlockHitResult hit) {
	}

	private EasyPlace() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (placing || !level.isClientSide() || !PlannerConfig.get().easyPlace || PlannerManager.isActive()
				|| !(player instanceof LocalPlayer localPlayer) || !(level instanceof ClientLevel clientLevel)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!(stack.getItem() instanceof BlockItem blockItem)) {
				return InteractionResult.PASS;
			}
			BlockPos clicked = hit.getBlockPos();
			BlockPos target = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
			BlockState planned = PlacementManager.plannedAt(target);
			if (planned == null) {
				return InteractionResult.PASS;
			}
			if (!level.getBlockState(target).canBeReplaced()) {
				return InteractionResult.FAIL;
			}
			if (!planned.is(blockItem.getBlock())) {
				PlannerManager.message(Component.translatable("blueprintplanner.easyplace.wrong", planned.getBlock().getName()).withStyle(ChatFormatting.RED), true);
				return InteractionResult.FAIL;
			}
			Solution solution = solve(localPlayer, clientLevel, target, planned, hand, stack, blockItem);
			if (solution == null) {
				return InteractionResult.PASS;
			}
			place(localPlayer, hand, solution);
			return InteractionResult.FAIL;
		});
	}

	private static void place(LocalPlayer player, InteractionHand hand, Solution solution) {
		Minecraft mc = Minecraft.getInstance();
		LOGGER.debug("Easy Place: yaw {} pitch {} click {} {} (look was {} {})", solution.yaw(), solution.pitch(), solution.hit().getBlockPos(),
			solution.hit().getDirection(), player.getYRot(), player.getXRot());
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		boolean rotate = Math.abs(yaw - solution.yaw()) > 0.01F || Math.abs(pitch - solution.pitch()) > 0.01F;
		placing = true;
		try {
			// sent even when the look already fits: turning of this tick only reaches the server after the click
			player.connection.send(new ServerboundMovePlayerPacket.Rot(solution.yaw(), solution.pitch(), player.onGround(), player.horizontalCollision));
			if (rotate) {
				player.setYRot(solution.yaw());
				player.setXRot(solution.pitch());
			}
			mc.gameMode.useItemOn(player, hand, solution.hit());
		} finally {
			if (rotate) {
				player.setYRot(yaw);
				player.setXRot(pitch);
				player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision));
			}
			placing = false;
		}
		player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false);
	}

	/** Tries look directions, clicked faces and click heights until vanilla placement yields the planned state. */
	private static @Nullable Solution solve(LocalPlayer player, ClientLevel level, BlockPos target, BlockState planned, InteractionHand hand, ItemStack stack, BlockItem item) {
		float originalYaw = player.getYRot();
		float originalPitch = player.getXRot();
		float originalYawO = player.yRotO;
		float originalPitchO = player.xRotO;
		try {
			// Prefer the current look direction, then the 16 compass directions.
			for (int pass = 0; pass < 2; pass++) {
				for (BlockHitResult hit : candidateHits(level, target)) {
					if (pass == 0) {
						if (matches(player, level, planned, hand, stack, item, hit, originalYaw, originalPitch)) {
							return new Solution(originalYaw, originalPitch, hit);
						}
						continue;
					}
					for (float pitch : PITCHES) {
						for (int step : YAW_STEPS) {
							float yaw = step * 22.5F;
							if (matches(player, level, planned, hand, stack, item, hit, yaw, pitch)) {
								return new Solution(yaw, pitch, hit);
							}
						}
					}
				}
			}
			return null;
		} finally {
			player.setYRot(originalYaw);
			player.setXRot(originalPitch);
			player.yRotO = originalYawO;
			player.xRotO = originalPitchO;
		}
	}

	private static boolean matches(LocalPlayer player, ClientLevel level, BlockState planned, InteractionHand hand, ItemStack stack, BlockItem item,
		BlockHitResult hit, float yaw, float pitch) {
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.yRotO = yaw;
		player.xRotO = pitch;
		BlockPlaceContext context = new BlockPlaceContext(level, player, hand, stack, hit);
		if (!context.canPlace()) {
			return false;
		}
		BlockPlaceContext updated = item.updatePlacementContext(context);
		if (updated == null) {
			return false;
		}
		BlockState state = ((BlockItemInvoker) item).blueprintplanner$getPlacementState(updated);
		return state != null && state.is(planned.getBlock()) && StateCompare.placedRight(planned, state);
	}

	/**
	 * Clicks on solid neighbours first (normal building), clicks on the target position itself last
	 * (needed for orientations that no neighbour face can produce).
	 */
	private static List<BlockHitResult> candidateHits(ClientLevel level, BlockPos target) {
		List<BlockHitResult> hits = new ArrayList<>();
		for (Direction face : Direction.values()) {
			BlockPos support = target.relative(face.getOpposite());
			if (!level.getBlockState(support).canBeReplaced()) {
				addFaceHits(hits, support, face);
			}
		}
		for (Direction face : Direction.values()) {
			addFaceHits(hits, target, face);
		}
		return hits;
	}

	private static void addFaceHits(List<BlockHitResult> hits, BlockPos clickedBlock, Direction face) {
		Vec3 center = Vec3.atCenterOf(clickedBlock).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		if (face.getAxis().isHorizontal()) {
			hits.add(new BlockHitResult(center.add(0, -0.25, 0), face, clickedBlock, false));
			hits.add(new BlockHitResult(center.add(0, 0.25, 0), face, clickedBlock, false));
		} else {
			hits.add(new BlockHitResult(center, face, clickedBlock, false));
		}
	}
}
