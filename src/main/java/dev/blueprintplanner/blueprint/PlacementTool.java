package dev.blueprintplanner.blueprint;

import dev.blueprintplanner.BlueprintPlanner;
import dev.blueprintplanner.config.PlannerConfig;
import dev.blueprintplanner.planner.PlannerManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Moving holograms with the mouse instead of buttons.
 *
 * <p>With a stick in the main hand (real world, at least one placement): right click picks up the placement you
 * look at (or the selected one), it then follows the crosshair until the next right click. Left click selects the
 * placement you look at. Sneak + mouse wheel pushes the selected placement one block along your view.
 *
 * <p>"Place" in the library starts the same carrying for a new blueprint (with any item in hand), so you aim at the
 * spot first; inside the planner "→ Planner" does it too and pastes the blueprint where you click.
 */
public final class PlacementTool {
	/** How far you can aim with a hologram (far beyond the normal block reach, big builds need room). */
	public static final double RANGE = 128.0;

	private static @Nullable Carry carry;
	/** The placement under the crosshair while the stick is in hand (highlighted). */
	private static @Nullable Placement hovered;
	/** A click was used by the tool: its button does nothing else (repeated use, block breaking) until released. */
	private static boolean swallowUntilRelease;

	/** A placement following the crosshair. */
	private static final class Carry {
		final Placement placement;
		/** Pasted into the planner world on drop instead of staying a hologram. */
		final boolean intoPlanner;
		/** Picked up from here; null for a placement that is new (cancel removes it). */
		final @Nullable BlockPos previousOrigin;
		final Rotation previousRotation;
		final Mirror previousMirror;
		final boolean previousVisible;
		/** Selected before a new placement was added (selected again when it is thrown away). */
		final int previousSelected;
		/** Where the blueprint was saved, if that was in this world (the pick block key puts it there). */
		final @Nullable BlockPos home;
		/** Extra height chosen with sneak + mouse wheel. */
		int lift;
		boolean aimed;

		Carry(Placement placement, boolean intoPlanner, boolean isNew, int previousSelected, @Nullable BlockPos home) {
			this.placement = placement;
			this.intoPlanner = intoPlanner;
			this.previousOrigin = isNew ? null : placement.origin();
			this.previousRotation = placement.rotation;
			this.previousMirror = placement.mirror;
			this.previousVisible = placement.visible;
			this.previousSelected = previousSelected;
			this.home = home;
		}

		/** The placement as it should be stored while it is still being carried. */
		@Nullable Placement committed() {
			return this.previousOrigin == null ? null : this.placement.copyAt(this.previousOrigin, this.previousRotation, this.previousMirror, this.previousVisible);
		}
	}

	private PlacementTool() {
	}

