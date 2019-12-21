package silly511.backups;

import net.minecraftforge.common.config.Config.Comment;
import net.minecraftforge.common.config.Config.Name;
import net.minecraftforge.common.config.Config.RangeInt;

@net.minecraftforge.common.config.Config(modid = BackupsMod.modid)
public class Config {
	
	@Comment("Path to the backups directory")
	public static String backupsDir = "backupsmod";
	
	@Comment("Number of minutes between automatic backups. Setting to zero will disable automatic backups")
	@RangeInt(min = 0)
	public static int backupInterval = 15;
	
	@Comment("If a world is deleted but still has backups, then an entry for the world will still be shown in the world selection list")
	public static boolean showDeletedWorlds = true;
	
	@Comment("If you need to be an op to use /backup. If this is false then any player can use /backup")
	public static boolean needOpToBackup = true;
	
	@Comment("If backups should still happen when there are no player online")
	public static boolean backupWhenServerEmpty = false;
	
	@Comment("Which players can see the Started Backup and Finished Backup messages")
	public static AnnounceBackupsMode announceBackups = AnnounceBackupsMode.ALL_PLAYERS;
	
	@Name("backupTrimming")
	public static BackupTrimming trimming = new BackupTrimming();
	
	public static class BackupTrimming {
		@Comment("Whether backup trimming is enabled")
		public boolean trimmingEnabled = true;
		
		@Comment("Number of hours old a backup has to be before it's trimmed to the hour")
		@RangeInt(min = 1, max = 24)
		public int perHour = 1;
		
		@Comment("Number of days old a backup has to be before it's trimmed to the day")
		@RangeInt(min = 1, max = 7)
		public int perDay = 3;
		
		@Comment("Number of days old a backup has to be before it's trimmed to the week")
		@RangeInt(min = 8, max = 90)
		public int perWeek = 30;
		
		@Comment("Number of days old a backup has to be before it's deleted. Set to zero to keep backups forever")
		@RangeInt(min = 0)
		public int maxAge = 0;
		
		@Comment("Maximum number of backups to keep. Once the number of backups exceeds this number the oldest ones are deleted. Set to zero to disable.")
		@RangeInt(min = 0)
		public int maxNumber = 0;
	}
	
	public static enum AnnounceBackupsMode {
		OFF,
		ALL_PLAYERS,
		OPS_ONLY
	}

}
