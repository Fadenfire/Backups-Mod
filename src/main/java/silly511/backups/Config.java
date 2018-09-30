package silly511.backups;

import java.io.File;
import java.util.Arrays;

import net.minecraftforge.common.config.Configuration;

public class Config {
	
	public static final String CATEGORY_TRIMMING = "backupsTrimming";
	
	public static String backupsDir;
	public static int backupInterval;
	public static boolean needOpToBackup;
	public static AnnounceBackupsMode announceBackups;
	
	public static boolean trimmingEnabled;
	public static int perHour;
	public static int perDay;
	public static int perWeek;
	
	public static void load(File configFile) {
		Configuration config = new Configuration(configFile);
		config.load();
		
		backupsDir = config.getString("backupsDir", Configuration.CATEGORY_GENERAL, "backups", "Path to the backups directory");
		backupInterval = config.getInt("backupInterval", Configuration.CATEGORY_GENERAL, 15, 0, Integer.MAX_VALUE, "Number of minutes between automatic backups. Zero with disable automatic backups");
		needOpToBackup = config.getBoolean("needOpToBackup", Configuration.CATEGORY_GENERAL, true, "If you need to be an op to use /backup. If this is false then any player can use /backup");
		announceBackups = AnnounceBackupsMode.valueOf(config.getString("announceBackups", Configuration.CATEGORY_GENERAL, "ALL_PLAYERS", "Which players can see the Started Backup and Finished Backup messages", Arrays.stream(AnnounceBackupsMode.values()).map(AnnounceBackupsMode::name).toArray(String[]::new)));
		
		trimmingEnabled = config.getBoolean("trimmingEnabled", CATEGORY_TRIMMING, true, "Whether backup trimming enabled");
		perHour = config.getInt("perHour", CATEGORY_TRIMMING, 1, 1, 24, "Number of hours old a backup has to be before it's trimmed to the hour");
		perDay = config.getInt("perDay", CATEGORY_TRIMMING, 3, 1, 7, "Number of days old a backup has to be before it's trimmed to the day");
		perWeek = config.getInt("perWeek", CATEGORY_TRIMMING, 30, 8, 90, "Number of days old a backup has to be before it's trimmed to the week");
		
		config.save();
	}
	
	public static enum AnnounceBackupsMode {
		OFF,
		ALL_PLAYERS,
		OPS_ONLY
	}

}
