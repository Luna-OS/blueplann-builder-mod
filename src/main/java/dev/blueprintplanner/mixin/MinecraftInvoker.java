package dev.blueprintplanner.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftInvoker {
	@Invoker("updateLevelInEngines")
	void blueprintplanner$updateLevelInEngines(@Nullable ClientLevel level, boolean stopSound);
}
