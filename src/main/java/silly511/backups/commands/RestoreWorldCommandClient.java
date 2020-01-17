package silly511.backups.commands;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.DimensionManager;
import silly511.backups.BackupsMod;
import silly511.backups.gui.GuiRestoreTask;
import silly511.backups.helpers.BackupHelper;
import silly511.backups.helpers.BackupHelper.Backup;

public class RestoreWorldCommandClient extends CommandBase {

	private static final AtomicReference<Runnable> clientTickTask = new AtomicReference<>();
	
	@Override
	public String getName() {
		return "restoreworld";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.backups.restoreworld.usage";
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
		if (args.length < 1) throw new WrongUsageException("commands.backups.restoreworld.usage");
		
		File worldDir = DimensionManager.getCurrentSaveRootDirectory();
		Backup backup = RestoreCommand.parseBackup(args[0]);
		Minecraft mc = Minecraft.getMinecraft();
		
		BackupsMod.logger.info("Shutting server down to restore backup");
		
		clientTickTask.set(() -> {
			mc.world.sendQuittingDisconnectingPacket();
			mc.loadWorld(null);
			
			mc.displayGuiScreen(new GuiRestoreTask(null, status -> {
				status.accept("gui.backups.restoring");
				
				try {
					//TODO: This might help with https://github.com/Silly511/Backups-Mod/issues/19, but it's also a race condition so it should be replaced with something else
					Thread.sleep(2000);
				} catch (InterruptedException ex) {}
				
				BackupHelper.restoreBackup(backup.dir, worldDir, new File("temp"), null);
				BackupHelper.setLastBackup(backup.dir.getParentFile(), backup.dir);
			}));
		});
	}
	
	public static void onPreClientTick() {
		Runnable task = clientTickTask.getAndSet(null);
		
		if (task != null)
			task.run();
	}
}
