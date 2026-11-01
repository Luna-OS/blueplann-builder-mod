package dev.blueprintplanner.mixin;

import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.fabricmc.fabric.impl.networking.client.ClientLoginNetworkAddon", remap = false)
public interface ClientLoginNetworkAddonAccessor {
	@Accessor("listener")
	ClientHandshakePacketListenerImpl blueprintplanner$getListener();
}
