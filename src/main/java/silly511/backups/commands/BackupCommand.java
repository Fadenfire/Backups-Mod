package silly511.backups.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import silly511.backups.BackupManager;
import silly511.backups.helpers.BackupHelper.BackupReason;

public class BackupCommand extends CommandBase {

	@Override
	public String getCommandName() {
		return "backup";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "commands.backups.backup.usage";
	}
	
	@Override
	public int getRequiredPermissionLevel() {
		return 3;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		if (BackupManager.isTempWorld())
			throw new CommandException("commands.backups.backup.tempWorld");
		if (BackupManager.isBackingUp())
			throw new CommandException("commands.backups.backup.inProgress");
		
		notifyOperators(sender, this, "commands.backups.backup.success");
		BackupManager.startBackup(BackupReason.USER);
	}

}
