package dev.blueprintplanner.blueprint;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/** A blueprint placed as a hologram into the real world. */
public final class Placement {
	public String file;
	public String name;
	public int x;
	public int y;
	public int z;
	public Rotation rotation = Rotation.NONE;
	public Mirror mirror = Mirror.NONE;
	public boolean visible = true;
	/** Treat air of the blueprint as "must be empty" (shows extra blocks as errors, paste clears them). */
	public boolean includeAir = false;

	private transient @Nullable Blueprint source;
	private transient @Nullable Blueprint transformed;
	private transient @Nullable List<Target> targets;
	/** A missing blueprint file is looked for again only now and then (box() runs every frame). */
	private transient long nextLoadAttempt;

	/** One block that the plan wants at a world position. */
	public record Target(BlockPos pos, BlockState state) {
	}

	public Placement() {
	}

	public Placement(String file, Blueprint blueprint, BlockPos origin) {
		this.file = file;
		this.name = blueprint.name();
		this.x = origin.getX();
		this.y = origin.getY();
		this.z = origin.getZ();
		this.source = blueprint;
	}

	/** A copy of this placement at another spot (shares the loaded blueprint). */
	public Placement copyAt(BlockPos origin, Rotation rotation, Mirror mirror, boolean visible) {
		Placement copy = new Placement();
		copy.file = this.file;
		copy.name = this.name;
		copy.setOrigin(origin);
		copy.rotation = rotation;
		copy.mirror = mirror;
		copy.visible = visible;
		copy.includeAir = this.includeAir;
		copy.source = this.source;
		return copy;
	}

	public BlockPos origin() {
		return new BlockPos(this.x, this.y, this.z);
	}

	public void setOrigin(BlockPos pos) {
		this.x = pos.getX();
		this.y = pos.getY();
		this.z = pos.getZ();
		this.targets = null;
	}

	public void move(int dx, int dy, int dz) {
		this.setOrigin(this.origin().offset(dx, dy, dz));
	}

	/** Turns the placement by 90° around its middle (it does not wander off to the side). */
	public void rotateClockwise() {
		BoundingBox before = this.box();
		this.rotation = this.rotation.getRotated(Rotation.CLOCKWISE_90);
		this.invalidate();
		BoundingBox after = this.box();
		if (before != null && after != null) {
			this.setOrigin(this.origin().offset((before.getXSpan() - after.getXSpan()) / 2, 0, (before.getZSpan() - after.getZSpan()) / 2));
		}
	}

	public void toggleMirror() {
		this.mirror = switch (this.mirror) {
			case NONE -> Mirror.FRONT_BACK;
			case FRONT_BACK -> Mirror.LEFT_RIGHT;
			case LEFT_RIGHT -> Mirror.NONE;
		};
		this.invalidate();
	}

	public void invalidate() {
		this.transformed = null;
		this.targets = null;
	}

	public boolean isLoaded() {
		return this.source != null || this.load() != null;
	}

	private @Nullable Blueprint load() {
		if (this.source == null && this.file != null && Util.getMillis() >= this.nextLoadAttempt) {
			this.source = BlueprintLibrary.loadByFileName(this.file);
			this.nextLoadAttempt = Util.getMillis() + 5000L;
		}
		return this.source;
	}

	public @Nullable Blueprint blueprint() {
		Blueprint base = this.load();
		if (base == null) {
			return null;
		}
		if (this.transformed == null) {
			this.transformed = base.transformed(this.rotation, this.mirror);
		}
		return this.transformed;
	}

	public @Nullable BoundingBox box() {
		Blueprint b = this.blueprint();
		if (b == null) {
			return null;
		}
		return new BoundingBox(this.x, this.y, this.z, this.x + b.sizeX() - 1, this.y + b.sizeY() - 1, this.z + b.sizeZ() - 1);
	}

	/** All positions this plan cares about, in world coordinates. */
	public List<Target> targets() {
		if (this.targets == null) {
			Blueprint b = this.blueprint();
			List<Target> list = new ArrayList<>();
			if (b != null) {
				b.forEach((bx, by, bz, state, be) -> {
					if (!state.isAir() || this.includeAir) {
						list.add(new Target(new BlockPos(this.x + bx, this.y + by, this.z + bz), state));
					}
				});
			}
			this.targets = list;
		}
		return this.targets;
	}

	public @Nullable BlockState targetAt(BlockPos pos) {
		Blueprint b = this.blueprint();
		if (b == null) {
			return null;
		}
		int bx = pos.getX() - this.x;
		int by = pos.getY() - this.y;
		int bz = pos.getZ() - this.z;
		if (bx < 0 || by < 0 || bz < 0 || bx >= b.sizeX() || by >= b.sizeY() || bz >= b.sizeZ()) {
			return null;
		}
		BlockState state = b.get(bx, by, bz);
		return state.isAir() && !this.includeAir ? null : state;
	}
}
