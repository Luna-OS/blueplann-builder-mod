package dev.blueprintplanner.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.blueprintplanner.planner.ContextSwitcher;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Every main-thread packet of every connection passes through here. Packets of the world that is currently
 * in the background are handled with that world's fields temporarily installed in {@code Minecraft}.
 */
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class PacketProcessorMixin {
	@Shadow
	@Final
	private PacketListener listener;

	@Shadow
	@Final
	private Packet<?> packet;

	@WrapMethod(method = "handle")
	private void blueprintplanner$routeToContext(Operation<Void> original) {
		if (PlannerManager.phase() == PlannerManager.Phase.OFF && PlannerManager.plannerConnection() == null) {
			original.call();
			return;
		}
		ContextSwitcher.Side side = PlannerManager.sideOf(this.listener);
		if (side == null) {
			original.call();
		} else {
			PlannerManager.handlePacket(side, this.packet, () -> original.call());
		}
	}
}
