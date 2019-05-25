package silly511.backups.commands;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableList;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

public class BackupsModCommand extends CommandBase {
	
	private final List<CommandBase> subCommands;
	private final String[] nameArray;
	
	public BackupsModCommand() {
		ImmutableList.Builder<CommandBase> builder = ImmutableList.builder();
		builder.add(new RestoreCommand());
		builder.add(new BackupCommand());
		builder.add(new LastBackupCommand());
		
		subCommands = builder.build();
		nameArray = new String[subCommands.size()];
		
		for (int i = 0; i < subCommands.size(); i++)
			nameArray[i] = subCommands.get(i).getName();
	}
	
	@Override
	public String getName() {
		return "backupsmod";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.backups.backupsmod.usage";
	}
	
	@Override
	public int getRequiredPermissionLevel() {
		return 0;
	}
	
	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
		if (args.length == 1)
			return getListOfStringsMatchingLastWord(args, nameArray);
		if (args.length > 1)
			for (CommandBase subCommand : subCommands)
				if (subCommand.getName().equals(args[0]))
					return subCommand.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), targetPos);
		
		return super.getTabCompletions(server, sender, args, targetPos);
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length >= 1)
			for (CommandBase subCommand : subCommands)
				if (subCommand.getName().equals(args[0])) {
					if (!subCommand.checkPermission(server, sender))
						throw new CommandException("commands.generic.permission");
					
					subCommand.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
					return;
				}
		
		throw new WrongUsageException("commands.backups.backupsmod.usage");
	}

}
