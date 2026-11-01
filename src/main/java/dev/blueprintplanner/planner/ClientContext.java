package dev.blueprintplanner.planner;

import dev.blueprintplanner.mixin.OptionsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Snapshot of all per-world fields of {@link Minecraft}. The client keeps two of these alive at the
 * same time: the real world (multiplayer server / singleplayer) and the planner world.
 */
public final class ClientContext {
	public @Nullable ClientLevel level;
	public @Nullable LocalPlayer player;
	public @Nullable MultiPlayerGameMode gameMode;
	public @Nullable Entity camera;
	public @Nullable HitResult hitResult;
	public @Nullable Entity crosshairPickEntity;
	public int serverRenderDistance;

	public static ClientContext empty() {
		return new ClientContext();
	}

	public static ClientContext capture(Minecraft mc) {
		ClientContext ctx = new ClientContext();
		ctx.level = mc.level;
		ctx.player = mc.player;
		ctx.gameMode = mc.gameMode;
		ctx.camera = mc.getCameraEntity();
		ctx.hitResult = mc.hitResult;
		ctx.crosshairPickEntity = mc.crosshairPickEntity;
		ctx.serverRenderDistance = ((OptionsAccessor) mc.options).blueprintplanner$getServerRenderDistance();
		return ctx;
	}

	/** Writes the fields back into {@link Minecraft} without touching renderers, sounds or screens. */
	public void applyFields(Minecraft mc) {
		mc.level = this.level;
		mc.player = this.player;
		mc.gameMode = this.gameMode;
		mc.hitResult = this.hitResult;
		mc.crosshairPickEntity = this.crosshairPickEntity;
		if (this.camera != null) {
			mc.gameRenderer.mainCamera().setEntity(this.camera);
		}
		mc.options.setServerRenderDistance(this.serverRenderDistance);
	}
}
