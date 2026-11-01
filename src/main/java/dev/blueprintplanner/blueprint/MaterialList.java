package dev.blueprintplanner.blueprint;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/** Items needed to build a placement, compared with what is already built and what the player carries. */
public final class MaterialList {
	public record Line(Item item, int total, int missing, int inInventory) {
	}

	private MaterialList() {
	}

	public static List<Line> compute(Placement placement, @Nullable Level level, @Nullable Inventory inventory) {
		return compute(placement, level, inventory, null);
	}

	/** With {@code onlyY} set, just the blocks of that height (layer mode). */
	public static List<Line> compute(Placement placement, @Nullable Level level, @Nullable Inventory inventory, @Nullable Integer onlyY) {
		Object2IntMap<Item> total = new Object2IntLinkedOpenHashMap<>();
		Object2IntMap<Item> missing = new Object2IntLinkedOpenHashMap<>();
		for (Placement.Target target : placement.targets()) {
			if (onlyY != null && target.pos().getY() != onlyY) {
				continue;
			}
			BlockState planned = target.state();
			Item item = itemFor(planned);
			if (item == Items.AIR) {
				continue;
			}
			int count = countFor(planned);
			total.mergeInt(item, count, Integer::sum);
			if (level == null || !level.isLoaded(target.pos()) || StateCompare.compare(planned, level.getBlockState(target.pos())) != StateCompare.Result.CORRECT) {
				missing.mergeInt(item, count, Integer::sum);
			}
		}

		List<Line> lines = new ArrayList<>();
		for (Object2IntMap.Entry<Item> entry : total.object2IntEntrySet()) {
			Item item = entry.getKey();
			lines.add(new Line(item, entry.getIntValue(), missing.getInt(item), inventory != null ? countInInventory(inventory, item) : 0));
		}
		lines.sort(Comparator.comparingInt(Line::missing).reversed().thenComparing(l -> l.item().getDescriptionId()));
		return lines;
	}

	public static Item itemFor(BlockState state) {
		if (state.isAir()) {
			return Items.AIR;
		}
		// Second halves of two-block structures are placed together with the first half.
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return Items.AIR;
		}
		if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
			return Items.AIR;
		}
		Block block = state.getBlock();
		if (block == Blocks.REDSTONE_WIRE) {
			return Items.REDSTONE;
		}
		if (block == Blocks.WATER) {
			return Items.WATER_BUCKET;
		}
		if (block == Blocks.LAVA) {
			return Items.LAVA_BUCKET;
		}
		if (block == Blocks.PISTON_HEAD || block == Blocks.MOVING_PISTON || block == Blocks.NETHER_PORTAL || block == Blocks.END_PORTAL) {
			return Items.AIR;
		}
		return block.asItem();
	}

	public static int countFor(BlockState state) {
		if (state.getBlock() instanceof SlabBlock && state.hasProperty(BlockStateProperties.SLAB_TYPE)
			&& state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
			return 2;
		}
		for (var property : List.of(BlockStateProperties.CANDLES, BlockStateProperties.PICKLES, BlockStateProperties.EGGS, BlockStateProperties.LAYERS)) {
			if (state.hasProperty(property)) {
				return state.getValue(property);
			}
		}
		return 1;
	}

	public static int countInInventory(Inventory inventory, Item item) {
		int count = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}
}
