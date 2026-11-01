package dev.blueprintplanner.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.PlannerServer;
import java.util.List;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * The server side of the planner connection fires Fabric's connection events partly on network threads, not on the
 * planner server thread. Mark them as planner server work so other mods' listeners stay out (see PlannerIsolation).
 */
@Mixin(
	targets = {
		"net.fabricmc.fabric.impl.networking.server.ServerLoginNetworkAddon",
		"net.fabricmc.fabric.impl.networking.server.ServerConfigurationNetworkAddon",
		"net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon"
	},
	remap = false
)
public abstract class FabricServerNetworkAddonMixin {
	@Shadow(remap = false)
	@Final
	private MinecraftServer server;

	@WrapMethod(method = {"invokeInitEvent()V", "invokeDisconnectEvent()V", "onClientReady()V"}, require = 0)
	private void blueprintplanner$plannerEvent(Operation<Void> original) {
		if (this.server instanceof PlannerServer) {
			PlannerIsolation.runAsPlannerServer(() -> original.call());
		} else {
			original.call();
		}
	}

	@WrapMethod(method = "onPong(I)V", require = 0)
	private void blueprintplanner$plannerPong(int id, Operation<Void> original) {
		if (this.server instanceof PlannerServer) {
			PlannerIsolation.runAsPlannerServer(() -> original.call(id));
		} else {
			original.call(id);
		}
	}

	@WrapMethod(method = {"invokeRegisterEvent(Ljava/util/List;)V", "invokeUnregisterEvent(Ljava/util/List;)V"}, require = 0)
	private void blueprintplanner$plannerChannels(List<Identifier> channels, Operation<Void> original) {
		if (this.server instanceof PlannerServer) {
			PlannerIsolation.runAsPlannerServer(() -> original.call(channels));
		} else {
			original.call(channels);
		}
	}

	@WrapMethod(method = "queryTick()Z", require = 0)
	private boolean blueprintplanner$plannerQueryTick(Operation<Boolean> original) {
		return this.server instanceof PlannerServer ? PlannerIsolation.runAsPlannerServer(() -> original.call()) : original.call();
	}

	@WrapMethod(method = "handle(Lnet/minecraft/network/protocol/login/ServerboundCustomQueryAnswerPacket;)Z", require = 0)
	private boolean blueprintplanner$plannerQueryAnswer(ServerboundCustomQueryAnswerPacket packet, Operation<Boolean> original) {
		return this.server instanceof PlannerServer ? PlannerIsolation.runAsPlannerServer(() -> original.call(packet)) : original.call(packet);
	}
}
