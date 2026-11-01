package dev.blueprintplanner.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * The area selection (two corners). Real world and planner share coordinates, so one selection is used
 * for both: select in the planner, leave it, and the same box is shown in the real world.
 */
public final class Selection {
	private static final Selection INSTANCE = new Selection();

	private @Nullable BlockPos first;
	private @Nullable BlockPos second;

	public static Selection get() {
		return INSTANCE;
	}

	public @Nullable BlockPos first() {
		return this.first;
	}

	public @Nullable BlockPos second() {
		return this.second;
	}

	public void setFirst(BlockPos pos) {
		this.first = pos.immutable();
	}

	public void setSecond(BlockPos pos) {
		this.second = pos.immutable();
	}

	public void clear() {
		this.first = null;
		this.second = null;
	}

	public boolean isComplete() {
		return this.first != null && this.second != null;
	}

	public void set(BoundingBox box) {
		this.first = new BlockPos(box.minX(), box.minY(), box.minZ());
		this.second = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
	}

	public @Nullable BoundingBox box() {
		if (!this.isComplete()) {
			return null;
		}
		return BoundingBox.fromCorners(this.first, this.second);
	}

	public int sizeX() {
		return this.isComplete() ? Math.abs(this.first.getX() - this.second.getX()) + 1 : 0;
	}

	public int sizeY() {
		return this.isComplete() ? Math.abs(this.first.getY() - this.second.getY()) + 1 : 0;
	}

	public int sizeZ() {
		return this.isComplete() ? Math.abs(this.first.getZ() - this.second.getZ()) + 1 : 0;
	}

	public long volume() {
		return (long) this.sizeX() * this.sizeY() * this.sizeZ();
	}
}
