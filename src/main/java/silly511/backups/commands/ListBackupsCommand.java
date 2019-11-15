package silly511.backups.commands;

import java.time.ZoneId;
import java.util.List;

import com.google.common.collect.Lists;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import silly511.backups.BackupManager;
import silly511.backups.helpers.BackupHelper;
import silly511.backups.helpers.BackupHelper.Backup;
import silly511.backups.helpers.FormatHelper;

public class ListBackupsCommand extends CommandBase {

	@Override
	public String getName() {
		return "listbackups";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.backups.listbackups.usage";
	}
	
	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		List<Backup> backups = BackupHelper.listAllBackups(BackupManager.getCurrentBackupsDir());
		if (backups.isEmpty()) throw new CommandException("commands.backups.lastbackup.noBackups");
		
		for (Backup backup : Lists.reverse(backups)) {
			String formattedTime = backup.time.atZone(ZoneId.systemDefault()).format(FormatHelper.dateTimeFormat);
			ITextComponent msg = new TextComponentString(formattedTime + " (" + FormatHelper.relativeTimeAgo(backup.time) + " ago)");
						
			sender.sendMessage(msg);
		}
	}

}
