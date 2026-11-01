package dev.blueprintplanner.gui;

import dev.blueprintplanner.config.PlannerConfig;
import dev.blueprintplanner.planner.EntryInfo;
import dev.blueprintplanner.planner.PlannerManager;
import dev.blueprintplanner.planner.PlannerServer;
import dev.blueprintplanner.planner.PlannerSky;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/** Simulation controls (tick rate, freeze, step, sprint, game rules) and planner options. */
public class PlannerSettingsScreen extends Screen {
	private static final int[] TICK_RATES = {1, 2, 3, 4, 5, 8, 10, 15, 20, 25, 30, 40, 50, 60, 80, 100, 150, 200, 300, 500, 1000};
	private static final List<Integer> RANDOM_TICK_SPEEDS = List.of(0, 1, 3, 10, 20, 50, 100, 500);
	private static final int ROW = 22;

	private final @Nullable Screen parent;
	private int page;

	public PlannerSettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("blueprintplanner.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int colWidth = 120;
		int gap = 4;
		int left = this.width / 2 - (colWidth * 3 + gap * 2) / 2;
		int col2 = left + colWidth + gap;
		int col3 = col2 + colWidth + gap;
		int y = 18;

		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.settings.page.simulation"), b -> this.switchPage(0))
			.bounds(left, y, colWidth * 3 / 2 + 1, 20).build()).active = this.page != 0;
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.settings.page.options"), b -> this.switchPage(1))
			.bounds(left + colWidth * 3 / 2 + gap + 1, y, colWidth * 3 / 2 + 1, 20).build()).active = this.page != 1;
		y += ROW + 4;

		if (this.page == 0) {
			this.initSimulation(left, col2, col3, colWidth, y);
		} else {
			this.initOptions(left, col2, col3, colWidth, y);
		}

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
			.bounds(this.width / 2 - 100, this.height - 23, 200, 20).build());
	}

	public void switchPage(int newPage) {
		this.page = newPage;
		this.rebuildWidgets();
	}

	private void initSimulation(int left, int col2, int col3, int colWidth, int y) {
		PlannerServer server = PlannerManager.server();
		boolean available = server != null;
		float currentRate = available ? server.tickRateManager().tickrate() : 20.0F;
		boolean frozen = available && server.tickRateManager().isFrozen();

		AbstractSliderButton tickSlider = new AbstractSliderButton(left, y, colWidth * 2 + 4, 20, Component.empty(), indexOfRate(currentRate) / (double) (TICK_RATES.length - 1)) {
			{
				this.updateMessage();
			}

			@Override
			protected void updateMessage() {
				this.setMessage(Component.translatable("blueprintplanner.settings.tickrate", rateAt(this.value)));
			}

			@Override
			protected void applyValue() {
				PlannerServer s = PlannerManager.server();
				if (s != null) {
					s.setTickRate(rateAt(this.value));
				}
			}
		};
		tickSlider.active = available;
		this.addRenderableWidget(tickSlider);
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.settings.tickrate.reset"), b -> {
			if (PlannerManager.server() != null) {
				PlannerManager.server().setTickRate(20.0F);
			}
			this.rebuildWidgets();
		}).bounds(col3, y, colWidth, 20).build()).active = available;
		y += ROW;

		this.addRenderableWidget(Button.builder(
			Component.translatable(frozen ? "blueprintplanner.settings.unfreeze" : "blueprintplanner.settings.freeze"),
			b -> {
				PlannerServer s = PlannerManager.server();
				if (s != null) {
					s.setFrozen(!s.tickRateManager().isFrozen());
				}
				this.minecraft.execute(this::rebuildWidgets);
			}
		).bounds(left, y, colWidth, 20).build()).active = available;
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.settings.step", 1), b -> this.withServer(s -> s.step(1)))
			.bounds(col2, y, colWidth / 2 - 2, 20).build()).active = available;
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.settings.step", 10), b -> this.withServer(s -> s.step(10)))
			.bounds(col2 + colWidth / 2 + 2, y, colWidth / 2 - 2, 20).build()).active = available;
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.settings.sprint", 1200), b -> this.withServer(s -> s.sprint(1200)))
			.bounds(col3, y, colWidth, 20).build()).active = available;
		y += ROW;

		this.addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)), (Supplier<Integer>) () -> this.ruleValue(GameRules.RANDOM_TICK_SPEED, 3))
			.withValues(withCurrent(RANDOM_TICK_SPEEDS, this.ruleValue(GameRules.RANDOM_TICK_SPEED, 3)))
			.create(left, y, colWidth, 20, Component.translatable("blueprintplanner.settings.randomtick"), (btn, v) -> this.setRule(GameRules.RANDOM_TICK_SPEED, v)))
			.active = available;
		this.addRenderableWidget(this.ruleToggle(col2, y, colWidth, GameRules.ADVANCE_TIME, "blueprintplanner.settings.daylight")).active = available;
		this.addRenderableWidget(this.ruleToggle(col3, y, colWidth, GameRules.ADVANCE_WEATHER, "blueprintplanner.settings.weathercycle")).active = available;
		y += ROW;

		int quarter = (colWidth * 3 + 8) / 4;
		String[][] times = {{"blueprintplanner.settings.time.day", "1000"}, {"blueprintplanner.settings.time.noon", "6000"},
			{"blueprintplanner.settings.time.night", "13000"}, {"blueprintplanner.settings.time.midnight", "18000"}};
		for (int i = 0; i < times.length; i++) {
			String ticks = times[i][1];
			this.addRenderableWidget(Button.builder(Component.translatable(times[i][0]), b -> this.withServer(s -> s.execute(() -> s.runCommand("time set " + ticks))))
				.bounds(left + i * (quarter + 1), y, quarter - 1, 20).build()).active = available;
		}
		y += ROW;

		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.settings.weather.clear"), b -> this.withServer(s -> s.execute(() -> s.runCommand("weather clear"))))
			.bounds(left, y, colWidth, 20).build()).active = available;
		this.addRenderableWidget(this.ruleToggle(col2, y, colWidth, GameRules.SPAWN_MOBS, "blueprintplanner.settings.mobspawning")).active = available;
		this.addRenderableWidget(this.ruleToggle(col3, y, colWidth, GameRules.TNT_EXPLODES, "blueprintplanner.settings.tnt")).active = available;
		y += ROW;

		this.addRenderableWidget(this.ruleToggle(left, y, colWidth, GameRules.BLOCK_DROPS, "blueprintplanner.settings.blockdrops")).active = available;
		this.addRenderableWidget(this.ruleToggle(col2, y, colWidth, GameRules.MOB_GRIEFING, "blueprintplanner.settings.mobgriefing")).active = available;
		this.addRenderableWidget(this.ruleToggle(col3, y, colWidth, GameRules.SPREAD_VINES, "blueprintplanner.settings.vines")).active = available;
		y += ROW;

		this.addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)), (Supplier<Integer>) () -> this.ruleValue(GameRules.MAX_ENTITY_CRAMMING, 24))
			.withValues(withCurrent(List.of(0, 8, 24, 64, 1000), this.ruleValue(GameRules.MAX_ENTITY_CRAMMING, 24)))
			.create(left, y, colWidth, 20, Component.translatable("blueprintplanner.settings.cramming"), (btn, v) -> this.setRule(GameRules.MAX_ENTITY_CRAMMING, v)))
			.active = available;
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.settings.kill_items"), b -> this.withServer(s -> s.execute(() -> s.runCommand("kill @e[type=item]"))))
			.bounds(col2, y, colWidth, 20).build()).active = available;
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.settings.kill_entities"), b -> this.withServer(s -> s.execute(() -> s.runCommand("kill @e[type=!player]"))))
			.bounds(col3, y, colWidth, 20).build()).active = available;
	}

	private void initOptions(int left, int col2, int col3, int colWidth, int y) {
		PlannerConfig config = PlannerConfig.get();
		this.addRenderableWidget(this.configToggle(left, y, colWidth, "blueprintplanner.settings.eject", config.ejectOnDamage, v -> config.ejectOnDamage = v));
		this.addRenderableWidget(this.configToggle(col2, y, colWidth, "blueprintplanner.settings.onground", config.requireOnGround, v -> config.requireOnGround = v));
		this.addRenderableWidget(this.configToggle(col3, y, colWidth, "blueprintplanner.settings.freezeaway", config.freezeWhenAway, v -> config.freezeWhenAway = v));
		y += ROW;

		this.addRenderableWidget(this.configToggle(left, y, colWidth, "blueprintplanner.settings.fullbright", config.fullbright, v -> {
			config.fullbright = v;
			PlannerServer s = PlannerManager.server();
			if (s != null) {
				s.execute(() -> {
					if (s.plannerPlayer() != null) {
						s.applyFullbright(s.plannerPlayer());
					}
				});
			}
		}));
		this.addRenderableWidget(this.configToggle(col2, y, colWidth, "blueprintplanner.settings.power", config.showRedstonePower, v -> config.showRedstonePower = v));
		this.addRenderableWidget(this.configToggle(col3, y, colWidth, "blueprintplanner.settings.chat", config.chatToRealServer, v -> config.chatToRealServer = v));
		y += ROW;

		this.addRenderableWidget(this.configToggle(left, y, colWidth, "blueprintplanner.settings.import", config.importSurroundings, v -> config.importSurroundings = v));
		this.addRenderableWidget(this.intSlider(col2, y, colWidth, 4, 96, config.importRadius, v -> Component.translatable("blueprintplanner.settings.importradius", v), v -> config.importRadius = v));
		this.addRenderableWidget(this.intSlider(col3, y, colWidth, 8, 128, config.plateRadius, v -> Component.translatable("blueprintplanner.settings.plateradius", v), v -> config.plateRadius = v));
		y += ROW;

		this.addRenderableWidget(this.intSlider(left, y, colWidth, 0, 64, config.importDepth, v -> Component.translatable("blueprintplanner.settings.importdepth", v), v -> config.importDepth = v));
		this.addRenderableWidget(this.intSlider(col2, y, colWidth, 4, 128, config.importHeight, v -> Component.translatable("blueprintplanner.settings.importheight", v), v -> config.importHeight = v));
		this.addRenderableWidget(this.intSlider(col3, y, colWidth, 0, 20, Math.round(config.ejectMinDamage), v -> Component.translatable("blueprintplanner.settings.ejectmin", v), v -> config.ejectMinDamage = v));
		y += ROW;

		CycleButton<Boolean> isolate = this.configToggle(left, y, colWidth, "blueprintplanner.settings.isolate", config.isolateFromOtherMods, v -> config.isolateFromOtherMods = v);
		isolate.setTooltip(Tooltip.create(Component.translatable("blueprintplanner.settings.isolate.tooltip")));
		this.addRenderableWidget(isolate);
		CycleButton<PlannerSky> sky = CycleButton.<PlannerSky>builder(v -> Component.translatable(v.translationKey()), (Supplier<PlannerSky>) PlannerSky::current)
			.withValues(PlannerSky.values())
			.create(col2, y, colWidth, 20, Component.translatable("blueprintplanner.settings.sky"), (btn, v) -> {
				config.plannerSky = v.name();
				PlannerConfig.save();
			});
		sky.setTooltip(Tooltip.create(Component.translatable("blueprintplanner.settings.sky.tooltip")));
		this.addRenderableWidget(sky);
		CycleButton<Boolean> stick = this.configToggle(col3, y, colWidth, "blueprintplanner.settings.stick", config.placementStick, v -> config.placementStick = v);
		stick.setTooltip(Tooltip.create(Component.translatable("blueprintplanner.settings.stick.tooltip")));
		this.addRenderableWidget(stick);
		y += ROW;

		AbstractSliderButton viewDistance = this.intSlider(left, y, colWidth, 0, 32, config.plannerViewDistance,
			v -> Component.translatable("blueprintplanner.settings.viewdistance", chunks(v)), v -> config.plannerViewDistance = v);
		viewDistance.setTooltip(Tooltip.create(Component.translatable("blueprintplanner.settings.viewdistance.tooltip")));
		this.addRenderableWidget(viewDistance);
		AbstractSliderButton simulationDistance = this.intSlider(col2, y, colWidth, 0, 32, config.plannerSimulationDistance,
			v -> Component.translatable("blueprintplanner.settings.simdistance", chunks(v)), v -> config.plannerSimulationDistance = v);
		simulationDistance.setTooltip(Tooltip.create(Component.translatable("blueprintplanner.settings.simdistance.tooltip")));
		this.addRenderableWidget(simulationDistance);
		y += ROW + 4;

		boolean inPlanner = PlannerManager.isActive();
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.settings.reimport"), b -> this.resetArea(true))
			.bounds(left, y, colWidth * 3 / 2 + 1, 20).build()).active = inPlanner;
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.settings.clear"), b -> this.resetArea(false))
			.bounds(left + colWidth * 3 / 2 + 5, y, colWidth * 3 / 2 + 1, 20).build()).active = inPlanner;
	}

	private static Component chunks(int value) {
		return value <= 0 ? Component.translatable("blueprintplanner.settings.auto") : Component.literal(String.valueOf(Math.max(2, value)));
	}

	private void resetArea(boolean reimport) {
		this.minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(confirmed -> {
			if (confirmed) {
				PlannerServer server = PlannerManager.server();
				var real = PlannerManager.realPlayer();
				var realLevel = PlannerManager.realLevel();
				if (server != null && this.minecraft.player != null) {
					int radius = Math.max(1, (PlannerConfig.get().importRadius + 15) / 16);
					EntryInfo info = null;
					if (reimport && real != null && realLevel != null) {
						EntryInfo captured = EntryInfo.capture(real, realLevel);
						info = captured;
					}
					server.resetChunksAround(this.minecraft.player.blockPosition(), radius, info);
					PlannerManager.forgetQueuedChunks();
				}
			}
			this.minecraft.gui.setScreen(this);
		}, Component.translatable(reimport ? "blueprintplanner.settings.reimport" : "blueprintplanner.settings.clear"),
			Component.translatable("blueprintplanner.settings.reset.confirm")));
	}

	private void withServer(Consumer<PlannerServer> action) {
		PlannerServer server = PlannerManager.server();
		if (server != null) {
			action.accept(server);
		}
	}

	private <T> T ruleValue(GameRule<T> rule, T fallback) {
		PlannerServer server = PlannerManager.server();
		return server != null ? server.getGameRules().get(rule) : fallback;
	}

	private <T> void setRule(GameRule<T> rule, T value) {
		this.withServer(s -> s.setGameRule(rule, value));
	}

	private CycleButton<Boolean> ruleToggle(int x, int y, int width, GameRule<Boolean> rule, String key) {
		return CycleButton.onOffBuilder(this.ruleValue(rule, rule.defaultValue()))
			.create(x, y, width, 20, Component.translatable(key), (btn, v) -> this.setRule(rule, v));
	}

	private CycleButton<Boolean> configToggle(int x, int y, int width, String key, boolean value, Consumer<Boolean> setter) {
		return CycleButton.onOffBuilder(value).create(x, y, width, 20, Component.translatable(key), (btn, v) -> {
			setter.accept(v);
			PlannerConfig.save();
		});
	}

	private AbstractSliderButton intSlider(int x, int y, int width, int min, int max, int value, IntFunction<Component> label, Consumer<Integer> setter) {
		return new AbstractSliderButton(x, y, width, 20, Component.empty(), (value - min) / (double) (max - min)) {
			{
				this.updateMessage();
			}

			private int current() {
				return (int) Math.round(min + this.value * (max - min));
			}

			@Override
			protected void updateMessage() {
				this.setMessage(label.apply(this.current()));
			}

			@Override
			protected void applyValue() {
				setter.accept(this.current());
				PlannerConfig.save();
			}
		};
	}

	private static List<Integer> withCurrent(List<Integer> values, int current) {
		if (values.contains(current)) {
			return values;
		}
		return java.util.stream.Stream.concat(values.stream(), java.util.stream.Stream.of(current)).sorted().toList();
	}

	private static int indexOfRate(float rate) {
		int best = 0;
		for (int i = 0; i < TICK_RATES.length; i++) {
			if (Math.abs(TICK_RATES[i] - rate) < Math.abs(TICK_RATES[best] - rate)) {
				best = i;
			}
		}
		return best;
	}

	private static int rateAt(double sliderValue) {
		return TICK_RATES[Mth.clamp((int) Math.round(sliderValue * (TICK_RATES.length - 1)), 0, TICK_RATES.length - 1)];
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 5, 0xFFFFFFFF);
		if (PlannerManager.server() == null) {
			graphics.centeredText(this.font, Component.translatable("blueprintplanner.settings.not_running"), this.width / 2, this.height - 34, 0xFFFFAA55);
		}
	}

	@Override
	public void onClose() {
		PlannerConfig.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
