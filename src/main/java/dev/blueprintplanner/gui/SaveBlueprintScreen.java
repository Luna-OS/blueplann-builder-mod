package dev.blueprintplanner.gui;

import dev.blueprintplanner.blueprint.BlueprintActions;
import dev.blueprintplanner.blueprint.BlueprintLibrary;
import dev.blueprintplanner.blueprint.Selection;
import dev.blueprintplanner.planner.PlannerManager;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Name the selection and save it as a blueprint. */
public class SaveBlueprintScreen extends Screen {
	private static boolean placeInRealWorld = true;

	private final @Nullable Screen parent;
	private EditBox nameBox;
	private Button saveButton;

	public SaveBlueprintScreen(@Nullable Screen parent) {
		super(Component.translatable("blueprintplanner.save.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - 150;
		this.nameBox = new EditBox(this.font, left, 60, 300, 20, Component.translatable("blueprintplanner.save.name"));
		this.nameBox.setMaxLength(64);
		this.nameBox.setValue("plan_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")));
		this.nameBox.setResponder(text -> this.updateButtons());
		this.addRenderableWidget(this.nameBox);
		this.setInitialFocus(this.nameBox);

		if (PlannerManager.isActive()) {
			this.addRenderableWidget(CycleButton.onOffBuilder(placeInRealWorld)
				.create(left, 88, 300, 20, Component.translatable("blueprintplanner.save.place_real"), (b, v) -> placeInRealWorld = v));
		}

		this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("blueprintplanner.save.button"), b -> this.save())
			.bounds(left, 120, 145, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose()).bounds(left + 155, 120, 145, 20).build());
		this.updateButtons();
	}

	private void updateButtons() {
		this.saveButton.active = !this.nameBox.getValue().isBlank() && Selection.get().isComplete();
	}

	private void save() {
		String name = this.nameBox.getValue().trim();
		boolean place = PlannerManager.isActive() && placeInRealWorld;
		this.saveButton.active = false;
		BlueprintActions.saveSelection(name, place, file -> this.minecraft.gui.setScreen(this.parent));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
		Selection selection = Selection.get();
		graphics.centeredText(this.font, Component.translatable("blueprintplanner.tools.selection",
			selection.sizeX(), selection.sizeY(), selection.sizeZ(), selection.volume()), this.width / 2, 36, 0xFFFFAA00);
		if (BlueprintLibrary.exists(this.nameBox.getValue())) {
			graphics.centeredText(this.font, Component.translatable("blueprintplanner.save.overwrite"), this.width / 2, 148, 0xFFFF5555);
		}
		graphics.centeredText(this.font, Component.translatable(PlannerManager.isActive()
			? "blueprintplanner.save.source_planner" : "blueprintplanner.save.source_real"), this.width / 2, 164, 0xFF888888);
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
