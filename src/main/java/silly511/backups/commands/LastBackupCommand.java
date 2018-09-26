package silly511.backups.commands;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import silly511.backups.BackupManager;
import silly511.backups.helpers.BackupHelper;
import silly511.backups.helpers.BackupHelper.Backup;
import silly511.backups.helpers.FormatHelper;

public class LastBackupCommand extends CommandBase {

	@Override
	public String getName() {
		return "lastbackup";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.backups.lastbackup.usage";
	}
	
	@Override
	public int getRequiredPermissionLevel() {
		return 0;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		List<Backup> backups = BackupHelper.listAllBackups(BackupManager.getCurrentBackupsDir());
		if (backups.isEmpty()) throw new CommandException("commands.backups.lastbackup.noBackups");
		Instant lastBackupTime = backups.get(0).time;
		
		sender.sendMessage(new TextComponentTranslation("commands.backups.lastbackup.success",
				lastBackupTime.atZone(ZoneId.systemDefault()).format(FormatHelper.dateTimeFormat),
				FormatHelper.relativeTimeAgo(lastBackupTime)));
	}

}
