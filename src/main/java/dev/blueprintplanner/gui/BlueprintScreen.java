package dev.blueprintplanner.gui;

import dev.blueprintplanner.BlueprintPlanner;
import dev.blueprintplanner.blueprint.BlueprintActions;
import dev.blueprintplanner.blueprint.BlueprintLibrary;
import dev.blueprintplanner.blueprint.Paster;
import dev.blueprintplanner.blueprint.Placement;
import dev.blueprintplanner.blueprint.PlacementManager;
import dev.blueprintplanner.blueprint.PlacementTool;
import dev.blueprintplanner.blueprint.Selection;
import dev.blueprintplanner.planner.PlannerManager;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.Blaze3D;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/** Blueprint library (files) and placements (holograms in the real world). */
public class BlueprintScreen extends Screen {
	private static final int ROW = 22;
	/** Width of the blueprint name in the library list; the buttons follow it. */
	private static final int NAME_WIDTH = 138;
	private static int tab;

	private final @Nullable Screen parent;
	private int page;
	private List<BlueprintLibrary.Entry> entries = List.of();

	public BlueprintScreen(@Nullable Screen parent) {
		super(Component.translatable("blueprintplanner.library.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.entries = BlueprintLibrary.list();
		int left = this.width / 2 - 185;
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.library.tab.files"), b -> this.switchTab(0))
			.bounds(left, 22, 183, 20).build()).active = tab != 0;
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.library.tab.placements", PlacementManager.placements().size()),
			b -> this.switchTab(1)).bounds(left + 187, 22, 183, 20).build()).active = tab != 1;

