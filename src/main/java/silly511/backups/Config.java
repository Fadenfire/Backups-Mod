package silly511.backups;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {
	
	public static final String CATEGORY_TRIMMING = "backupsTrimming";
	
	public static String backupsDir;
	public static int backupInterval;
	
	public static boolean trimmingEnabled;
	public static int perHour;
	public static int perDay;
	public static int perWeek;
	
	public static void load(File configFile) {
		Configuration config = new Configuration(configFile);
		config.load();
		
		backupsDir = config.getString("backupsDir", Configuration.CATEGORY_GENERAL, "backups", "Path to the backups directory");
		backupInterval = config.getInt("backupInterval", Configuration.CATEGORY_GENERAL, 15, 0, Integer.MAX_VALUE, "Number of minutes between automatic backups. Zero with disable automatic backups");
		
		trimmingEnabled = config.getBoolean("trimmingEnabled", CATEGORY_TRIMMING, true, "Whether backup trimming enabled");
		perHour = config.getInt("perHour", CATEGORY_TRIMMING, 1, 1, 24, "Number of hours old a backup has to be before it's trimmed to the hour");
		perDay = config.getInt("perDay", CATEGORY_TRIMMING, 3, 1, 7, "Number of days old a backup has to be before it's trimmed to the day");
		perWeek = config.getInt("perWeek", CATEGORY_TRIMMING, 30, 8, 90, "Number of days old a backup has to be before it's trimmed to the week");
		
		config.save();
	}

}
