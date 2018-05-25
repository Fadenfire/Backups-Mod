package silly511.backups;

import net.minecraftforge.common.config.Config.Comment;
import net.minecraftforge.common.config.Config.Name;
import net.minecraftforge.common.config.Config.RangeInt;

@net.minecraftforge.common.config.Config(modid = BackupsMod.modid)
public class Config {
	
	@Comment("Path to the backups directory")
	public static String backupsDir = "backups";
	
	@Comment("Number of minutes between automatic backups. Setting to zero will disable automatic backups")
	@RangeInt(min = 0)
	public static int backupInterval = 15;
	
	@Name("backupTrimming")
	public static BackupTrimming trimming = new BackupTrimming();
	
	public static class BackupTrimming {
		@Comment("Whether backup trimming enabled")
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
	}

}
