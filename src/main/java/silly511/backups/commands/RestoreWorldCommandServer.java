package silly511.backups.commands;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.DimensionManager;
import silly511.backups.BackupsMod;
import silly511.backups.helpers.BackupHelper;
import silly511.backups.helpers.BackupHelper.Backup;

public class RestoreWorldCommandServer extends CommandBase {
	
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
		
		//I really don't like this, but it works and the alternative is ASM so I'm keeping it for now
		try {
			Class<?> shutdownHooksClass = Class.forName("java.lang.ApplicationShutdownHooks");
			Field hooksField = shutdownHooksClass.getDeclaredField("hooks");
			hooksField.setAccessible(true);
			IdentityHashMap<Thread, Thread> hooks = (IdentityHashMap<Thread, Thread>) hooksField.get(null);
			
			synchronized (shutdownHooksClass) {
				Thread oldHook = null;
				
				for (Thread hook : hooks.values())
					if (hook.getClass().getEnclosingClass() == MinecraftServer.class) {
						oldHook = hook;
						break;
					}
				
				if (oldHook == null)
					throw new IllegalStateException("Unable to find server shutdown hook");
				
				final Thread finalOldHook = oldHook;
				Thread newHook = new Thread(() -> {
					finalOldHook.run();
					
					try {
						System.out.println("[Backups]: Restoring backup");
						
						BackupHelper.restoreBackup(backup.dir, worldDir, null);
						BackupHelper.setLastBackup(backup.dir.getParentFile(), backup.dir);
						
						System.out.println("[Backups]: Backup restored, please restart server");
					} catch (Exception ex) {
						System.out.println("[Backups]: Error restoring backup");
						ex.printStackTrace();
					}
				}, "Server Shutdown Thread (Wrapped by Backup Restore Thread)");
				
				hooks.remove(oldHook);
				hooks.put(newHook, newHook);
			}
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Error trying to wrap shutdown hook", ex);
		}
		
		BackupsMod.logger.info("Shutting server down to restore backup");
		server.initiateShutdown();
	}
}
