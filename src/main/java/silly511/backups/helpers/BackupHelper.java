package silly511.backups.helpers;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import com.google.common.collect.ImmutableList;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.ForgeVersion;
import silly511.backups.BackupsMod;
import silly511.backups.Config;
import silly511.backups.util.GzipInputStream;
import silly511.backups.util.GzipOutputStream;
import silly511.backups.util.IORunnable;

public final class BackupHelper {
	
	public static enum BackupReason {
		SCHEDULED,
		USER,
		RESTORE,
		WORLD_JOIN;
		
		public final String tranKey = "backups.reason." + name().toLowerCase();
	}
	
	public static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-M-d k-mm-ss");
	public static final int FORMAT_VERSION = 1;
	
	public static Backup backup(Path sourceDir, Path backupsDir, BackupReason reason, Supplier<BufferedImage> iconFetcher) throws IOException {
		Path currentBackup = backupsDir.resolve("In-Progress");
		Path lastBackup = getLastBackup(backupsDir);
		Instant time = Instant.now();
		
		Files.createDirectories(currentBackup);
		FileHelper.cleanDirectory(currentBackup);
				
		for (Path sourceFile : FileHelper.listFilesDeep(sourceDir, false)) {
			Path currentFile = FileHelper.relativize(sourceDir, sourceFile, currentBackup); //File inside current backup
			
			if (Files.isDirectory(sourceFile, LinkOption.NOFOLLOW_LINKS))
				Files.createDirectory(currentFile);
			else if (Files.isRegularFile(sourceFile, LinkOption.NOFOLLOW_LINKS)) {
				Path lastFile = FileHelper.relativizeAdd(sourceDir, sourceFile, lastBackup, ".gz"); //File inside last backup
				currentFile = currentFile.resolveSibling(currentFile.getFileName() + ".gz");
				
				if (Files.isRegularFile(lastFile, LinkOption.NOFOLLOW_LINKS) && Files.getLastModifiedTime(sourceFile).equals(FileHelper.readGzipTime(lastFile)))
					Files.createLink(currentFile, lastFile);
				else
					try (OutputStream out = new GzipOutputStream(Files.newOutputStream(currentFile), Files.getLastModifiedTime(sourceFile, LinkOption.NOFOLLOW_LINKS), 8192)) {
						Files.copy(sourceFile, out);
					}
			}
		}
		
		Path finalBackupDir = backupsDir.resolve(time.atZone(ZoneId.systemDefault()).format(dateFormat));
		Files.move(currentBackup, finalBackupDir);
		
		Backup backup = new Backup(finalBackupDir.toAbsolutePath().normalize(), FORMAT_VERSION, reason, time, ForgeVersion.mcVersion, null);
		backup.writeBackup();
		
		if (iconFetcher != null)
			ImageIO.write(iconFetcher.get(), "png", finalBackupDir.resolve("icon.png").toFile());
		
		//Update last backup to newly created backup
		setLastBackup(backupsDir, finalBackupDir);
		
		return backup;
	}
	
	public static void restoreBackup(Path backupDir, Path targetDir, Path tempDir, Predicate<String> filter) throws IOException {
		Files.createDirectories(tempDir);
		Path tempRestoreDir = Files.createTempDirectory(tempDir, "backupRestore");
		
		try {
			List<IORunnable> attributeCopyTasks = new LinkedList<>();
			
			for (Path backupFile : FileHelper.listFilesDeep(backupDir, false)) {
				Path targetFile = FileHelper.relativizeRemove(backupDir, backupFile, tempRestoreDir, ".gz");
				
				if (Files.isDirectory(backupFile))
					Files.createDirectory(targetFile);
				else if (Files.isRegularFile(backupFile, LinkOption.NOFOLLOW_LINKS) && backupFile.toString().endsWith(".gz")) {
					if (filter != null && filter.test(targetFile.getFileName().toString())) continue;
					
					try (GzipInputStream stream = new GzipInputStream(Files.newInputStream(backupFile), 8192)) {
						Files.copy(stream, targetFile, StandardCopyOption.REPLACE_EXISTING);
						
						attributeCopyTasks.add(() -> Files.setLastModifiedTime(targetFile, stream.getModTime()));
					}
				}
			}
			
			for (IORunnable t : attributeCopyTasks) t.run();
			
			Path oldTargetDir = tempDir.resolve("BackupsModOldWorldTemp");
			
			FileHelper.deleteIfExists(oldTargetDir);
			if (Files.exists(targetDir, LinkOption.NOFOLLOW_LINKS)) Files.move(targetDir, oldTargetDir);
			
			Files.createDirectories(targetDir.getParent());
			Files.move(tempRestoreDir, targetDir);
			
			FileHelper.deleteIfExists(oldTargetDir);
		} finally {
			FileHelper.deleteIfExists(tempRestoreDir);
		}
	}
	
