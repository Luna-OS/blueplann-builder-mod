package dev.blueprintplanner.compat.mixin.xaero;

import com.mojang.logging.LogUtils;
import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Xaero's chat handling uses the minimap session, which does not exist in the planner (it would crash there). */
@Mixin(targets = "xaero.common.events.ClientEvents", remap = false)
public abstract class XaeroClientEventsMixin {
	/** Clicked waypoint links ("xaero_waypoint_add:...") belong to the real world; normal chat goes on as usual. */
	@Inject(method = "handleClientSendChatEvent(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$noWaypointsInPlanner(String message, CallbackInfoReturnable<Boolean> cir) {
		if (!PlannerIsolation.inPlannerClientContext()) {
			return;
		}
		boolean xaeroCommand = message.startsWith("xaero_waypoint_add:") || message.equals("xaero_tp_anyway");
		if (xaeroCommand) {
			LogUtils.getLogger().debug("Xaero chat command swallowed inside the planner: {}", message);
			PlannerManager.message(Component.translatable("blueprintplanner.compat.xaero_waypoint"), true);
		}
		cir.setReturnValue(xaeroCommand);
	}

	@Inject(method = "handleClientSystemChatReceivedEvent(Lnet/minecraft/network/chat/Component;)Z", at = @At("HEAD"), cancellable = true)
	private void blueprintplanner$plainChatInPlanner(Component message, CallbackInfoReturnable<Boolean> cir) {
		if (PlannerIsolation.inPlannerClientContext()) {
			cir.setReturnValue(false);
		}
	}
}
