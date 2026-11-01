package dev.blueprintplanner.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.config.PlannerConfig;
import dev.blueprintplanner.planner.ContextSwitcher;
import dev.blueprintplanner.planner.PlannerServer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The planner server loads and simulates as many chunks as set in the planner options (vanilla: like the client). */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
	@ModifyExpressionValue(method = "tickServer", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I", ordinal = 0))
	private int blueprintplanner$plannerViewDistance(int viewDistance) {
		if (!((Object) this instanceof PlannerServer)) {
			return viewDistance;
		}
		PlannerConfig config = PlannerConfig.get();
		int view = config.plannerViewDistance > 0 ? Math.max(2, config.plannerViewDistance) : viewDistance;
		// chunks only tick while they are loaded, so the active radius pulls the loaded radius along
		return config.plannerSimulationDistance > 0 ? Math.max(view, config.plannerSimulationDistance) : view;
	}

	/**
	 * "Open to LAN" pressed while the planner is shown publishes the real singleplayer world; it prepares the chat
	 * key of Minecraft's current connection, which has to be the real one then.
	 */
	@WrapMethod(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z")
	private boolean blueprintplanner$publishWithRealWorld(MinecraftServer.MultiplayerScope scope, int port, Operation<Boolean> original) {
		if ((Object) this instanceof PlannerServer || !PlannerIsolation.inPlannerClientContext()) {
			return original.call(scope, port);
		}
		boolean[] published = new boolean[1];
		ContextSwitcher.runIn(ContextSwitcher.Side.REAL, () -> published[0] = original.call(scope, port));
		return published[0];
	}

	@ModifyExpressionValue(method = "tickServer", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I", ordinal = 1))
	private int blueprintplanner$plannerSimulationDistance(int simulationDistance) {
		int configured = PlannerConfig.get().plannerSimulationDistance;
		return (Object) this instanceof PlannerServer && configured > 0 ? Math.max(2, configured) : simulationDistance;
	}
}
