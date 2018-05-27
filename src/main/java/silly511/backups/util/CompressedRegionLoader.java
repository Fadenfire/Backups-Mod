package silly511.backups.util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.NibbleArray;
import silly511.backups.BackupsMod;

public class CompressedRegionLoader {
	
	private final Map<ChunkPos, ChunkData> chunkCache = new HashMap<>();
	private File regionDir;
	
	public CompressedRegionLoader(File dimDir) {
		this.regionDir = new File(dimDir, "region");
	}
	
	public IBlockState getBlockState(BlockPos pos) {
		ChunkData chunk = getChunk(new ChunkPos(pos));
		if (chunk.isEmpty) return null;
		
		IBlockState state = chunk.blockStates[pos.getX() & 15][pos.getY()][pos.getZ() & 15];
		return state == null ? Blocks.air.getDefaultState() : state;
	}
	
	public NBTTagCompound getTileEntityData(BlockPos pos) {
		return getChunk(new ChunkPos(pos)).tileEntityData.get(pos);
	}
	
	public TileTick getTileTick(BlockPos pos) {
		return getChunk(new ChunkPos(pos)).tileTicks.get(pos);
	}
	
	protected ChunkData getChunk(ChunkPos pos) {
		ChunkData chunkData = chunkCache.get(pos);
		
		if (chunkData == null) {
			File regionFile = new File(regionDir, "r." + (pos.chunkXPos >> 5) + "." + (pos.chunkZPos >> 5) + ".mca");
			
			if (regionFile.exists())
				try (DataInputStream stream = new DataInputStream(new InflaterInputStream(new FileInputStream(regionFile)))) {
					chunkData = new ChunkData(getChunkNBT(stream, pos.chunkXPos & 31, pos.chunkZPos & 31));
				} catch (IOException ex) {
					BackupsMod.logger.error("Unable to read chunk " + pos.chunkXPos + ", " + pos.chunkZPos, ex);
					
					chunkData = new ChunkData();
				}
			else
				chunkData = new ChunkData();
			
			chunkCache.put(pos, chunkData);
		}
		
		return chunkData;
	}
	
	private static NBTTagCompound getChunkNBT(DataInputStream stream, int chunkX, int chunkZ) throws IOException {
		stream.skip((chunkX + chunkZ * 32) * Integer.BYTES);
		int offset = stream.readInt();
		if (offset == 0) return null;
		
		stream.skip((offset >> 8) * 4096 - (chunkX + chunkZ * 32 + 1) * Integer.BYTES);
		
		int length = stream.readInt(); if (length > (offset & 0xFF) * 4096 || length <= 0) throw new IOException("Invalid length");
		byte compressionType = stream.readByte();
		byte[] bytes = new byte[length - 1];
		
		stream.readFully(bytes);
		
		InputStream inputStream = new ByteArrayInputStream(bytes);
		
		if (compressionType == 1)
			inputStream = new GZIPInputStream(inputStream);
		else if (compressionType == 2)
			inputStream = new InflaterInputStream(inputStream);
		else
			throw new IOException("Invalid compression type");
		
		return CompressedStreamTools.read(new DataInputStream(inputStream));
	}
	
	protected static class ChunkData {
		
		private final boolean isEmpty;
		private final IBlockState[][][] blockStates;
		private final Map<BlockPos, NBTTagCompound> tileEntityData = new HashMap<>();
		private final Map<BlockPos, TileTick> tileTicks = new HashMap<>();
		private final List<NBTTagCompound> entities = new LinkedList<>();
		
		public ChunkData() {
			isEmpty = true;
			blockStates = null;
		}
		
		public ChunkData(NBTTagCompound nbt) {
			isEmpty = false;
			blockStates = new IBlockState[16][256][16];
			
			NBTTagCompound tag = nbt.getCompoundTag("Level");
			NBTTagList sections = tag.getTagList("Sections", 10);
			
			for (int i = 0; i < sections.tagCount(); i++) {
				NBTTagCompound section = sections.getCompoundTagAt(i);
				int sectionY = section.getByte("Y") << 4;
				
				byte[] blocks = section.getByteArray("Blocks");
				NibbleArray metadata = new NibbleArray(section.getByteArray("Data"));
				NibbleArray blocksExtra = section.hasKey("Add", 7) ? new NibbleArray(section.getByteArray("Add")) : null;
				
				for (int j = 0; j < blocks.length; j++) {
					int x = j & 15;
					int y = j >> 8 & 15;
					int z = j >> 4 & 15;
					
					int extra = blocksExtra != null ? blocksExtra.get(x, y, z) : 0;
					int blockStateId = extra << 12 | (blocks[j] & 0xFF) << 4 | metadata.get(x, y, z);
					
					blockStates[x][y + sectionY][z] = Block.BLOCK_STATE_IDS.getByValue(blockStateId);
				}
			}
			
			NBTTagList tileEntities = tag.getTagList("TileEntities", 10);
			
			for (int i = 0; i < tileEntities.tagCount(); i++) {
				NBTTagCompound tileData = tileEntities.getCompoundTagAt(i);
				BlockPos pos = new BlockPos(tileData.getInteger("x"), tileData.getInteger("y"), tileData.getInteger("z"));
				
				tileEntityData.put(pos, tileData);
			}
			
			NBTTagList tileTicksList = tag.getTagList("TileTicks", 10);
			
			for (int i = 0; i < tileTicksList.tagCount(); i++) {
				NBTTagCompound tickData = tileTicksList.getCompoundTagAt(i);
				BlockPos pos = new BlockPos(tickData.getInteger("x"), tickData.getInteger("y"), tickData.getInteger("z"));
				
				tileTicks.put(pos, new TileTick(Block.getBlockFromName(tickData.getString("i")), tickData.getInteger("t"), tickData.getInteger("p")));
			}
			
			NBTTagList entitiesList = tag.getTagList("Entities", 10);
			
			for (int i = 0; i < entitiesList.tagCount(); i++)
				entities.add(entitiesList.getCompoundTagAt(i));
		}

	}
	
	public static class TileTick {
		
		public final Block block;
		public final int time;
		public final int priority;
		
		public TileTick(Block block, int time, int priority) {
			this.block = block;
			this.time = time;
			this.priority = priority;
		}
	}

}
