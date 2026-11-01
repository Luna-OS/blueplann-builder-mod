package dev.blueprintplanner.compat.mixin.xaeromap;

import com.mojang.logging.LogUtils;
import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.map.events.ClientEvents", remap = false)
public abstract class XaeroWorldMapClientEventsMixin {
	/**
	 * The world map is the real world's. Opened in the planner it crashed (no minimap session there), and its actions
	 * (teleport, dimension switch) would change the real map's settings or send chat to the real server.
	 */
	@Inject(method = "handleGuiOpen(Lnet/minecraft/client/gui/screens/Screen;)Lnet/minecraft/client/gui/screens/Screen;", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$noMapInPlanner(Screen screen, CallbackInfoReturnable<Screen> cir) {
		if (screen != null && PlannerIsolation.inPlannerClientContext() && "xaero.map.gui.GuiMap".equals(screen.getClass().getName())) {
			LogUtils.getLogger().debug("Xaero's world map refused inside the planner");
			PlannerManager.message(Component.translatable("blueprintplanner.compat.xaero_map"), true);
			cir.setReturnValue(null);
		}
	}
}
