package dev.blueprintplanner.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

/**
 * The real world and the planner have separate copies of the data driven registries (enchantments, trims, ...).
 * Things made for one cannot be sent over the other's connection as they are.
 */
public final class PlannerRegistries {
	private PlannerRegistries() {
	}

	/** Render thread: an item from the real world's registries, rebuilt for the planner while it is shown. */
	public static ItemStack toPlanner(ItemStack stack) {
		ClientLevel planner = Minecraft.getInstance().level;
		ClientLevel real = PlannerIsolation.realLevel();
		if (stack.isEmpty() || !PlannerIsolation.inPlannerClientContext() || planner == null || real == null || real == planner) {
			return stack;
		}
		RegistryOps<Tag> from = real.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		RegistryOps<Tag> to = planner.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		return ItemStack.OPTIONAL_CODEC.encodeStart(from, stack)
			.flatMap(tag -> ItemStack.OPTIONAL_CODEC.parse(to, tag))
			.result()
			// something the planner's registries do not know: at least the plain item
			.orElseGet(() -> new ItemStack(stack.getItem(), stack.getCount()));
	}
}
