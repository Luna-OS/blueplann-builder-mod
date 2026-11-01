package dev.blueprintplanner.mixin;

import dev.blueprintplanner.config.PlannerConfig;
import dev.blueprintplanner.planner.ContextSwitcher;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	/** Chat typed inside the planner goes to the real server; commands stay in the planner. */
	@Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$chatToRealServer(String content, CallbackInfo ci) {
		ClientPacketListener self = (ClientPacketListener) (Object) this;
		if (PlannerConfig.get().chatToRealServer && PlannerManager.isPlannerConnection(self.getConnection())) {
			ClientPacketListener real = PlannerManager.realListener();
			if (real != null && real != self) {
				ContextSwitcher.runIn(ContextSwitcher.Side.REAL, () -> real.sendChat(content));
				ci.cancel();
			}
		}
	}
}
