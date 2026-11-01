package dev.blueprintplanner.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkHolder.class)
public interface ChunkHolderAccessor {
	@Accessor("levelHeightAccessor")
	LevelHeightAccessor blueprintplanner$getLevel();
}
