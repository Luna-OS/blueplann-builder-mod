package dev.blueprintplanner.compat.mixin.jei;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.ContextSwitcher;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "mezz.jei.fabric.startup.ClientLifecycleHandler", remap = false)
public abstract class JeiClientLifecycleMixin {
	/**
	 * JEI rebuilds itself from Minecraft's current world. It must never restart because the planner loads its world or
	 * logs in (it did, and posted its "server does not have JEI" warning each time), and when you restart it inside the
	 * planner it is built from the real world.
	 */
	@WrapMethod(method = {"stopJei()V", "startJei()V"})
	private void blueprintplanner$realWorldOnly(Operation<Void> original) {
		if (!Minecraft.getInstance().isSameThread()) {
			// a config file watcher thread: only the render thread knows which world is installed
			Minecraft.getInstance().execute(() -> this.blueprintplanner$realWorldOnly(original));
			return;
		}
		if (PlannerIsolation.onPlannerServerThread() || PlannerIsolation.handlingPlannerPacket()) {
			return;
		}
		if (PlannerIsolation.inPlannerClientContext()) {
			ContextSwitcher.runIn(ContextSwitcher.Side.REAL, () -> original.call());
			return;
		}
		original.call();
	}

	/** Also with the Mod shield off: the planner's recipe sync must not replace the real server's recipes in JEI. */
	@Inject(method = "onRecipesSynchronized(Lnet/minecraft/world/item/crafting/RecipeMap;)V", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$keepRealRecipes(RecipeMap recipes, CallbackInfo ci) {
		if (PlannerIsolation.handlingPlannerPacket()) {
			ci.cancel();
		}
	}
}
