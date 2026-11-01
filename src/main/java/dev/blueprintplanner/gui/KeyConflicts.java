package dev.blueprintplanner.gui;

import com.mojang.logging.LogUtils;
import dev.blueprintplanner.BlueprintPlanner;
import dev.blueprintplanner.planner.PlannerManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;

/**
 * Another mod on the same key as one of ours: Minecraft triggers both, and when the other one opens a screen our key
 * press is thrown away (Xaero's Minimap uses B for "new waypoint", for example). Pointed out once per game start.
 */
public final class KeyConflicts {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static boolean checked;

	/** One of our key mappings and a mapping of something else on the same key. */
	public record Conflict(KeyMapping ours, KeyMapping other) {
	}

	private KeyConflicts() {
	}

	public static List<Conflict> find() {
		List<Conflict> conflicts = new ArrayList<>();
		for (KeyMapping ours : BlueprintPlanner.KEYS) {
			if (ours.isUnbound()) {
				continue;
			}
			for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
				// debug keys only work together with F3, they do not get in the way
				if (other != ours && !BlueprintPlanner.KEYS.contains(other) && other.getCategory() != KeyMapping.Category.DEBUG && ours.same(other)) {
					conflicts.add(new Conflict(ours, other));
				}
			}
		}
		return conflicts;
	}

	/** End of the client tick: in the real world, without a screen, check once. */
	public static void tick(Minecraft mc) {
		if (checked || mc.player == null || PlannerManager.isActive() || mc.gui.screen() != null) {
			return;
		}
		checked = true;
		Map<KeyMapping, MutableComponent> others = new LinkedHashMap<>();
		for (Conflict conflict : find()) {
			LOGGER.warn("Key {} is used by {} and {}", conflict.ours().saveString(), conflict.ours().getName(), conflict.other().getName());
			MutableComponent other = Component.translatable("blueprintplanner.keys.conflict.other", Component.translatable(conflict.other().getName()),
				conflict.other().getCategory().label());
			others.merge(conflict.ours(), other, (a, b) -> a.append(", ").append(b));
		}
		if (others.isEmpty()) {
			return;
		}
		PlannerManager.message(Component.translatable("blueprintplanner.keys.conflict").withStyle(ChatFormatting.GOLD), false);
		others.forEach((ours, other) -> PlannerManager.message(Component.translatable("blueprintplanner.keys.conflict.line",
			ours.getTranslatedKeyMessage(), Component.translatable(ours.getName()), other).withStyle(ChatFormatting.GOLD), false));
	}
}
