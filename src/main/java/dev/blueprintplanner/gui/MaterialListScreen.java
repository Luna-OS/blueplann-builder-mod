package dev.blueprintplanner.gui;

import dev.blueprintplanner.blueprint.MaterialList;
import dev.blueprintplanner.blueprint.Placement;
import dev.blueprintplanner.blueprint.PlacementManager;
import dev.blueprintplanner.planner.PlannerManager;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Items needed for a placement: total, still missing in the world, and what is in the inventory. */
public class MaterialListScreen extends Screen {
	private static final int ROW = 18;
	private final @Nullable Screen parent;
	private final Placement placement;
	private List<MaterialList.Line> lines = List.of();
	private int page;
	private boolean onlyMissing = true;
	/** In layer mode: list only the shown layer. */
	private boolean onlyLayer = true;

	public MaterialListScreen(@Nullable Screen parent, Placement placement) {
		super(Component.translatable("blueprintplanner.materials.title", placement.name));
		this.parent = parent;
		this.placement = placement;
	}

	@Override
	protected void init() {
		LocalPlayer player = PlannerManager.realPlayer();
		boolean layerMode = PlacementManager.layerMode() && PlacementManager.selected() == this.placement;
		this.lines = MaterialList.compute(this.placement, PlannerManager.realLevel(), player != null ? player.getInventory() : null,
			layerMode && this.onlyLayer ? PlacementManager.layer() : null);
		if (this.onlyMissing) {
			this.lines = this.lines.stream().filter(l -> l.missing() > 0).toList();
		}
		int perPage = this.perPage();
		int pages = Math.max(1, (this.lines.size() + perPage - 1) / perPage);
		this.page = Math.min(this.page, pages - 1);

		this.addRenderableWidget(Button.builder(Component.translatable(this.onlyMissing ? "blueprintplanner.materials.only_missing" : "blueprintplanner.materials.all"), b -> {
			this.onlyMissing = !this.onlyMissing;
			this.rebuildWidgets();
		}).bounds(this.width / 2 - 150, this.height - 26, 96, 20).build());
		if (layerMode) {
			this.addRenderableWidget(Button.builder(Component.translatable(this.onlyLayer ? "blueprintplanner.materials.layer" : "blueprintplanner.materials.whole", PlacementManager.layer()), b -> {
				this.onlyLayer = !this.onlyLayer;
				this.rebuildWidgets();
			}).bounds(this.width / 2 - 150, this.height - 50, 96, 20).build());
		}
		this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			this.page--;
			this.rebuildWidgets();
		}).bounds(this.width / 2 - 50, this.height - 26, 20, 20).build()).active = this.page > 0;
		this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			this.page++;
			this.rebuildWidgets();
		}).bounds(this.width / 2 - 26, this.height - 26, 20, 20).build()).active = this.page < pages - 1;
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
			.bounds(this.width / 2 + 4, this.height - 26, 146, 20).build());
	}

	private int perPage() {
		return Math.max(1, (this.height - 80) / ROW);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
		int left = this.width / 2 - 150;
		int y = 36;
		graphics.text(this.font, Component.translatable("blueprintplanner.materials.header.item"), left + 20, 24, 0xFFAAAAAA);
		graphics.text(this.font, Component.translatable("blueprintplanner.materials.header.total"), left + 190, 24, 0xFFAAAAAA);
		graphics.text(this.font, Component.translatable("blueprintplanner.materials.header.missing"), left + 230, 24, 0xFFAAAAAA);
		graphics.text(this.font, Component.translatable("blueprintplanner.materials.header.have"), left + 272, 24, 0xFFAAAAAA);
		int perPage = this.perPage();
		int from = this.page * perPage;
		for (int i = from; i < Math.min(this.lines.size(), from + perPage); i++) {
			MaterialList.Line line = this.lines.get(i);
			ItemStack stack = new ItemStack(line.item());
			graphics.item(stack, left, y - 4);
			graphics.text(this.font, this.font.plainSubstrByWidth(stack.getHoverName().getString(), 165), left + 20, y, 0xFFFFFFFF);
			graphics.text(this.font, String.valueOf(line.total()), left + 190, y, 0xFFFFFFFF);
			graphics.text(this.font, String.valueOf(line.missing()), left + 230, y, line.missing() > 0 ? 0xFFFF7777 : 0xFF77FF77);
			int haveColor = line.inInventory() >= line.missing() ? 0xFF77FF77 : 0xFFFFAA55;
			graphics.text(this.font, String.valueOf(line.inInventory()), left + 272, y, haveColor);
			y += ROW;
		}
		if (this.lines.isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("blueprintplanner.materials.complete"), this.width / 2, 60, 0xFF77FF77);
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
