package dev.blueprintplanner.blueprint;

import dev.blueprintplanner.planner.PlannerManager;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Area selection like WorldEdit: inside the planner a wooden axe sets the corners (left click = corner 1,
 * right click = corner 2). Everywhere the corner keys select the block you are looking at.
 */
public final class SelectionTool {
	private SelectionTool() {
	}

	public static boolean isTool(ItemStack stack) {
		return stack.is(Items.WOODEN_AXE);
	}

	public static void register() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level.isClientSide() && hand == InteractionHand.MAIN_HAND && PlannerManager.isActive() && isTool(player.getMainHandItem())) {
				setCorner(true, pos);
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && PlannerManager.isActive() && isTool(player.getItemInHand(hand))) {
				if (hand == InteractionHand.MAIN_HAND) {
					setCorner(false, hit.getBlockPos());
				}
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	/** Corner key pressed: use the targeted block (also far away), or the block at the player's feet. */
	public static void setCornerFromCrosshair(boolean first) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		BlockHitResult hit = PlacementTool.aim(mc, ClipContext.Fluid.NONE);
		setCorner(first, hit != null ? hit.getBlockPos() : mc.player.blockPosition());
	}

	public static void setCorner(boolean first, BlockPos pos) {
		Selection selection = Selection.get();
		if (first) {
			selection.setFirst(pos);
		} else {
			selection.setSecond(pos);
		}
		Component message = Component.translatable(first ? "blueprintplanner.selection.first" : "blueprintplanner.selection.second",
			pos.getX(), pos.getY(), pos.getZ());
		if (selection.isComplete()) {
			message = Component.translatable("blueprintplanner.selection.size", message, selection.sizeX(), selection.sizeY(), selection.sizeZ(), selection.volume());
		}
		PlannerManager.message(message, true);
	}
}
