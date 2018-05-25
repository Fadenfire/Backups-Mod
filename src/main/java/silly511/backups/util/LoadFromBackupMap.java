package silly511.backups.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.zip.InflaterOutputStream;

import net.minecraft.world.chunk.storage.RegionFile;
import silly511.backups.BackupsMod;
import silly511.backups.helpers.FileHelper;

public class LoadFromBackupMap extends HashMap<File, RegionFile> {

	private static final long serialVersionUID = 1L;
	
	private File backupDir;
	private File tempWorldDir;
	
	public LoadFromBackupMap(File backupDir, File tempWorldDir) {
		this.backupDir = backupDir;
		this.tempWorldDir = tempWorldDir.getAbsoluteFile();
	}
	
	@Override
	public RegionFile get(Object key) {
		RegionFile regionFile = super.get(key);
		
		if (regionFile == null && key instanceof File && !((File) key).exists()) {
			File file = FileHelper.normalize((File) key);
			Path backupFile = FileHelper.relativize(tempWorldDir, file, backupDir);
			
			if (Files.isRegularFile(backupFile))
				try (InflaterOutputStream out = new InflaterOutputStream(new FileOutputStream(file))) {
					Files.copy(backupFile, out);
				} catch (IOException ex) {
					BackupsMod.logger.error("Error loading region file from backup", ex);
				}
		}
		
		return regionFile;
	}

}
