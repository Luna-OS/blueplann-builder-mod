package dev.blueprintplanner.compat.mixin.jei;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.blueprintplanner.compat.PlannerIsolation;
import dev.blueprintplanner.planner.ContextSwitcher;
import java.nio.file.Path;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "mezz.jei.common.util.ServerConfigPathUtil", remap = false)
public abstract class JeiServerConfigPathMixin {
	/** Bookmarks and history are saved per real server; inside the planner JEI found no folder and dropped the changes. */
	@WrapMethod(method = "getWorldPath(Ljava/nio/file/Path;)Ljava/util/Optional;")
	private static Optional<Path> blueprintplanner$realWorldFolder(Path base, Operation<Optional<Path>> original) {
		if (!PlannerIsolation.inPlannerClientContext()) {
			return original.call(base);
		}
		@SuppressWarnings("unchecked")
		Optional<Path>[] result = new Optional[] {Optional.empty()};
		ContextSwitcher.runIn(ContextSwitcher.Side.REAL, () -> result[0] = original.call(base));
		return result[0];
	}
}
