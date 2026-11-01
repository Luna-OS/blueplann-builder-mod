package dev.blueprintplanner.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.logging.LogUtils;
import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.PlannerManager;
import java.nio.file.Path;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A chunk that fails to generate or load hands its crash report straight to the client, which then crashes. For the
 * planner (e.g. a broken mod) only the planner closes; the real world keeps running.
 */
@Mixin(GenerationChunkHolder.class)
public abstract class GenerationChunkHolderMixin {
	@Unique
	private static final Logger LOGGER = LogUtils.getLogger();

	@WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/thread/BlockableEventLoop;relayDelayCrash(Lnet/minecraft/CrashReport;)V"))
	private void blueprintplanner$containPlannerCrash(CrashReport report, Operation<Void> original) {
		if (!((Object) this instanceof ChunkHolder holder) || !PlannerIsolation.isPlannerLevel(((ChunkHolderAccessor) holder).blueprintplanner$getLevel())) {
			original.call(report);
			return;
		}
		Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-planner.txt");
		if (report.saveToFile(file, ReportType.CRASH)) {
			LOGGER.error("Planner crash report saved to {}", file.toAbsolutePath(), report.getException());
		} else {
			LOGGER.error("Planner chunk generation/loading failed", report.getException());
		}
		String reason = report.getException().toString();
		Minecraft.getInstance().execute(() -> PlannerManager.onPlannerChunkCrashed(reason));
	}
}
