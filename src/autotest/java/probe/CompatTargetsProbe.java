package probe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads every class the optional compat hooks target. In test builds the hooks are required, so a hook that no
 * longer finds its target (mod updated) fails here instead of silently doing nothing.
 */
public final class CompatTargetsProbe {
	private static final Map<String, List<String>> TARGETS = Map.ofEntries(
		Map.entry("distanthorizons", List.of(
			"com.seibel.distanthorizons.core.api.internal.ClientApi",
			"com.seibel.distanthorizons.core.api.internal.ServerApi",
			"com.seibel.distanthorizons.core.api.internal.SharedApi",
			"com.seibel.distanthorizons.core.world.DhClientWorld",
			"com.seibel.distanthorizons.core.world.DhClientServerWorld",
			"com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftClientWrapper",
			"net.irisshaders.iris.compat.dh.DHCompat")),
		Map.entry("bobby", List.of("de.johni0702.minecraft.bobby.Bobby", "de.johni0702.minecraft.bobby.BobbyConfig")),
		Map.entry("iris", List.of("net.irisshaders.iris.Iris", "net.irisshaders.iris.UpdateChecker")),
		Map.entry("flashback", List.of("com.moulberry.flashback.record.Recorder", "com.moulberry.flashback.Flashback")),
		Map.entry("viafabricplus", List.of("com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslationImpl")),
		Map.entry("axiom", List.of(
			"com.moulberry.axiom.AxiomClient",
			"com.moulberry.axiom.world_modification.Dispatcher",
			"com.moulberry.axiom.operations.RebuildOperation",
			"com.moulberry.axiom.ClientEvents",
			"com.moulberry.axiom.capabilities.BuildSymmetry")),
		Map.entry("plasmovoice", List.of(
			"su.plo.slib.mod.mixinkt.MixinServerPlayerKt",
			"su.plo.slib.mod.mixinkt.MixinPlayerListKt",
			"su.plo.voice.client.connection.ModClientChannelHandler")),
		Map.entry("lambdynlights", List.of("dev.lambdaurora.lambdynlights.LambDynLights")),
		Map.entry("roughlyenoughitems", List.of(
			"me.shedaniel.rei.RoughlyEnoughItemsCoreClient",
			"me.shedaniel.rei.impl.client.registry.display.ClientRecipeFallback",
			"me.shedaniel.rei.impl.client.registry.display.DisplayRegistryImpl",
			"me.shedaniel.rei.impl.common.InternalRegistryAccess",
			"me.shedaniel.rei.impl.common.util.InstanceHelper")),
		Map.entry("xaerominimap", List.of(
			"xaero.common.server.core.XaeroMinimapServerCore",
			"xaero.common.core.XaeroMinimapCore",
			"xaero.hud.HudSession",
			"xaero.common.events.ClientEvents")),
		Map.entry("xaeroworldmap", List.of(
			"xaero.map.server.core.XaeroWorldMapServerCore",
			"xaero.map.events.CommonEvents",
			"xaero.map.core.XaeroWorldMapCore",
			"xaero.map.MapProcessor",
			"xaero.map.events.ClientEvents",
			"xaero.map.controls.ControlsHandler")),
		Map.entry("xaerolib", List.of(
			"xaero.lib.XaeroLib",
			"xaero.lib.common.packet.PacketHandlerFabric",
			"xaero.lib.common.packet.ClientPacketHandlerFabric",
			"xaero.lib.common.packet.ClientPacketReceiverFabric",
			"xaero.lib.client.config.sync.ClientConfigSynchronizer",
			"xaero.lib.common.config.server.ServerConfigManager")),
		Map.entry("voicechat", List.of(
			"de.maxhenkel.voicechat.net.ClientServerNetManager",
			"de.maxhenkel.voicechat.voice.client.AudioChannel",
			"de.maxhenkel.voicechat.voice.client.camera.CameraTracker",
			"de.maxhenkel.voicechat.voice.server.ServerVoiceEvents",
			"de.maxhenkel.voicechat.net.Channel",
			"de.maxhenkel.voicechat.net.ClientServerChannel",
			"de.maxhenkel.voicechat.voice.client.ClientManager",
			"de.maxhenkel.voicechat.voice.client.ClientPlayerStateManager",
			"de.maxhenkel.voicechat.voice.server.ServerPlayerManager")),
		Map.entry("jei", List.of(
			"mezz.jei.fabric.startup.ClientLifecycleHandler",
			"mezz.jei.fabric.events.JeiLifecycleEvents",
			"mezz.jei.gui.util.CommandUtil",
			"mezz.jei.common.Internal",
			"mezz.jei.common.util.ServerConfigPathUtil"))
	);

	private CompatTargetsProbe() {
	}

	/** Problems found, empty if every hook of every installed mod applied. */
	public static List<String> check() {
		List<String> problems = new ArrayList<>();
		ClassLoader loader = CompatTargetsProbe.class.getClassLoader();
		TARGETS.forEach((mod, classes) -> {
			if (!FabricLoader.getInstance().isModLoaded(mod)) {
				return;
			}
			for (String name : classes) {
				if (name.startsWith("net.irisshaders") && !FabricLoader.getInstance().isModLoaded("iris")) {
					continue;
				}
				try {
					Class.forName(name, false, loader);
				} catch (Throwable t) {
					problems.add(name + ": " + t);
				}
			}
		});
		return problems;
	}
}
