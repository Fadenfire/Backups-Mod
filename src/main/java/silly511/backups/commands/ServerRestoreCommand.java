package silly511.backups.commands;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import silly511.backups.BackupsMod;
import silly511.backups.helpers.BackupHelper;
import silly511.backups.helpers.BackupHelper.Backup;
import silly511.backups.helpers.FormatHelper;

public class ServerRestoreCommand extends CommandBase {

	private static Backup toRestore;
	private static File worldSaveDir;
	
	@Override
	public String getName() {
		return "serverrestore";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.backups.serverrestore.usage";
	}
	
	@Override
	public int getRequiredPermissionLevel() {
		return 4;
	}
	
	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
		if (args.length == 1)
			return RestoreCommand.getListOfBackups(args);
		
		return Collections.emptyList();
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) throw new WrongUsageException("commands.backups.serverrestore.usage");
		if (!server.isDedicatedServer()) throw new RuntimeException();
		
		Backup backup = RestoreCommand.parseBackup(args[0]);
		
		BackupsMod.logger.info("Shutting server down to restore backup");
		worldSaveDir = DimensionManager.getCurrentSaveRootDirectory();
		toRestore = backup;
		server.initiateShutdown();
	}
	
	public static void onServerShutdown() {
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		
		if (toRestore == null || worldSaveDir == null || server == null || !server.isDedicatedServer()) return;
		BackupsMod.logger.info("Restoring backup " + toRestore.time.atZone(ZoneId.systemDefault()).format(FormatHelper.dateTimeFormat));
		
		try {
			File outputDir = new File(worldSaveDir.getParent(), "restored_world");
			outputDir.mkdir();
			BackupHelper.restoreBackup(toRestore.dir, outputDir, null);
			toRestore = null;
			
			BackupsMod.logger.info("Restored backup, please replace your world dir with restored_world and restart the server");
		} catch (IOException ex) {
			BackupsMod.logger.fatal("Error restoring backup", ex);
		}
	}
}
