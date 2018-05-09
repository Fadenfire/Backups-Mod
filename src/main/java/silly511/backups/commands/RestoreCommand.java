package silly511.backups.commands;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import silly511.backups.BackupManager;
import silly511.backups.helpers.BackupHelper;
import silly511.backups.helpers.BackupHelper.Backup;
import silly511.backups.helpers.FormatHelper;
import silly511.backups.util.CompressedRegionLoader;
import silly511.backups.util.CompressedRegionLoader.TileTick;

public class RestoreCommand extends CommandBase {
	
	public static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy/M/d-k:mm:ss");

	@Override
	public String getName() {
		return "restore";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.backups.restore.usage";
	}
	
	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}
	
	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
		int l = args.length;
		
		if (l > 0 && l <= 6)
			return getTabCompletionCoordinate(args, l > 3 ? 3 : 0, pos);
		else if (l == 7) {
			ZoneId timeZone = ZoneId.systemDefault();
			List<String> list = BackupHelper.listAllBackups(BackupManager.getCurrentBackupsDir()).stream()
					.map(backup -> backup.time.atZone(timeZone).format(dateFormat))
					.collect(Collectors.toList());
			
			return getListOfStringsMatchingLastWord(args, list);
		} else if (l == 8)
			return getListOfStringsMatchingLastWord(args, "true", "false");
		
		return Collections.emptyList();
	}
	
//	private static List<String> getAllDatesWithBackups(ToIntFunction<ZonedDateTime> function, Predicate<ZonedDateTime> filter) {
//		ZoneId timeZone = ZoneId.systemDefault();
//		
//		return BackupHelper.listAllBackups(BackupManager.getCurrentBackupsDir()).stream()
//				.map(backup -> backup.time.atZone(timeZone))
//				.filter(filter)
//				.mapToInt(function)
//				.distinct()
//				.mapToObj(String::valueOf)
//				.collect(Collectors.toList());
//	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 7) throw new WrongUsageException(getUsage(sender));
		sender.setCommandStat(CommandResultStats.Type.AFFECTED_BLOCKS, 0);
		
		World world = sender.getEntityWorld();
		
		StructureBoundingBox box = new StructureBoundingBox(parseBlockPos(sender, args, 0, false), parseBlockPos(sender, args, 3, false));
		Backup backup = parseBackup(args, 6);
		BlockPos minPos = new BlockPos(box.minX, box.minY, box.minZ);
		BlockPos maxPos = new BlockPos(box.maxX, box.maxY, box.maxZ);
		int size = box.getXSize() * box.getYSize() * box.getZSize();
		
		if (size > 524288) throw new CommandException("commands.clone.tooManyBlocks", size, 524288);
		if (!world.isAreaLoaded(box)) throw new CommandException("commands.clone.outOfWorld");
		
		String dimDir = sender.getEntityWorld().provider.getSaveFolder();
		CompressedRegionLoader loader = new CompressedRegionLoader(dimDir == null ? backup.dir : new File(backup.dir, dimDir));
		
		LinkedList<BlockPos> blocks = new LinkedList<>();
		
		for (BlockPos pos : BlockPos.getAllInBox(minPos, maxPos)) {
			if (loader.getBlockState(pos) == null) continue;
			
			IBlockState state = world.getBlockState(pos);
			
			if (!state.isFullBlock() && !state.isFullCube())
				blocks.addFirst(pos);
			else
				blocks.addLast(pos);
		}
		
		for (BlockPos pos : blocks) {
			TileEntity tileEntity = world.getTileEntity(pos);
			
			if (tileEntity instanceof IInventory) ((IInventory) tileEntity).clear();
			
			world.setBlockState(pos, Blocks.BARRIER.getDefaultState(), 2);
		}
		
		int changedCount = 0;
		
		for (BlockPos pos : blocks)
			if (world.setBlockState(pos, loader.getBlockState(pos), 2))
				changedCount++;
		
		for (BlockPos pos : blocks) {
			TileEntity tileEntity = world.getTileEntity(pos);
			NBTTagCompound nbt = loader.getTileEntityData(pos);
			
			if (tileEntity == null || nbt == null) continue;
			
			tileEntity.readFromNBT(nbt);
			tileEntity.markDirty();
		}
		
		for (BlockPos pos : blocks) {
			TileTick tileTick = loader.getTileTick(pos);
			
			if (tileTick != null)
				world.scheduleBlockUpdate(pos, tileTick.block, tileTick.time, tileTick.priority);
		}
		
		sender.setCommandStat(CommandResultStats.Type.AFFECTED_BLOCKS, changedCount);
		notifyCommandListener(sender, this, "commands.backups.restore.success", changedCount, backup.time.atZone(ZoneId.systemDefault()).format(FormatHelper.dateTimeFormat));
	}
	
	public static Backup parseBackup(String[] args, int index) throws CommandException {
		List<Backup> backups = BackupHelper.listAllBackups(BackupManager.getCurrentBackupsDir());
		
		try {
			int backupsAgo = Integer.parseInt(args[index]);
			if (backupsAgo < 1) throw new NumberInvalidException("commands.generic.num.tooSmall", backupsAgo, 1);
			if (backupsAgo >= backups.size()) throw new CommandException("commands.backups.restore.notABackup");
			
			return backups.get(backupsAgo);
		} catch (NumberFormatException ex) {
			try {
				long time = LocalDateTime.parse(args[index], dateFormat).atZone(ZoneId.systemDefault()).toEpochSecond();
				
				return backups.stream()
						.filter(backup -> backup.time.getEpochSecond() == time)
						.findFirst()
						.orElseThrow(() -> new CommandException("commands.backups.restore.notABackup"));
			} catch (DateTimeParseException ex2) {
				throw new CommandException("commands.backups.restore.invalidDate", args[index]);
			}
		}
	}

}