	public static void trimBackups(Path backupsDir) throws IOException {
		Set<Long> set1 = new HashSet<>();
		Set<Long> set2 = new HashSet<>();
		Set<Long> set3 = new HashSet<>();
		
		ZoneId timeZone = ZoneId.systemDefault();
		long currentSec = Instant.now().getEpochSecond();
		int i = 0;
		
		for (Backup backup : listAllBackups(backupsDir)) {
			if (backup.label != null) continue;
			
			long day = backup.time.atZone(timeZone).toLocalDate().toEpochDay();
			long sec = backup.time.getEpochSecond();
			long secAgo = currentSec - sec;
			
			boolean shouldDelete = false;
			i++;
			
			if (Config.trimming.maxNumber > 0 && i > Config.trimming.maxNumber)
				shouldDelete = true;
			else if (Config.trimming.maxAge > 0 && secAgo >= Config.trimming.maxAge * 86400)
				shouldDelete = true;
			else if (secAgo >= Config.trimming.perWeek * 86400) //After 8+ days, trim to every week
				shouldDelete = !set1.add((day + 3) / 7);
			else if (secAgo >= Config.trimming.perDay * 86400) //After 1-7 days, trim to every day
				shouldDelete = !set2.add(day);
			else if (secAgo >= Config.trimming.perHour * 3600) //After 1-24 hours, trim to every hour
				shouldDelete = !set3.add(sec / 3600);
			
			if (shouldDelete)
				try {
					deleteBackup(backup);
					BackupsMod.logger.info("Trimming backup " + backup.time);
				} catch (IOException ex) {
					BackupsMod.logger.error("Unable to trim backup " + backup.time, ex);
				}
		}
	}
	
	public static void deleteBackup(Backup backup) throws IOException {
		Path backupsDir = backup.dir.getParent();
		
		if (getLastBackup(backupsDir).equals(backup.dir)) {
			List<Backup> backups = listAllBackups(backupsDir);
			int index = backups.indexOf(backup);
			
			if (backups.size() > 1)
				setLastBackup(backupsDir, backups.get(index + (index == 0 ? 1 : -1)).dir);
		}
		
		FileHelper.deleteDirectory(backup.dir);
	}
	
	public static void setLastBackup(Path backupsDir, Path newLastBackup) throws IOException {
		Path last = backupsDir.resolve("Last");
		
		if (Files.isSymbolicLink(last)) Files.delete(last);
		
		Files.write(last, newLastBackup.getFileName().toString().getBytes(StandardCharsets.UTF_8));
	}
	
	public static Path getLastBackup(Path backupsDir) throws IOException {
		Path last = backupsDir.resolve("Last");
		
		if (Files.isRegularFile(last)) {
			Path readDir = backupsDir.resolve(new String(Files.readAllBytes(last), StandardCharsets.UTF_8));
			
			if (Files.isDirectory(readDir)) return readDir;
		}
		
		return last;
	}
	
	public static List<Backup> listAllBackups(Path backupsDir) {
		if (!Files.isDirectory(backupsDir)) return ImmutableList.of();
		
		List<Backup> list = new ArrayList<>();
		
		try {
			for (Path path : FileHelper.listFiles(backupsDir))
				if (Files.isRegularFile(path.resolve("backupMetadata.dat")))
					list.add(Backup.getBackup(path));
		} catch (IOException ex) {
			BackupsMod.logger.error("Error trying to list backups", ex);
		}
		
		list.sort((b1, b2) -> b2.time.compareTo(b1.time));
		
		return ImmutableList.copyOf(list);
	}
	
	public static class Backup {
		public final Path dir;
		public final int format;
		public final BackupReason reason;
		public final Instant time;
		public final String mcVersion;
		protected String label;
		
		private static final Map<Path, Backup> cache = new HashMap<>();
		
		public static Backup getBackup(Path backupDir) {
			backupDir = backupDir.toAbsolutePath().normalize();
			Path metadataFile = backupDir.resolve("backupMetadata.dat");
			
			if (!Files.isRegularFile(metadataFile)) return null;
			if (cache.containsKey(backupDir)) return cache.get(backupDir);
			
			try {
				NBTTagCompound tag = CompressedStreamTools.read(metadataFile.toFile());
				BackupReason reason = BackupReason.values()[tag.getByte("Reason")];
				Instant time = Instant.ofEpochSecond(tag.getLong("Time"));
				String label = tag.hasKey("Label", 8) ? tag.getString("Label") : null;
				
				return new Backup(backupDir, tag.getInteger("Format"), reason, time, tag.getString("mcVersion"), label);
			} catch (IOException | IndexOutOfBoundsException ex) {
				return new Backup(backupDir, FORMAT_VERSION, null, FileHelper.getDateCreated(backupDir), null, null);
			}
		}

		protected Backup(Path dir, int format, BackupReason reason, Instant time, String mcVersion, String label) {
			this.dir = dir;
			this.format = format;
			this.reason = reason;
			this.time = time;
			this.mcVersion = mcVersion;
			this.label = label;
			
			cache.put(this.dir, this);
		}
		
		public void writeBackup() throws IOException {
			NBTTagCompound tag = new NBTTagCompound();
			
			tag.setInteger("Format", format);
			tag.setByte("Reason", (byte) reason.ordinal());
			tag.setLong("Time", time.getEpochSecond());
			tag.setString("mcVersion", mcVersion);
			
			if (label != null) tag.setString("Label", label);
			
			CompressedStreamTools.write(tag, dir.resolve("backupMetadata.dat").toFile());
		}
		
		public void setLabel(String label) {
			this.label = label;
		}
		
		public String getLabel() {
			return label;
		}
	}

}
