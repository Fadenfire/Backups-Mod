package silly511.backups;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {
	
	public static File backupsPath;
	public static int backupInterval;
	
	public static void load(File configFile) {
		Configuration config = new Configuration(configFile);
		config.load();
		
		backupsPath = new File(config.getString("backupsDir", Configuration.CATEGORY_GENERAL, "backups", "Path to the backups directory")).getAbsoluteFile();
		backupInterval = config.getInt("backupInterval", Configuration.CATEGORY_GENERAL, 15, 0, Integer.MAX_VALUE, "Number of minutes between automatic backups. Zero with disable automatic backups");
		
		config.save();
	}

}
