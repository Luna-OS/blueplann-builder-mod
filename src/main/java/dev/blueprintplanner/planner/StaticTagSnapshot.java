package dev.blueprintplanner.planner;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import org.slf4j.Logger;

/**
 * Starting the planner server re-binds the tags and default item components of the static (built-in)
 * registries, which are shared with the real-world client. When playing on a remote server whose datapacks
 * differ from vanilla this would silently replace the server's tags, so we snapshot and restore them.
 */
final class StaticTagSnapshot {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final List<Runnable> restorers = new ArrayList<>();

	static StaticTagSnapshot capture() {
		StaticTagSnapshot snapshot = new StaticTagSnapshot();
		for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
			snapshot.restorers.add(captureRegistry(registry));
		}
		return snapshot;
	}

	private static <T> Runnable captureRegistry(Registry<T> registry) {
		Map<TagKey<T>, List<Holder<T>>> tags = new HashMap<>();
		registry.getTags().forEach((HolderSet.Named<T> named) -> tags.put(named.key(), named.stream().toList()));
		return () -> registry.prepareTagReload(new TagLoader.LoadResult<>(registry.key(), tags)).apply();
	}

	void restore(RegistryAccess realRegistries) {
		try {
			this.restorers.forEach(Runnable::run);
			BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(realRegistries).forEach(pending -> pending.apply());
		} catch (Exception e) {
			LOGGER.warn("Could not restore static registry tags after starting the planner", e);
		}
	}
}
