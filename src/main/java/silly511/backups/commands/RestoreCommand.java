package silly511.backups.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import silly511.backups.BackupManager;
import silly511.backups.BackupsMod;
import silly511.backups.helpers.BackupHelper;
import silly511.backups.helpers.BackupHelper.Backup;
import silly511.backups.helpers.FormatHelper;
import silly511.backups.util.GzipInputStream;
import silly511.backups.util.GzippedRegionLoader;
import silly511.backups.util.GzippedRegionLoader.TileTick;

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
		
		if (l == 1)
			return getListOfStringsMatchingLastWord(args, "blocks", "player");
		else if (l > 1)
			if (args[0].equals("blocks")) {
				if (l > 1 && l <= 7)
					return getTabCompletionCoordinate(args, l > 4 ? 4 : 1, pos);
				else if (l == 8)
					return getListOfBackups(args);
			} else if (args[0].equals("player")) {
				if (l == 2)
					return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
				else if (l == 3)
					return getListOfBackups(args);
			}
		
		return Collections.emptyList();
	}
	
	public static List<String> getListOfBackups(String[] args) {
		ZoneId timeZone = ZoneId.systemDefault();
		List<String> list = BackupHelper.listAllBackups(BackupManager.getCurrentBackupsDir()).stream()
				.map(backup -> backup.time.atZone(timeZone).format(dateFormat))
				.collect(Collectors.toList());
		
		return getListOfStringsMatchingLastWord(args, list);
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) throw new WrongUsageException("commands.backups.restore.usage");
		
		if (args[0].equals("blocks"))
			restoreBlocks(sender, args);
		else if (args[0].equals("player"))
			restorePlayer(server, sender, args);
		else
			throw new WrongUsageException("commands.backups.restore.usage");
	}
	
	public void restoreBlocks(ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 8) throw new WrongUsageException("commands.backups.restore.area.usage");
		sender.setCommandStat(CommandResultStats.Type.AFFECTED_BLOCKS, 0);
		
		World world = sender.getEntityWorld();
		
		StructureBoundingBox box = new StructureBoundingBox(parseBlockPos(sender, args, 1, false), parseBlockPos(sender, args, 4, false));
		int size = box.getXSize() * box.getYSize() * box.getZSize();
		Backup backup = parseBackup(args[7]);
		
		if (size > 524288) throw new CommandException("commands.clone.tooManyBlocks", size, 524288);
		if (!world.isAreaLoaded(box)) throw new CommandException("commands.clone.outOfWorld");
		
		String dimDir = sender.getEntityWorld().provider.getSaveFolder();
		GzippedRegionLoader loader = new GzippedRegionLoader(dimDir == null ? backup.dir : new File(backup.dir, dimDir));
		
		LinkedList<BlockPos> blocks = new LinkedList<>();
		
		for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(box.minX, box.minY, box.minZ), new BlockPos(box.maxX, box.maxY, box.maxZ))) {
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
		notifyCommandListener(sender, this, "commands.backups.restore.area.success", changedCount, backup.time.atZone(ZoneId.systemDefault()).format(FormatHelper.dateTimeFormat));
	}
	
	public void restorePlayer(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 3) throw new WrongUsageException("commands.backups.restore.player.usage");
		
		EntityPlayer player = getPlayer(server, sender, args[1]);
		Backup backup = parseBackup(args[2]);
		
		File playerFile = new File(new File(backup.dir, "playerdata"), player.getCachedUniqueIdString() + ".dat.gz");
		NBTTagCompound tag = null;
		
		if (playerFile.exists())
			try (InputStream in = new GzipInputStream(new FileInputStream(playerFile))) {
				tag = CompressedStreamTools.readCompressed(in);
			} catch (IOException ex) {
				BackupsMod.logger.error("Unable to restore player", ex);
			}
		
		if (tag != null && tag.hasKey("Inventory", 9)) {
			player.inventory.readFromNBT(tag.getTagList("Inventory", 10));
			player.inventoryContainer.detectAndSendChanges();
			
			notifyCommandListener(sender, this, "commands.backups.restore.player.success", player.getName(), backup.time.atZone(ZoneId.systemDefault()).format(FormatHelper.dateTimeFormat));
		} else
			throw new CommandException("commands.backups.restore.error");
	}
	
	public static Backup parseBackup(String s) throws CommandException {
		List<Backup> backups = BackupHelper.listAllBackups(BackupManager.getCurrentBackupsDir());
		
		try {
			int backupsAgo = Integer.parseInt(s);
			if (backupsAgo < 1) throw new NumberInvalidException("commands.generic.num.tooSmall", backupsAgo, 1);
			if (backupsAgo > backups.size()) throw new CommandException("commands.backups.restore.notABackup");
			
			return backups.get(backupsAgo - 1);
		} catch (NumberFormatException ex) {
			try {
				long time = LocalDateTime.parse(s, dateFormat).atZone(ZoneId.systemDefault()).toEpochSecond();
				
				return backups.stream()
						.filter(backup -> backup.time.getEpochSecond() == time)
						.findFirst()
						.orElseThrow(() -> new CommandException("commands.backups.restore.notABackup"));
			} catch (DateTimeParseException ex2) {
				throw new CommandException("commands.backups.restore.invalidDate", s);
			}
		}
	}

}
