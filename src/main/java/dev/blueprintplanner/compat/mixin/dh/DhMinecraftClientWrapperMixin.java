package dev.blueprintplanner.compat.mixin.dh;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftClientWrapper;
import dev.blueprintplanner.compat.PlannerIsolation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * DH asks for "the current level" from its worker threads as well. While the planner is shown, the answer stays the
 * real level, so DH simply keeps working on the real world in the background.
 */
@Mixin(value = MinecraftClientWrapper.class, remap = false)
public abstract class DhMinecraftClientWrapperMixin {
	@WrapOperation(
		method = "getWrappedClientLevel(Z)Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/IClientLevelWrapper;",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;", opcode = Opcodes.GETFIELD)
	)
	private ClientLevel blueprintplanner$realLevel(Minecraft minecraft, Operation<ClientLevel> original) {
		ClientLevel level = original.call(minecraft);
		return PlannerIsolation.isPlannerLevel(level) ? PlannerIsolation.realLevel() : level;
	}
}
