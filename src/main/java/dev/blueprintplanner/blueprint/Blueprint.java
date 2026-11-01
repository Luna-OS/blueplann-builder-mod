package dev.blueprintplanner.blueprint;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

/**
 * A captured box of blocks. Saved in the vanilla structure format ({@code .nbt}), so blueprints can also be
 * loaded by structure blocks and vice versa.
 */
public final class Blueprint {
	public static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

	private String name;
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final List<BlockState> palette;
	private final int[] blocks;
	private final Int2ObjectMap<CompoundTag> blockEntities;
	private @Nullable BlockPos origin;
	private String author = "";
	private String world = "";
	private long created = System.currentTimeMillis();

	private Blueprint(String name, int sizeX, int sizeY, int sizeZ, List<BlockState> palette, int[] blocks, Int2ObjectMap<CompoundTag> blockEntities) {
		this.name = name;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.palette = palette;
		this.blocks = blocks;
		this.blockEntities = blockEntities;
	}

	// ------------------------------------------------------------------ capture

	public static Blueprint capture(Level level, BoundingBox box, String name) {
		int sx = box.getXSpan();
		int sy = box.getYSpan();
		int sz = box.getZSpan();
		Builder builder = new Builder(sx, sy, sz);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = 0; y < sy; y++) {
			for (int z = 0; z < sz; z++) {
				for (int x = 0; x < sx; x++) {
					pos.set(box.minX() + x, box.minY() + y, box.minZ() + z);
					BlockState state = level.getBlockState(pos);
					CompoundTag beTag = null;
					if (state.hasBlockEntity()) {
						BlockEntity be = level.getBlockEntity(pos);
						if (be != null) {
							beTag = be.saveWithoutMetadata(level.registryAccess());
						}
					}
					builder.set(x, y, z, state, beTag);
				}
			}
		}
		Blueprint blueprint = builder.build(name);
		blueprint.origin = new BlockPos(box.minX(), box.minY(), box.minZ());
		return blueprint;
	}

	// ------------------------------------------------------------------ access

	public String name() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public @Nullable BlockPos origin() {
		return this.origin;
	}

	public void setOrigin(@Nullable BlockPos origin) {
		this.origin = origin;
	}

	public String author() {
		return this.author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	/** Planner/real world key the blueprint was made in (used to place it back at its original position). */
	public String world() {
		return this.world;
	}

	public void setWorld(String world) {
		this.world = world;
	}

	public long created() {
		return this.created;
	}

	public int sizeX() {
		return this.sizeX;
	}

	public int sizeY() {
		return this.sizeY;
	}

	public int sizeZ() {
		return this.sizeZ;
	}

	public Vec3i size() {
		return new Vec3i(this.sizeX, this.sizeY, this.sizeZ);
	}

	public int index(int x, int y, int z) {
		return (y * this.sizeZ + z) * this.sizeX + x;
	}

	public BlockState get(int x, int y, int z) {
		return this.palette.get(this.blocks[this.index(x, y, z)]);
	}

	public @Nullable CompoundTag blockEntity(int x, int y, int z) {
		return this.blockEntities.get(this.index(x, y, z));
	}

	public int nonAirCount() {
		int count = 0;
		for (int id : this.blocks) {
			if (!this.palette.get(id).isAir()) {
				count++;
			}
		}
		return count;
	}

	public interface BlockVisitor {
		void visit(int x, int y, int z, BlockState state, @Nullable CompoundTag blockEntity);
	}

	public void forEach(BlockVisitor visitor) {
		for (int y = 0; y < this.sizeY; y++) {
			for (int z = 0; z < this.sizeZ; z++) {
				for (int x = 0; x < this.sizeX; x++) {
					int i = this.index(x, y, z);
					visitor.visit(x, y, z, this.palette.get(this.blocks[i]), this.blockEntities.get(i));
				}
			}
		}
	}

	// ------------------------------------------------------------------ transformation

	/** Returns a rotated/mirrored copy (states rotated too). Mirror is applied before rotation, like structure blocks. */
	public Blueprint transformed(Rotation rotation, Mirror mirror) {
		if (rotation == Rotation.NONE && mirror == Mirror.NONE) {
			return this;
		}
		BlockPos far = StructureTemplate.transform(new BlockPos(this.sizeX - 1, this.sizeY - 1, this.sizeZ - 1), mirror, rotation, BlockPos.ZERO);
		BlockPos zero = StructureTemplate.transform(BlockPos.ZERO, mirror, rotation, BlockPos.ZERO);
		int minX = Math.min(far.getX(), zero.getX());
		int minZ = Math.min(far.getZ(), zero.getZ());
		int nx = Math.abs(far.getX() - zero.getX()) + 1;
		int nz = Math.abs(far.getZ() - zero.getZ()) + 1;
		Builder builder = new Builder(nx, this.sizeY, nz);
		this.forEach((x, y, z, state, be) -> {
			BlockPos t = StructureTemplate.transform(new BlockPos(x, y, z), mirror, rotation, BlockPos.ZERO);
			builder.set(t.getX() - minX, y, t.getZ() - minZ, state.mirror(mirror).rotate(rotation), be);
		});
		Blueprint result = builder.build(this.name);
		result.origin = this.origin;
		result.author = this.author;
		result.world = this.world;
		result.created = this.created;
		return result;
	}

	// ------------------------------------------------------------------ placing (server thread only)

	/** Pastes into a server level. Block entities get their saved data back. */
	public void pasteInto(ServerLevel level, BlockPos at, boolean includeAir, boolean updateNeighbors) {
		this.pasteInto(level, at, includeAir, updateNeighbors, null);
	}

	/** With {@code onlyY} set, just the blocks of that world height. */
	public void pasteInto(ServerLevel level, BlockPos at, boolean includeAir, boolean updateNeighbors, @Nullable Integer onlyY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int flags = updateNeighbors ? Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS : SET_FLAGS;
		this.forEach((x, y, z, state, be) -> {
			if (state.isAir() && !includeAir || onlyY != null && at.getY() + y != onlyY) {
				return;
			}
			pos.set(at.getX() + x, at.getY() + y, at.getZ() + z);
			if (!level.isInWorldBounds(pos)) {
				return;
			}
			BlockEntity existing = level.getBlockEntity(pos);
			if (existing != null) {
				// avoid dropping container contents when replacing
				level.removeBlockEntity(pos);
			}
			level.setBlock(pos, state, flags);
			if (be != null) {
				BlockEntity created = level.getBlockEntity(pos);
				if (created != null) {
					created.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), be));
					created.setChanged();
					level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
				}
			}
		});
	}

	// ------------------------------------------------------------------ (de)serialization

	public CompoundTag toNbt() {
		CompoundTag root = new CompoundTag();
		ListTag size = new ListTag();
		size.add(IntTag.valueOf(this.sizeX));
		size.add(IntTag.valueOf(this.sizeY));
		size.add(IntTag.valueOf(this.sizeZ));
		root.put("size", size);

		ListTag paletteTag = new ListTag();
		for (BlockState state : this.palette) {
			paletteTag.add(NbtUtils.writeBlockState(state));
		}
		root.put("palette", paletteTag);

		ListTag blocksTag = new ListTag();
		this.forEach((x, y, z, state, be) -> {
			CompoundTag entry = new CompoundTag();
			ListTag pos = new ListTag();
			pos.add(IntTag.valueOf(x));
			pos.add(IntTag.valueOf(y));
			pos.add(IntTag.valueOf(z));
			entry.put("pos", pos);
			entry.putInt("state", this.blocks[this.index(x, y, z)]);
			if (be != null) {
				entry.put("nbt", be.copy());
			}
			blocksTag.add(entry);
		});
		root.put("blocks", blocksTag);
		root.put("entities", new ListTag());

		CompoundTag meta = new CompoundTag();
		meta.putString("name", this.name);
		meta.putString("author", this.author);
		meta.putString("world", this.world);
		meta.putLong("created", this.created);
		if (this.origin != null) {
			meta.putIntArray("origin", new int[] {this.origin.getX(), this.origin.getY(), this.origin.getZ()});
		}
		root.put("blueprintplanner", meta);
		return NbtUtils.addCurrentDataVersion(root);
	}

	public static Blueprint fromNbt(CompoundTag raw, String fallbackName) {
		int dataVersion = NbtUtils.getDataVersion(raw, 500);
		CompoundTag tag = dataVersion < SharedConstants.getCurrentVersion().dataVersion().version()
			? DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), raw, dataVersion)
			: raw;

		ListTag size = tag.getListOrEmpty("size");
		int sx = Math.max(1, size.getIntOr(0, 1));
		int sy = Math.max(1, size.getIntOr(1, 1));
		int sz = Math.max(1, size.getIntOr(2, 1));

		HolderGetter<Block> blockLookup = BuiltInRegistries.BLOCK;
		ListTag paletteTag = tag.getList("palette").orElseGet(() -> tag.getListOrEmpty("palettes").getListOrEmpty(0));
		List<BlockState> palette = new ArrayList<>();
		for (Tag entry : paletteTag) {
			palette.add(entry instanceof CompoundTag ct ? NbtUtils.readBlockState(blockLookup, ct) : Blocks.AIR.defaultBlockState());
		}

		// Positions not listed in the file (structure voids) are treated as air.
		Builder builder = new Builder(sx, sy, sz);
		for (Tag entry : tag.getListOrEmpty("blocks")) {
			if (!(entry instanceof CompoundTag block)) {
				continue;
			}
			ListTag pos = block.getListOrEmpty("pos");
			int x = pos.getIntOr(0, 0);
			int y = pos.getIntOr(1, 0);
			int z = pos.getIntOr(2, 0);
			int stateId = block.getIntOr("state", 0);
			if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz || stateId < 0 || stateId >= palette.size()) {
				continue;
			}
			builder.set(x, y, z, palette.get(stateId), block.getCompound("nbt").orElse(null));
		}

		CompoundTag meta = tag.getCompoundOrEmpty("blueprintplanner");
		Blueprint blueprint = builder.build(meta.getStringOr("name", fallbackName));
		blueprint.author = meta.getStringOr("author", "");
		blueprint.world = meta.getStringOr("world", "");
		blueprint.created = meta.getLongOr("created", System.currentTimeMillis());
		Optional<int[]> origin = meta.getIntArray("origin");
		if (origin.isPresent() && origin.get().length == 3) {
			blueprint.origin = new BlockPos(origin.get()[0], origin.get()[1], origin.get()[2]);
		}
		return blueprint;
	}

	// ------------------------------------------------------------------ builder

	public static final class Builder {
		private final int sx;
		private final int sy;
		private final int sz;
		private final List<BlockState> palette = new ArrayList<>();
		private final Object2IntMap<BlockState> ids = new Object2IntOpenHashMap<>();
		private final int[] blocks;
		private final Int2ObjectMap<CompoundTag> blockEntities = new Int2ObjectOpenHashMap<>();

		public Builder(int sx, int sy, int sz) {
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
			this.blocks = new int[sx * sy * sz];
			this.ids.defaultReturnValue(-1);
			this.id(Blocks.AIR.defaultBlockState());
		}

		private int id(BlockState state) {
			int id = this.ids.getInt(state);
			if (id < 0) {
				id = this.palette.size();
				this.palette.add(state);
				this.ids.put(state, id);
			}
			return id;
		}

		public void set(int x, int y, int z, BlockState state, @Nullable CompoundTag blockEntity) {
			int index = (y * this.sz + z) * this.sx + x;
			this.blocks[index] = this.id(state);
			if (blockEntity != null) {
				this.blockEntities.put(index, blockEntity);
			} else {
				this.blockEntities.remove(index);
			}
		}

		public Blueprint build(String name) {
			return new Blueprint(name, this.sx, this.sy, this.sz, this.palette, this.blocks, this.blockEntities);
		}
	}
}
