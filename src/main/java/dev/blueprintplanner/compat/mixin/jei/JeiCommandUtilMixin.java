package dev.blueprintplanner.compat.mixin.jei;

import dev.blueprintplanner.compat.PlannerRegistries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "mezz.jei.gui.util.CommandUtil", remap = false)
public abstract class JeiCommandUtilMixin {
	/**
	 * JEI's item list belongs to the real world. Its enchanted books, trims etc. point at the real world's registries,
	 * which the planner connection cannot encode (it closed the planner); the cheat-mode item is rebuilt for the planner.
	 */
	@ModifyVariable(method = {"giveStack(Lnet/minecraft/world/item/ItemStack;Lmezz/jei/gui/util/GiveAmount;Lmezz/jei/common/config/GiveMode;)V",
		"setHotbarStack(Lnet/minecraft/world/item/ItemStack;I)V"}, at = @At("HEAD"), argsOnly = true)
	private ItemStack blueprintplanner$forPlanner(ItemStack stack) {
		return PlannerRegistries.toPlanner(stack);
	}
}
