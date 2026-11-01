package dev.blueprintplanner.mixin;

import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon", remap = false)
public interface ChanneledNetworkAddonAccessor {
	@Accessor("connection")
	Connection blueprintplanner$getConnection();
}
