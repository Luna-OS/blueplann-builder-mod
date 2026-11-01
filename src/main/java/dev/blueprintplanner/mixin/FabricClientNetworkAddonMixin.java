package dev.blueprintplanner.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.blueprintplanner.compat.PlannerIsolation;
import java.util.List;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Fabric fires all client connection events (login, configuration, join, disconnect, channel registration) from
 * these per-connection addons, partly on network threads. Marking the planner connection's events here hides
 * them from other mods no matter which thread they run on.
 */
@Mixin(
	targets = {
		"net.fabricmc.fabric.impl.networking.client.ClientLoginNetworkAddon",
		"net.fabricmc.fabric.impl.networking.client.ClientConfigurationNetworkAddon",
		"net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon"
	},
	remap = false
)
public abstract class FabricClientNetworkAddonMixin {
	@WrapMethod(
		method = {"invokeInitEvent()V", "invokeDisconnectEvent()V", "onServerReady()V", "invokeStartEvent()V", "handleComplete()V"},
		require = 0
	)
	private void blueprintplanner$hidePlannerConnection(Operation<Void> original) {
		PlannerIsolation.fireConnectionEvent(this.blueprintplanner$connection(), () -> original.call());
	}

	@WrapMethod(method = {"invokeRegisterEvent(Ljava/util/List;)V", "invokeUnregisterEvent(Ljava/util/List;)V"}, require = 0)
	private void blueprintplanner$hidePlannerChannels(List<Identifier> channels, Operation<Void> original) {
		PlannerIsolation.fireConnectionEvent(this.blueprintplanner$connection(), () -> original.call(channels));
	}

	@WrapMethod(method = "handlePacket(ILnet/minecraft/resources/Identifier;Lnet/minecraft/network/FriendlyByteBuf;)Z", require = 0)
	private boolean blueprintplanner$hidePlannerLoginQuery(int queryId, Identifier channel, FriendlyByteBuf buf, Operation<Boolean> original) {
		return PlannerIsolation.fireConnectionEvent(this.blueprintplanner$connection(), () -> original.call(queryId, channel, buf));
	}

	@Unique
	private @Nullable Connection blueprintplanner$connection() {
		Object self = this;
		if (self instanceof ChanneledNetworkAddonAccessor channeled) {
			return channeled.blueprintplanner$getConnection();
		}
		if (self instanceof ClientLoginNetworkAddonAccessor login && login.blueprintplanner$getListener() != null) {
			return ((ClientHandshakePacketListenerAccessor) login.blueprintplanner$getListener()).blueprintplanner$getConnection();
		}
		return null;
	}
}
