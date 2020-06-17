package silly511.backups.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import net.minecraft.world.chunk.storage.RegionFile;
import silly511.backups.BackupsMod;
import silly511.backups.helpers.FileHelper;

public class LoadFromBackupMap extends HashMap<File, RegionFile> {

	private static final long serialVersionUID = 1L;
	
	private Path backupDir;
	private Path tempWorldDir;
	
	public LoadFromBackupMap(Path backupDir, Path tempWorldDir) {
		this.backupDir = backupDir;
		this.tempWorldDir = tempWorldDir.toAbsolutePath().normalize();
	}
	
	@Override
	public RegionFile get(Object key) {
		RegionFile regionFile = super.get(key);
		
		if (regionFile == null && key instanceof File && !((File) key).exists()) {
			Path path = ((File) key).toPath().toAbsolutePath().normalize();
			Path backupFile = FileHelper.relativizeAdd(tempWorldDir, path, backupDir, ".gz");
			
			if (Files.isRegularFile(backupFile))
				try (InputStream in = new GzipInputStream(Files.newInputStream(backupFile))) {
					Files.copy(in, path);
				} catch (IOException ex) {
					BackupsMod.logger.error("Error loading region file from backup", ex);
				}
		}
		
		return regionFile;
	}

}
