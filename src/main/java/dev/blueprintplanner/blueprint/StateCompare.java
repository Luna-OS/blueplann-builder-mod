package dev.blueprintplanner.blueprint;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Compares a placed block with the planned one. Only properties the builder controls are compared
 * (orientation, half, delay, comparator mode ...); things like redstone power or fence connections
 * follow from the neighbours and are ignored.
 */
public final class StateCompare {
	public enum Result { CORRECT, WRONG_STATE, WRONG_BLOCK, MISSING, EXTRA }

	private static final List<Property<?>> RELEVANT = List.of(
		BlockStateProperties.FACING,
		BlockStateProperties.HORIZONTAL_FACING,
		BlockStateProperties.FACING_HOPPER,
		BlockStateProperties.AXIS,
		BlockStateProperties.HORIZONTAL_AXIS,
		BlockStateProperties.ORIENTATION,
		BlockStateProperties.ATTACH_FACE,
		BlockStateProperties.HALF,
		BlockStateProperties.DOUBLE_BLOCK_HALF,
		BlockStateProperties.SLAB_TYPE,
		BlockStateProperties.ROTATION_16,
		BlockStateProperties.DELAY,
		BlockStateProperties.MODE_COMPARATOR,
		BlockStateProperties.DOOR_HINGE,
		BlockStateProperties.BED_PART,
		BlockStateProperties.VERTICAL_DIRECTION,
		BlockStateProperties.BELL_ATTACHMENT,
		BlockStateProperties.LAYERS,
		BlockStateProperties.PICKLES,
		BlockStateProperties.EGGS,
		BlockStateProperties.CANDLES,
		BlockStateProperties.NOTE,
		BlockStateProperties.INVERTED,
		BlockStateProperties.HANGING,
		BlockStateProperties.BOTTOM,
		BlockStateProperties.RAIL_SHAPE,
		BlockStateProperties.RAIL_SHAPE_STRAIGHT
	);

	/**
	 * Set by clicking the block after it was placed (or by its neighbours), so a fresh placement cannot have them
	 * yet: Easy Place ignores them when it looks for the right way to place a block.
	 */
	private static final List<Property<?>> SET_AFTER_PLACING = List.of(
		BlockStateProperties.DELAY,
		BlockStateProperties.MODE_COMPARATOR,
		BlockStateProperties.NOTE,
		BlockStateProperties.INVERTED,
		BlockStateProperties.LAYERS,
		BlockStateProperties.PICKLES,
		BlockStateProperties.EGGS,
		BlockStateProperties.CANDLES,
		BlockStateProperties.BOTTOM,
		BlockStateProperties.RAIL_SHAPE,
		BlockStateProperties.RAIL_SHAPE_STRAIGHT
	);

	private StateCompare() {
	}

	public static Result compare(BlockState planned, BlockState actual) {
		if (planned.isAir()) {
			return actual.isAir() ? Result.CORRECT : Result.EXTRA;
		}
		if (actual.isAir() || (actual.canBeReplaced() && !actual.is(planned.getBlock()))) {
			return Result.MISSING;
		}
		if (!actual.is(planned.getBlock())) {
			return Result.WRONG_BLOCK;
		}
		return sameOrientation(planned, actual) ? Result.CORRECT : Result.WRONG_STATE;
	}

	public static boolean sameOrientation(BlockState planned, BlockState actual) {
		for (Property<?> property : RELEVANT) {
			if (differs(planned, actual, property)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether a block placed in this state is on the way to the planned one: same orientation, except for what
	 * can only be set afterwards (repeater delay, comparator mode, a double slab's second half ...).
	 */
	public static boolean placedRight(BlockState planned, BlockState placed) {
		for (Property<?> property : RELEVANT) {
			if (SET_AFTER_PLACING.contains(property)
				|| property == BlockStateProperties.SLAB_TYPE && planned.hasProperty(property) && planned.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
				continue;
			}
			if (differs(planned, placed, property)) {
				return false;
			}
		}
		return true;
	}

	private static boolean differs(BlockState planned, BlockState actual, Property<?> property) {
		return planned.hasProperty(property) && actual.hasProperty(property) && !planned.getValue(property).equals(actual.getValue(property));
	}
}
