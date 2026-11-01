package dev.blueprintplanner.compat.mixin.svc;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blueprintplanner.planner.ContextSwitcher;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "de.maxhenkel.voicechat.voice.client.ClientPlayerStateManager", remap = false)
public abstract class SvcClientPlayerStateManagerMixin {
	/**
	 * The real server's answer to "join group" arrives while the planner is shown; screens cannot change during that
	 * moment, so the group screen is opened right after it.
	 */
	@WrapOperation(method = "lambda$new$4(Lnet/minecraft/client/player/LocalPlayer;Lde/maxhenkel/voicechat/net/JoinedGroupPacket;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
	private void blueprintplanner$openAfterSwitch(Gui gui, Screen screen, Operation<Void> original) {
		if (ContextSwitcher.inTemporarySwitch() && PlannerManager.isActive()) {
			Minecraft.getInstance().schedule(() -> gui.setScreen(screen));
		} else {
			original.call(gui, screen);
		}
	}
}
