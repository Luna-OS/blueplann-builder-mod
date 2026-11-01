package dev.blueprintplanner.compat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.jspecify.annotations.Nullable;

/** Applies the hooks for another mod only when that mod is installed (one sub-package per mod). */
public final class CompatMixinPlugin implements IMixinConfigPlugin {
	/** Sub-package of the hooks -> mods that must all be installed for them. */
	private static final Map<String, List<String>> MODS_BY_PACKAGE = Map.ofEntries(
		Map.entry(".dh.", List.of("distanthorizons")),
		Map.entry(".bobby.", List.of("bobby")),
		Map.entry(".iris.", List.of("iris")),
		Map.entry(".irisdh.", List.of("iris", "distanthorizons")),
		Map.entry(".flashback.", List.of("flashback")),
		Map.entry(".vfp.", List.of("viafabricplus")),
		Map.entry(".axiom.", List.of("axiom")),
		Map.entry(".plasmo.", List.of("plasmovoice")),
		Map.entry(".ldl.", List.of("lambdynlights")),
		Map.entry(".rei.", List.of("roughlyenoughitems")),
		// the fair-play and Better PvP editions ship the same minimap code under their own ids
		Map.entry(".xaero.", List.of("xaerominimap|xaerominimapfair|xaerobetterpvp")),
		Map.entry(".xaeromap.", List.of("xaeroworldmap")),
		Map.entry(".xaerolib.", List.of("xaerolib")),
		Map.entry(".svc.", List.of("voicechat")),
		Map.entry(".jei.", List.of("jei"))
	);

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public @Nullable String getRefMapperConfig() {
		return null;
	}

	/** A mod id, or alternatives separated by '|' of which one has to be loaded. */
	private static boolean loaded(String ids) {
		for (String id : ids.split("\\|")) {
			if (FabricLoader.getInstance().isModLoaded(id)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		for (Map.Entry<String, List<String>> entry : MODS_BY_PACKAGE.entrySet()) {
			if (mixinClassName.contains(entry.getKey())) {
				return entry.getValue().stream().allMatch(CompatMixinPlugin::loaded);
			}
		}
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public @Nullable List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
