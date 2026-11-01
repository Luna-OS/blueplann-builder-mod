package dev.blueprintplanner.mixin;

import dev.blueprintplanner.planner.ContextSwitcher;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	public @Nullable ClientLevel level;

	@Shadow
	@Final
	public GameRenderer gameRenderer;

	/** Leaving the real world (disconnect button, kick, quitting): bring the real world back first. */
	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
	private void blueprintplanner$beforeDisconnect(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
		PlannerManager.onRealWorldLeaving();
	}

	@Inject(method = "clearClientLevel", at = @At("HEAD"))
	private void blueprintplanner$beforeClearLevel(Screen screen, CallbackInfo ci) {
		PlannerManager.onRealWorldLeaving();
	}

	/** While handling packets of the background world only the field may change, renderers stay untouched. */
	@Inject(method = "setLevel", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$setLevelInBackground(ClientLevel newLevel, CallbackInfo ci) {
		if (ContextSwitcher.inTemporarySwitch()) {
			this.level = newLevel;
			ci.cancel();
		}
	}

	@Inject(method = "setCameraEntity", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$setCameraInBackground(@Nullable Entity entity, CallbackInfo ci) {
		if (ContextSwitcher.inTemporarySwitch()) {
			if (entity != null) {
				this.gameRenderer.mainCamera().setEntity(entity);
			}
			ci.cancel();
		}
	}

	@Inject(method = "setScreenAndShow", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$noScreensFromBackground(Screen screen, CallbackInfo ci) {
		if (ContextSwitcher.inTemporarySwitch()) {
			ci.cancel();
		}
	}
}