		if (tab == 0) {
			this.initFiles(left);
		} else {
			this.initPlacements(left);
		}
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
			.bounds(this.width / 2 - 100, this.height - 26, 200, 20).build());
	}

	private int rowsPerPage() {
		return Math.max(1, (this.height - 110) / ROW);
	}

	public void switchTab(int newTab) {
		tab = newTab;
		this.page = 0;
		this.rebuildWidgets();
	}

	// ------------------------------------------------------------------ files

	private void initFiles(int left) {
		int perPage = this.rowsPerPage();
		int y = 50;
		int from = this.page * perPage;
		boolean planner = PlannerManager.isActive();
		boolean hasSpot = Selection.get().first() != null || Selection.get().second() != null;
		// aiming needs the real world and a player that can click (spectators open containers instead)
		boolean aim = !planner && this.minecraft.player != null && !this.minecraft.player.isSpectator();
		for (int i = from; i < Math.min(this.entries.size(), from + perPage); i++) {
			BlueprintLibrary.Entry entry = this.entries.get(i);
			Button place = Button.builder(Component.translatable("blueprintplanner.library.place"), b -> {
				if (aim) {
					// the hologram follows the crosshair until you click where it should go
					this.minecraft.gui.setScreen(null);
					PlacementTool.startPlacing(entry);
				} else {
					BlueprintActions.placeInRealWorld(entry);
					this.switchTab(1);
				}
			}).bounds(left + NAME_WIDTH + 2, y, 62, 20).build();
			place.setTooltip(Tooltip.create(Component.translatable(aim ? "blueprintplanner.library.place.tooltip" : "blueprintplanner.library.place.tooltip_planner")));
			this.addRenderableWidget(place);
			Button intoSelection = Button.builder(Component.translatable("blueprintplanner.library.into_selection"), b -> {
				BlueprintActions.placeIntoSelection(entry);
				if (planner) {
					this.onClose();
				} else {
					this.switchTab(1);
				}
			}).bounds(left + NAME_WIDTH + 66, y, 60, 20).build();
			intoSelection.setTooltip(Tooltip.create(Component.translatable("blueprintplanner.library.into_selection.tooltip")));
			intoSelection.active = hasSpot;
			this.addRenderableWidget(intoSelection);
			Button toPlanner = Button.builder(Component.translatable("blueprintplanner.library.to_planner"), b -> {
				this.minecraft.gui.setScreen(null);
				PlacementTool.startPasting(entry);
			}).bounds(left + NAME_WIDTH + 128, y, 62, 20).build();
			toPlanner.setTooltip(Tooltip.create(Component.translatable("blueprintplanner.library.to_planner.tooltip")));
			toPlanner.active = planner;
			this.addRenderableWidget(toPlanner);
			this.addRenderableWidget(Button.builder(Component.literal("✖"), b -> this.minecraft.gui.setScreen(new ConfirmScreen(ok -> {
				if (ok) {
					BlueprintLibrary.delete(entry);
				}
				this.minecraft.gui.setScreen(this);
			}, Component.translatable("blueprintplanner.library.delete"), Component.literal(entry.displayName())))).bounds(left + NAME_WIDTH + 192, y, 370 - NAME_WIDTH - 192, 20).build());
			y += ROW;
		}
		this.pager(this.entries.size(), perPage);
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.library.open_folder"),
			b -> Blaze3D.openPath(BlueprintLibrary.directory())).bounds(left, this.height - 50, 183, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.tools.save"),
			b -> this.minecraft.gui.setScreen(new SaveBlueprintScreen(this))).bounds(left + 187, this.height - 50, 183, 20).build())
			.active = Selection.get().isComplete();
	}

	// ------------------------------------------------------------------ placements

	private void initPlacements(int left) {
		List<Placement> placements = PlacementManager.placements();
		int perPage = Math.max(1, this.rowsPerPage() - 6);
		int y = 50;
		int from = this.page * perPage;
		for (int i = from; i < Math.min(placements.size(), from + perPage); i++) {
			int index = i;
			Placement placement = placements.get(i);
			boolean isSelected = index == PlacementManager.selectedIndex();
			Button select = Button.builder(Component.literal((isSelected ? "▶ " : "") + placement.name), b -> {
				PlacementManager.select(index);
				this.rebuildWidgets();
			}).bounds(left, y, 230, 20).build();
			this.addRenderableWidget(select);
			this.addRenderableWidget(Button.builder(Component.translatable(placement.visible ? "blueprintplanner.placement.visible" : "blueprintplanner.placement.hidden"), b -> {
				placement.visible = !placement.visible;
				PlacementManager.changed();
				this.rebuildWidgets();
			}).bounds(left + 232, y, 90, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("✖"), b -> {
				PlacementManager.remove(placement);
				this.rebuildWidgets();
			}).bounds(left + 324, y, 46, 20).build());
			y += ROW;
		}
		this.pager(placements.size(), perPage);

		Placement selected = PlacementManager.selected();
		int controlsY = this.height - 26 - ROW * 5 - 4;
		if (selected == null) {
			return;
		}
		int w = 72;
		int x = left;
		Button move = Button.builder(Component.translatable("blueprintplanner.placement.move"), b -> {
			this.minecraft.gui.setScreen(null);
			PlacementTool.pickUp(selected);
		}).bounds(x, controlsY, 183, 20).build();
		move.setTooltip(Tooltip.create(Component.translatable("blueprintplanner.placement.move.tooltip")));
		move.active = !PlannerManager.isActive() && this.minecraft.player != null && !this.minecraft.player.isSpectator();
		this.addRenderableWidget(move);
		Button intoSelection = Button.builder(Component.translatable("blueprintplanner.placement.into_selection"), b -> {
			BlueprintActions.fitIntoSelection(selected);
			this.rebuildWidgets();
		}).bounds(x + 187, controlsY, 183, 20).build();
		intoSelection.setTooltip(Tooltip.create(Component.translatable("blueprintplanner.library.into_selection.tooltip")));
		intoSelection.active = Selection.get().first() != null || Selection.get().second() != null;
		this.addRenderableWidget(intoSelection);
		controlsY += ROW;
		this.control(x, controlsY, w, "blueprintplanner.placement.rotate", () -> selected.rotateClockwise());
		this.control(x + 74, controlsY, w, "blueprintplanner.placement.mirror", () -> selected.toggleMirror());
		this.control(x + 148, controlsY, w, "blueprintplanner.placement.here", () -> {
			if (this.minecraft.player != null) {
				selected.setOrigin(this.minecraft.player.blockPosition());
			}
		});
		this.control(x + 222, controlsY, w, selected.includeAir ? "blueprintplanner.placement.air_on" : "blueprintplanner.placement.air_off", () -> {
			selected.includeAir = !selected.includeAir;
			selected.invalidate();
		});
		this.control(x + 296, controlsY, 74, "blueprintplanner.placement.select_area", () -> {
			BoundingBox box = selected.box();
			if (box != null) {
				Selection.get().set(box);
			}
		});

		int y2 = controlsY + ROW;
		String[] labels = {"X-", "X+", "Y-", "Y+", "Z-", "Z+"};
		int[][] deltas = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
		for (int i = 0; i < labels.length; i++) {
			int[] d = deltas[i];
			this.addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> {
				selected.move(d[0], d[1], d[2]);
				PlacementManager.changed();
			}).bounds(x + i * 38, y2, 36, 20).build());
		}
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.placement.materials"),
			b -> this.minecraft.gui.setScreen(new MaterialListScreen(this, selected))).bounds(x + 230, y2, 140, 20).build());

		int yLayer = y2 + ROW;
		boolean layerMode = PlacementManager.layerMode();
		Button layerToggle = Button.builder(Component.translatable(layerMode ? "blueprintplanner.placement.layer_on" : "blueprintplanner.placement.layer_off",
			PlacementManager.layer(), Objects.requireNonNullElse(PlacementManager.layerLabel(), "")), b -> {
			PlacementManager.setLayerMode(!layerMode, PlacementManager.defaultLayer(this.minecraft));
			this.rebuildWidgets();
		}).bounds(x, yLayer, 183, 20).build();
		layerToggle.setTooltip(Tooltip.create(Component.translatable("blueprintplanner.placement.layer.tooltip",
			BlueprintPlanner.layerDown.getTranslatedKeyMessage(), BlueprintPlanner.layerUp.getTranslatedKeyMessage(), BlueprintPlanner.layerToggle.getTranslatedKeyMessage())));
		this.addRenderableWidget(layerToggle);
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.placement.layer_down"), b -> {
			PlacementManager.moveLayer(this.minecraft, -1);
			this.rebuildWidgets();
		}).bounds(x + 187, yLayer, 90, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.placement.layer_up"), b -> {
			PlacementManager.moveLayer(this.minecraft, 1);
			this.rebuildWidgets();
		}).bounds(x + 280, yLayer, 90, 20).build());

		int y3 = yLayer + ROW;
		Button paste = Button.builder(Component.translatable(Paster.isRunning() ? "blueprintplanner.placement.paste_stop" : layerMode ? "blueprintplanner.placement.paste_layer" : "blueprintplanner.placement.paste"), b -> {
			if (Paster.isRunning()) {
				Paster.cancel();
				this.rebuildWidgets();
			} else {
				this.minecraft.gui.setScreen(new ConfirmScreen(ok -> {
					if (ok) {
						Paster.paste(selected);
					}
					this.minecraft.gui.setScreen(this);
				}, Component.translatable("blueprintplanner.placement.paste"), Component.translatable("blueprintplanner.placement.paste_confirm")));
			}
		}).bounds(x, y3, 183, 20).build();
		paste.active = Paster.isRunning() || (Paster.canPaste() && !PlannerManager.isActive());
		this.addRenderableWidget(paste);
		this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.placement.to_planner"), b -> {
			var blueprint = selected.blueprint();
			if (blueprint != null) {
				dev.blueprintplanner.blueprint.EditTools.pasteBlueprint(blueprint, selected.origin(), selected.includeAir, true);
				this.onClose();
			}
		}).bounds(x + 187, y3, 183, 20).build()).active = PlannerManager.isActive();
	}

	private void control(int x, int y, int w, String key, Runnable action) {
		this.addRenderableWidget(Button.builder(Component.translatable(key), b -> {
			action.run();
			PlacementManager.changed();
			this.rebuildWidgets();
		}).bounds(x, y, w, 20).build());
	}

	private void pager(int total, int perPage) {
		int pages = Math.max(1, (total + perPage - 1) / perPage);
		if (pages <= 1) {
			return;
		}
		int y = 50 + perPage * ROW + 2;
		this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			this.page = Math.max(0, this.page - 1);
			this.rebuildWidgets();
		}).bounds(this.width / 2 - 50, y, 20, 20).build()).active = this.page > 0;
		this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			this.page = Math.min(pages - 1, this.page + 1);
			this.rebuildWidgets();
		}).bounds(this.width / 2 + 30, y, 20, 20).build()).active = this.page < pages - 1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
		int left = this.width / 2 - 185;
		if (tab == 0) {
			int perPage = this.rowsPerPage();
			int y = 50;
			int from = this.page * perPage;
			for (int i = from; i < Math.min(this.entries.size(), from + perPage); i++) {
				BlueprintLibrary.Entry entry = this.entries.get(i);
				String name = this.font.plainSubstrByWidth(entry.displayName(), NAME_WIDTH - 4);
				graphics.text(this.font, name, left + 2, y + 6, 0xFFFFFFFF);
				y += ROW;
			}
			if (this.entries.isEmpty()) {
				graphics.centeredText(this.font, Component.translatable("blueprintplanner.library.empty"), this.width / 2, 70, 0xFFAAAAAA);
			}
		} else {
			Placement selected = PlacementManager.selected();
			if (PlacementManager.placements().isEmpty()) {
				graphics.centeredText(this.font, Component.translatable("blueprintplanner.placement.empty"), this.width / 2, 70, 0xFFAAAAAA);
			}
			if (selected != null) {
				BlockPos origin = selected.origin();
				var blueprint = selected.blueprint();
				Component info = blueprint == null
					? Component.translatable("blueprintplanner.load.failed", selected.file)
					: Component.translatable("blueprintplanner.placement.info", origin.getX(), origin.getY(), origin.getZ(),
						blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ(), selected.rotation.getSerializedName(), selected.mirror.getSerializedName());
				graphics.centeredText(this.font, info, this.width / 2, this.height - 26 - ROW * 5 - 16, 0xFF55FFFF);
			}
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
