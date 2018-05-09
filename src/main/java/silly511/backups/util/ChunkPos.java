package silly511.backups.util;

import net.minecraft.util.BlockPos;

public class ChunkPos {
	
	public final int chunkXPos, chunkZPos;

	public ChunkPos(int x, int z) {
		this.chunkXPos = x;
		this.chunkZPos = z;
	}
	
	public ChunkPos(BlockPos blockPos) {
		this(blockPos.getX() >> 4, blockPos.getZ() >> 4);
	}

	@Override
	public int hashCode() {
		int i = 1664525 * this.chunkXPos + 1013904223;
		int j = 1664525 * (this.chunkZPos ^ -559038737) + 1013904223;
		return i ^ j;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof ChunkPos)) return false;
		
		ChunkPos other = (ChunkPos) obj;
		
		return chunkXPos == other.chunkXPos && chunkZPos == other.chunkZPos;
	}

}
