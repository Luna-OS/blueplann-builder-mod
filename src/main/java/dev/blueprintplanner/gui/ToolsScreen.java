package dev.blueprintplanner.gui;

import dev.blueprintplanner.blueprint.EditTools;
import dev.blueprintplanner.blueprint.Selection;
import dev.blueprintplanner.planner.PlannerManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * WorldEdit-like building tools without commands. Block A = item in the main hand,
 * block B = item in the off hand. Directions follow where you look.
 */
public class ToolsScreen extends Screen {
	private static final int ROW = 22;
	private static int distance = 1;

	private @Nullable BlockState blockA;
	private @Nullable BlockState blockB;
	private Direction lookDirection = Direction.NORTH;
	private @Nullable BlockPos pasteTarget;

	public ToolsScreen() {
		super(Component.translatable("blueprintplanner.tools.title"));
	}

	@Override
	protected void init() {
		if (this.minecraft.player != null) {
			this.blockA = blockOf(this.minecraft.player.getMainHandItem());
			this.blockB = blockOf(this.minecraft.player.getOffhandItem());
			this.lookDirection = this.minecraft.player.getNearestViewDirection();
			if (this.minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
				this.pasteTarget = hit.getBlockPos().relative(hit.getDirection());
			}
		}
		boolean planner = PlannerManager.isActive();
		boolean selection = Selection.get().isComplete();

		int w = 120;
		int gap = 4;
		int left = this.width / 2 - (w * 3 + gap * 2) / 2;
		int c2 = left + w + gap;
		int c3 = c2 + w + gap;
		int y = 44;

		this.button(left, y, w, "blueprintplanner.tools.fill", planner && selection && this.blockA != null, () -> EditTools.fill(this.blockA));
		this.button(c2, y, w, "blueprintplanner.tools.replace", planner && selection && this.blockA != null && this.blockB != null,
			() -> EditTools.replace(this.blockB.getBlock(), this.blockA));
		this.button(c3, y, w, "blueprintplanner.tools.clear", planner && selection, EditTools::clear);
		y += ROW;

		this.button(left, y, w, "blueprintplanner.tools.walls", planner && selection && this.blockA != null, () -> EditTools.walls(this.blockA));
		this.button(c2, y, w, "blueprintplanner.tools.shell", planner && selection && this.blockA != null, () -> EditTools.shell(this.blockA));
		this.button(c3, y, w, "blueprintplanner.tools.frame", planner && selection && this.blockA != null, () -> EditTools.frame(this.blockA));
		y += ROW;

		this.button(left, y, w, "blueprintplanner.tools.copy", planner && selection, () -> EditTools.copy(false));
		this.button(c2, y, w, "blueprintplanner.tools.cut", planner && selection, () -> EditTools.copy(true));
		this.button(c3, y, w, "blueprintplanner.tools.paste", planner && EditTools.clipboard() != null && this.pasteTarget != null,
			() -> EditTools.paste(this.pasteTarget, false));
		y += ROW;

		this.button(left, y, w, "blueprintplanner.tools.rotate", EditTools.clipboard() != null, EditTools::rotateClipboard, false);
		this.button(c2, y, w, "blueprintplanner.tools.mirror", EditTools.clipboard() != null, EditTools::mirrorClipboard, false);
		this.button(c3, y, w, "blueprintplanner.tools.paste_air", planner && EditTools.clipboard() != null && this.pasteTarget != null,
			() -> EditTools.paste(this.pasteTarget, true));
		y += ROW;

		this.addRenderableWidget(new AbstractSliderButton(left, y, w, 20, Component.empty(), (distance - 1) / 63.0) {
			{
				this.updateMessage();
			}

			@Override
			protected void updateMessage() {
				this.setMessage(Component.translatable("blueprintplanner.tools.distance", distance));
			}

			@Override
			protected void applyValue() {
				distance = 1 + (int) Math.round(this.value * 63);
			}
		});
		this.button(c2, y, w, Component.translatable("blueprintplanner.tools.move", directionName(this.lookDirection)), planner && selection,
			() -> EditTools.move(this.lookDirection, distance));
		this.button(c3, y, w, Component.translatable("blueprintplanner.tools.stack", directionName(this.lookDirection)), planner && selection,
			() -> EditTools.stack(this.lookDirection, distance));
		y += ROW;

		this.button(left, y, w, "blueprintplanner.tools.undo", planner && EditTools.canUndo(), EditTools::undo);
		this.button(c2, y, w, "blueprintplanner.tools.redo", planner && EditTools.canRedo(), EditTools::redo);
		this.button(c3, y, w, "blueprintplanner.tools.deselect", selection, () -> Selection.get().clear(), false);
		y += ROW;

		this.button(left, y, w * 3 / 2 + 1, "blueprintplanner.tools.save", selection, () -> this.minecraft.gui.setScreen(new SaveBlueprintScreen(this)), false);
		this.button(left + w * 3 / 2 + gap + 3, y, w * 3 / 2 + 1, "blueprintplanner.tools.library", true,
			() -> this.minecraft.gui.setScreen(new BlueprintScreen(this)), false);

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
			.bounds(this.width / 2 - 100, this.height - 23, 200, 20).build());
	}

	private void button(int x, int y, int w, String key, boolean active, Runnable action) {
		this.button(x, y, w, Component.translatable(key), active, action, true);
	}

	private void button(int x, int y, int w, String key, boolean active, Runnable action, boolean closeAfter) {
		this.button(x, y, w, Component.translatable(key), active, action, closeAfter);
	}

	private void button(int x, int y, int w, Component text, boolean active, Runnable action) {
		this.button(x, y, w, text, active, action, true);
	}

	private void button(int x, int y, int w, Component text, boolean active, Runnable action, boolean closeAfter) {
		Button button = Button.builder(text, b -> {
			action.run();
			if (closeAfter) {
				this.onClose();
			} else {
				this.rebuildWidgets();
			}
		}).bounds(x, y, w, 20).build();
		button.active = active;
		this.addRenderableWidget(button);
	}

	private static @Nullable BlockState blockOf(ItemStack stack) {
		if (stack.getItem() instanceof BlockItem blockItem) {
			return blockItem.getBlock().defaultBlockState();
		}
		return null;
	}

	private static Component directionName(Direction direction) {
		return Component.translatable("blueprintplanner.direction." + direction.getSerializedName());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 5, 0xFFFFFFFF);
		Selection selection = Selection.get();
		Component selectionText = selection.isComplete()
			? Component.translatable("blueprintplanner.tools.selection", selection.sizeX(), selection.sizeY(), selection.sizeZ(), selection.volume())
			: Component.translatable("blueprintplanner.tools.selection_hint");
		graphics.centeredText(this.font, selectionText, this.width / 2, 17, 0xFFFFAA00);
		Component blocks = Component.translatable("blueprintplanner.tools.blocks",
			this.blockA != null ? this.blockA.getBlock().getName() : Component.translatable("blueprintplanner.tools.none"),
			this.blockB != null ? this.blockB.getBlock().getName() : Component.translatable("blueprintplanner.tools.none"));
		graphics.centeredText(this.font, blocks, this.width / 2, 29, 0xFFAAAAAA);
		if (!PlannerManager.isActive()) {
			graphics.centeredText(this.font, Component.translatable("blueprintplanner.tools.planner_only"), this.width / 2, this.height - 34, 0xFFFF5555);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
