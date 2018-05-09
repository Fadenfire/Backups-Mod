package silly511.backups.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import silly511.backups.BackupManager;
import silly511.backups.helpers.BackupHelper.BackupReason;

public class BackupCommand extends CommandBase {

	@Override
	public String getName() {
		return "backup";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.backups.backup.usage";
	}
	
	@Override
	public int getRequiredPermissionLevel() {
		return 3;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (BackupManager.isTempWorld())
			throw new CommandException("commands.backups.backup.tempWorld");
		if (BackupManager.isBackingUp())
			throw new CommandException("commands.backups.backup.inProgress");
		
		notifyCommandListener(sender, this, "commands.backups.backup.success");
		BackupManager.startBackup(BackupReason.USER);
	}

}
