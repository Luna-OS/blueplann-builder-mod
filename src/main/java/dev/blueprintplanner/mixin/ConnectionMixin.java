package dev.blueprintplanner.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
	/** Other mods hook packet handlers on the network thread too; let them know a packet belongs to the planner. */
	@WrapOperation(
		method = "channelRead0",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V")
	)
	private void blueprintplanner$markPlannerPacket(Packet<?> packet, PacketListener listener, Operation<Void> original) {
		if (PlannerManager.isPlannerConnection((Connection) (Object) this)) {
			PlannerIsolation.runForPlannerConnection(() -> original.call(packet, listener));
		} else {
			original.call(packet, listener);
		}
	}
}