	public static void register() {
		// clicks are taken in onStartTick; these only keep held buttons from also using or breaking things
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> blocking(player, level) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> blocking(player, level) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> blocking(player, level) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> blocking(player, level) ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	private static boolean blocking(Player player, Level level) {
		return level.isClientSide() && player == Minecraft.getInstance().player && (swallowUntilRelease || isActive(Minecraft.getInstance()));
	}

	// ------------------------------------------------------------------ state

	/** True while the stick tool or a carried placement takes the mouse buttons. */
	public static boolean isActive(Minecraft mc) {
		return carry != null || stickInHand(mc);
	}

	private static boolean stickInHand(Minecraft mc) {
		LocalPlayer player = mc.player;
		return player != null && PlannerConfig.get().placementStick && !PlannerManager.isActive() && !player.isSpectator()
			&& player.getMainHandItem().is(Items.STICK) && !PlacementManager.placements().isEmpty();
	}

	public static @Nullable Placement carried() {
		return carry != null ? carry.placement : null;
	}

	/**
	 * What to save instead of {@code placements}: a carried hologram counts as not moved yet (a new one as not
	 * there yet), so quitting or crashing mid-move keeps the last dropped state.
	 */
	public static List<Placement> forSaving(List<Placement> placements) {
		Carry current = carry;
		if (current == null || current.intoPlanner || !placements.contains(current.placement)) {
			return placements;
		}
		List<Placement> result = new ArrayList<>(placements.size());
		for (Placement placement : placements) {
			if (placement != current.placement) {
				result.add(placement);
			} else if (current.committed() != null) {
				result.add(current.committed());
			}
		}
		return result;
	}

	/** True while a blueprint follows the crosshair to be pasted into the planner. */
	public static boolean carryingIntoPlanner() {
		return carry != null && carry.intoPlanner;
	}

	public static @Nullable Placement hovered() {
		return hovered;
	}

	// ------------------------------------------------------------------ starting

	/** Library "Place" in the real world (not as spectator): the new hologram follows the crosshair until you click. */
	public static void startPlacing(BlueprintLibrary.Entry entry) {
		cancel(false);
		Blueprint blueprint = load(entry);
		if (blueprint == null) {
			return;
		}
		int previousSelected = PlacementManager.selectedIndex();
		Placement placement = new Placement(entry.fileName(), blueprint, BlueprintActions.defaultOrigin(blueprint));
		// carried before it is added, so it is not saved until it is dropped
		Carry newCarry = new Carry(placement, false, true, previousSelected, BlueprintActions.homeOrigin(blueprint));
		carry = newCarry;
		PlacementManager.add(placement);
		start(newCarry);
	}

	/** Library "→ Planner" inside the planner: aim, click, and the blueprint is pasted there. */
	public static void startPasting(BlueprintLibrary.Entry entry) {
		cancel(false);
		Blueprint blueprint = load(entry);
		if (blueprint == null) {
			return;
		}
		Placement placement = new Placement(entry.fileName(), blueprint, BlueprintActions.defaultOrigin(blueprint));
		start(new Carry(placement, true, true, PlacementManager.selectedIndex(), BlueprintActions.homeOrigin(blueprint)));
	}

	/** Picks up an existing placement (stick right click, or "Move" in the placement list). */
	public static void pickUp(Placement placement) {
		Minecraft mc = Minecraft.getInstance();
		if (PlannerManager.isActive() || mc.player == null || mc.player.isSpectator()) {
			return;
		}
		cancel(false);
		int index = PlacementManager.placements().indexOf(placement);
		if (index < 0) {
			return;
		}
		int previousSelected = PlacementManager.selectedIndex();
		PlacementManager.select(index);
		Carry newCarry = new Carry(placement, false, false, previousSelected, null);
		// shown while you move it; a hidden one is hidden again if you cancel
		placement.visible = true;
		start(newCarry);
	}

	private static void start(Carry newCarry) {
		carry = newCarry;
		follow(Minecraft.getInstance());
		PlannerManager.message(Component.translatable("blueprintplanner.tool.carrying", newCarry.placement.name).withStyle(ChatFormatting.YELLOW), true);
	}

	private static @Nullable Blueprint load(BlueprintLibrary.Entry entry) {
		Blueprint blueprint = BlueprintLibrary.loadByFileName(entry.fileName());
		if (blueprint == null) {
			PlannerManager.message(Component.translatable("blueprintplanner.load.failed", entry.displayName()).withStyle(ChatFormatting.RED), false);
		}
		return blueprint;
	}

	// ------------------------------------------------------------------ ticking and input

	/** Start of the client tick, before Minecraft handles the mouse buttons. */
	public static void onStartTick(Minecraft mc) {
		if (swallowUntilRelease && !mc.options.keyUse.isDown() && !mc.options.keyAttack.isDown()) {
			swallowUntilRelease = false;
		}
		if (carry != null && !carry.intoPlanner && !PlacementManager.placements().contains(carry.placement)) {
			// the placements of another world/dimension were loaded meanwhile
			carry = null;
		}
		if (carry != null && (mc.level == null || mc.player == null || mc.player.isSpectator() || carry.intoPlanner != PlannerManager.isActive()
			|| mc.gui.screen() != null)) {
			// world left, planner entered/left, spectator mode (it opens containers without asking anyone),
			// or a menu opened (Esc cancels like in most games)
			cancel(mc.level != null && mc.gui.screen() != null);
		}
		if (mc.player == null || mc.gui.screen() != null || !isActive(mc)) {
			return;
		}
		// normally done every frame; here too so a click always uses the current aim
		follow(mc);
		while (isActive(mc) && mc.options.keyAttack.consumeClick()) {
			swallowUntilRelease = true;
			leftClick(mc);
		}
		while (isActive(mc) && mc.options.keyUse.consumeClick()) {
			swallowUntilRelease = true;
			rightClick(mc);
		}
		while (carry != null && carry.home != null && mc.options.keyPickItem.consumeClick()) {
			goHome();
		}
	}

	/** Every frame: the carried placement follows the crosshair, the stick highlights what it points at. */
	public static void onFrame(Minecraft mc) {
		follow(mc);
		hovered = carry == null && stickInHand(mc) ? lookedAt(mc) : null;
	}

	private static void leftClick(Minecraft mc) {
		if (carry != null) {
			cancel(true);
			return;
		}
		Placement target = lookedAt(mc);
		if (target == null) {
			PlannerManager.message(Component.translatable("blueprintplanner.tool.nothing").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		PlacementManager.select(PlacementManager.placements().indexOf(target));
		PlannerManager.message(Component.translatable("blueprintplanner.tool.selected", target.name), true);
	}

	private static void rightClick(Minecraft mc) {
		if (carry != null) {
			drop();
			return;
		}
		Placement target = lookedAt(mc);
		if (target == null) {
			// fetch the selected one, unless you hid it
			Placement selected = PlacementManager.selected();
			target = selected != null && selected.visible ? selected : null;
		}
		if (target != null) {
			pickUp(target);
		} else {
			PlannerManager.message(Component.translatable("blueprintplanner.tool.nothing").withStyle(ChatFormatting.GRAY), true);
		}
	}

	/** Sneak + mouse wheel (from MouseHandlerMixin). Returns true when the wheel was used here. */
	public static boolean onScroll(int steps) {
		Minecraft mc = Minecraft.getInstance();
		if (steps == 0 || mc.player == null || !mc.player.isShiftKeyDown() || !isActive(mc)) {
			return false;
		}
		if (carry != null) {
			carry.lift += steps;
			follow(mc);
			PlannerManager.message(Component.translatable("blueprintplanner.tool.lift", signed(carry.lift)), true);
			return true;
		}
		Placement placement = PlacementManager.selected();
		if (placement == null || !placement.visible) {
			PlannerManager.message(Component.translatable("blueprintplanner.tool.none_selected").withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		Direction direction = Direction.getApproximateNearest(mc.player.getViewVector(1.0F));
		placement.move(direction.getStepX() * steps, direction.getStepY() * steps, direction.getStepZ() * steps);
		PlacementManager.changed();
		BlockPos origin = placement.origin();
		PlannerManager.message(Component.translatable("blueprintplanner.tool.pushed", placement.name, origin.getX(), origin.getY(), origin.getZ()), true);
		return true;
	}

	/** The rotate key: turns the carried placement, otherwise the selected one if it is shown (around its middle). */
	public static void rotate() {
		Placement placement = carry != null ? carry.placement : PlannerManager.isActive() ? null : PlacementManager.selected();
		if (placement == null || !placement.visible) {
			return;
		}
		placement.rotateClockwise();
		if (carry != null) {
			follow(Minecraft.getInstance());
		} else {
			PlacementManager.changed();
		}
		PlannerManager.message(Component.translatable("blueprintplanner.placement.rotated", placement.rotation.getSerializedName()), true);
	}

	// ------------------------------------------------------------------ carrying

	private static void follow(Minecraft mc) {
		if (carry == null || mc.player == null || mc.level == null) {
			return;
		}
		Blueprint blueprint = carry.placement.blueprint();
		BlockHitResult hit = aim(mc, ClipContext.Fluid.SOURCE_ONLY);
		if (blueprint == null || hit == null) {
			// aiming at the sky: the hologram waits where it is
			carry.aimed = false;
			return;
		}
		carry.aimed = true;
		BlockPos origin = anchoredOrigin(hit, blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ()).above(carry.lift);
		if (!origin.equals(carry.placement.origin())) {
			carry.placement.setOrigin(origin);
			if (!carry.intoPlanner) {
				PlacementManager.moved();
			}
		}
	}

	/**
	 * Where a box of this size goes when you point at a block face: in front of the face, centered on the
	 * crosshair. On the ground it stands on the block, under a ceiling it hangs from it.
	 */
	static BlockPos anchoredOrigin(BlockHitResult hit, int sizeX, int sizeY, int sizeZ) {
		Direction face = hit.getDirection();
		BlockPos anchor = hit.getBlockPos().relative(face);
		int x = switch (face) {
			case EAST -> anchor.getX();
			case WEST -> anchor.getX() - sizeX + 1;
			default -> anchor.getX() - sizeX / 2;
		};
		int z = switch (face) {
			case SOUTH -> anchor.getZ();
			case NORTH -> anchor.getZ() - sizeZ + 1;
			default -> anchor.getZ() - sizeZ / 2;
		};
		int y = face == Direction.DOWN ? anchor.getY() - sizeY + 1 : anchor.getY();
		return new BlockPos(x, y, z);
	}

	/** Pick block key while carrying a library blueprint: back to the spot it was saved from, and drop it there. */
	private static void goHome() {
		Carry current = carry;
		if (current == null || current.home == null) {
			return;
		}
		current.placement.rotation = Rotation.NONE;
		current.placement.mirror = Mirror.NONE;
		current.placement.invalidate();
		current.placement.setOrigin(current.home);
		drop();
	}

	private static void drop() {
		Carry done = carry;
		carry = null;
		if (done == null) {
			return;
		}
		Placement placement = done.placement;
		BlockPos origin = placement.origin();
		if (done.intoPlanner) {
			Blueprint blueprint = placement.blueprint();
			if (blueprint != null) {
				EditTools.pasteBlueprint(blueprint, origin, placement.includeAir, true);
			}
			return;
		}
		PlacementManager.changed();
		PlannerManager.message(Component.translatable("blueprintplanner.tool.dropped", placement.name, origin.getX(), origin.getY(), origin.getZ())
			.withStyle(ChatFormatting.GREEN), true);
	}

	/** Puts a picked up placement back, or removes a new one. */
	public static void cancel(boolean announce) {
		Carry done = carry;
		carry = null;
		if (done == null) {
			return;
		}
		if (!done.intoPlanner) {
			if (done.previousOrigin == null) {
				PlacementManager.remove(done.placement);
			} else {
				done.placement.rotation = done.previousRotation;
				done.placement.mirror = done.previousMirror;
				done.placement.visible = done.previousVisible;
				done.placement.invalidate();
				done.placement.setOrigin(done.previousOrigin);
			}
			PlacementManager.select(done.previousSelected);
			PlacementManager.changed();
		}
		if (announce) {
			PlannerManager.message(Component.translatable("blueprintplanner.tool.cancelled").withStyle(ChatFormatting.GRAY), true);
		}
	}

	// ------------------------------------------------------------------ aiming

	/** The block you look at, up to {@link #RANGE} blocks away. */
	public static @Nullable BlockHitResult aim(Minecraft mc, ClipContext.Fluid fluid) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return null;
		}
		float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 end = eye.add(player.getViewVector(partialTick).scale(RANGE));
		BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, fluid, player));
		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
	}

	/** The visible placement under the crosshair: the nearest one the view ray enters, else the smallest one you stand in. */
	public static @Nullable Placement lookedAt(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			return null;
		}
		float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 end = eye.add(player.getViewVector(partialTick).scale(RANGE));
		Placement nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		Placement around = null;
		long aroundVolume = Long.MAX_VALUE;
		for (Placement placement : PlacementManager.placements()) {
			BoundingBox box = placement.visible ? placement.box() : null;
			if (box == null) {
				continue;
			}
			AABB aabb = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
			if (aabb.contains(eye)) {
				long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
				if (volume < aroundVolume) {
					around = placement;
					aroundVolume = volume;
				}
				continue;
			}
			Optional<Vec3> entry = aabb.clip(eye, end);
			if (entry.isPresent() && entry.get().distanceToSqr(eye) < nearestDistance) {
				nearest = placement;
				nearestDistance = entry.get().distanceToSqr(eye);
			}
		}
		return nearest != null ? nearest : around;
	}

	// ------------------------------------------------------------------ HUD

	/** Help lines for the HUD while the tool is in use, empty otherwise. */
	public static List<Component> hudLines(Minecraft mc) {
		List<Component> lines = new ArrayList<>();
		Component rotate = BlueprintPlanner.rotatePlacement.getTranslatedKeyMessage();
		if (carry != null) {
			BlockPos origin = carry.placement.origin();
			lines.add(Component.translatable(carry.intoPlanner ? "blueprintplanner.tool.hud.carry_planner" : "blueprintplanner.tool.hud.carry",
				carry.placement.name, origin.getX(), origin.getY(), origin.getZ()));
			lines.add(Component.translatable("blueprintplanner.tool.hud.carry_keys", rotate, signed(carry.lift)));
			if (carry.home != null) {
				lines.add(Component.translatable("blueprintplanner.tool.hud.home", mc.options.keyPickItem.getTranslatedKeyMessage()));
			}
			if (!carry.aimed) {
				lines.add(Component.translatable("blueprintplanner.tool.hud.aim"));
			}
		} else if (stickInHand(mc)) {
			Placement selected = PlacementManager.selected();
			lines.add(Component.translatable("blueprintplanner.tool.hud.stick", selected != null ? selected.name : "-"));
			lines.add(Component.translatable("blueprintplanner.tool.hud.stick_keys", rotate));
		}
		return lines;
	}

	private static String signed(int value) {
		return value > 0 ? "+" + value : String.valueOf(value);
	}
}
