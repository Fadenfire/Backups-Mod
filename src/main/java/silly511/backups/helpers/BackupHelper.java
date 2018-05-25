package silly511.backups.helpers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
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
import java.util.zip.DeflaterInputStream;
import java.util.zip.InflaterInputStream;

import org.apache.commons.io.FileUtils;

import com.google.common.collect.ImmutableList;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.ForgeVersion;
import silly511.backups.BackupsMod;
import silly511.backups.Config;
import silly511.backups.util.IORunnable;

public final class BackupHelper {
	
	public static enum BackupReason {
		SCHEDULED,
		USER,
		RESTORE;
		
		public final String tranKey = "backups.reason." + name().toLowerCase();
	}
	
	public static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d-M-yyyy h-mm-ss a");
	
	public static void backup(File sourceDir, File backupsDir, BackupReason reason, BufferedImage icon) throws IOException {
		File currentBackup = new File(backupsDir, "In-Progress");
		File lastBackup = new File(backupsDir, "Last");
		Instant time = Instant.now();
		
		FileUtils.forceMkdir(currentBackup);
		FileHelper.cleanDirectory(currentBackup);
		
//		List<Runnable> fileCopyTasks = new LinkedList<>();
		List<IORunnable> attributeCopyTasks = new LinkedList<>();
		
		for (File file : FileHelper.listFiles(sourceDir, false)) {
			Path sourceFile = file.toPath();
			Path currentFile = FileHelper.relativize(sourceDir, file, currentBackup); //File inside current backup
			Path lastFile = FileHelper.relativize(sourceDir, file, lastBackup); //File inside last backup
			
			if (Files.isDirectory(sourceFile, LinkOption.NOFOLLOW_LINKS))
				Files.createDirectory(currentFile);
			else if (FileHelper.hasSameModifyTime(sourceFile, lastFile))
				Files.createLink(currentFile, lastFile);
			else if (Files.isSymbolicLink(sourceFile))
				Files.createSymbolicLink(currentFile, Files.readSymbolicLink(sourceFile));
			else
				try (InputStream stream = new DeflaterInputStream(new FileInputStream(file))) {
					Files.copy(stream, currentFile, StandardCopyOption.REPLACE_EXISTING);
				}
			
			attributeCopyTasks.add(() -> {
				if (Files.exists(currentFile))
					FileHelper.copyAttributes(sourceFile, currentFile);
			});
		}
		
//		fileCopyTasks.parallelStream().forEach(Runnable::run); //Compress files in parallel
		for (IORunnable t : attributeCopyTasks) t.run(); //Copy attributes last
		
		File finalBackupDir = new File(backupsDir, time.atZone(ZoneId.systemDefault()).format(dateFormat));
		Files.move(currentBackup.toPath(), finalBackupDir.toPath());
		
		//Make backup contents read-only
		for (File file : FileHelper.listFiles(finalBackupDir, false))
			file.setWritable(false);
		
		new Backup(finalBackupDir, reason, time, ForgeVersion.mcVersion, ImageHelper.toCompressedBytes(icon), null).writeBackup();
		
		//I can't do this with the rest because I need to write the metadata first
		finalBackupDir.setWritable(false);
		
		//Update last backup to newly created backup
		setLastBackup(backupsDir, finalBackupDir);
	}
	
	public static void restoreBackup(File backupDir, File targetDir, Predicate<File> filter) throws IOException {
		FileUtils.forceMkdir(targetDir);
		FileHelper.cleanDirectory(targetDir);
		
		List<Runnable> fileCopyTasks = new LinkedList<>();
		List<IORunnable> attributeCopyTasks = new LinkedList<>();
		File metadataFile = new File(backupDir, "backupMetadata.dat");
		
		for (File file : FileHelper.listFiles(backupDir, false)) {
			if ((filter != null && filter.test(file)) || file.equals(metadataFile)) continue;
			
			Path backupFile = file.toPath();
			Path targetFile = FileHelper.relativize(backupDir, file, targetDir);
			
			if (Files.isDirectory(backupFile, LinkOption.NOFOLLOW_LINKS))
				Files.createDirectory(targetFile);
			else if (Files.isSymbolicLink(backupFile))
				Files.createSymbolicLink(targetFile, Files.readSymbolicLink(backupFile));
			else
				fileCopyTasks.add(() -> {
					try (InputStream stream = new InflaterInputStream(new FileInputStream(file))) {
						Files.copy(stream, targetFile, StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException ex) {
						BackupsMod.logger.error("Unable to restore " + file.getName(), ex);
						
						targetFile.toFile().delete();
					}
				});
			
			attributeCopyTasks.add(() -> {
				if (Files.exists(targetFile))
					FileHelper.copyAttributes(backupFile, targetFile);
			});
		}
		
		fileCopyTasks.parallelStream().forEach(Runnable::run);
		for (IORunnable t : attributeCopyTasks) t.run();
	}
	
	public static void trimBackups(File backupsDir) {
		Set<Long> set1 = new HashSet<>();
		Set<Long> set2 = new HashSet<>();
		Set<Long> set3 = new HashSet<>();
		
		ZoneId timeZone = ZoneId.systemDefault();
		long currentDay = LocalDate.now().toEpochDay();
		long currentSec = Instant.now().getEpochSecond();
		
		for (Backup backup : listAllBackups(backupsDir)) {
			if (backup.label != null) continue;
			
			long day = backup.time.atZone(timeZone).toLocalDate().toEpochDay();
			long sec = backup.time.getEpochSecond();
			
			boolean shouldDelete = false;
				
			if (currentDay - day >= Config.perWeek) //After 8+ days, trim to every week
				shouldDelete = !set1.add((day + 3) / 7);
			else if (currentDay - day >= Config.perDay) //After 1-7 days, trim to every day
				shouldDelete = !set2.add(day);
			else if (currentSec - sec >= Config.perHour * 3600) //After 1-24 hours, trim to every hour
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
		File backupsDir = backup.dir.getParentFile();
		
		if (new File(backupsDir, "Last").getCanonicalFile().equals(backup.dir)) {
			List<Backup> backups = listAllBackups(backupsDir);
			int index = backups.indexOf(backup);
			
			if (backups.size() <= 1) return;
			
			setLastBackup(backupsDir, backups.get(index + (index == 0 ? 1 : -1)).dir);
		}
		
		for (File file : FileHelper.listFiles(backup.dir, true))
			file.setWritable(true); //Scary
		
		FileHelper.deleteDirectory(backup.dir);
	}
	
	public static void setLastBackup(File backupsDir, File newLastBackup) throws IOException {
		File lastBackup = new File(backupsDir, "Last");
		
		lastBackup.delete();
		Files.createSymbolicLink(lastBackup.toPath(), newLastBackup.toPath().toAbsolutePath());
	}
	
	public static List<Backup> listAllBackups(File backupsDir) {
		if (!backupsDir.isDirectory()) return ImmutableList.of();
		
		List<Backup> list = new ArrayList<>();
		File latestSymlink = new File(backupsDir, "Last");
		
		for (File file : backupsDir.listFiles()) {
			File metadataFile = new File(file, "backupMetadata.dat");
			
			if (FileHelper.equals(file, latestSymlink) || !metadataFile.isFile()) continue;
			
			list.add(Backup.readBackup(file));
		}
		
		list.sort((b1, b2) -> b2.time.compareTo(b1.time));
		
		return ImmutableList.copyOf(list);
	}
	
	public static class Backup {
		public final File dir;
		public final BackupReason reason;
		public final Instant time;
		public final String mcVersion;
		public final byte[] iconData;
		protected String label;
		
		private static final Map<File, Backup> cache = new HashMap<>();
		
		public static Backup readBackup(File backupDir) {
			backupDir = backupDir.getAbsoluteFile();
			File metadataFile = new File(backupDir, "backupMetadata.dat");
			
			if (!metadataFile.isFile()) return null;
			if (cache.containsKey(backupDir)) return cache.get(backupDir);
			
			try {
				NBTTagCompound tag = CompressedStreamTools.read(metadataFile);
				BackupReason reason = BackupReason.values()[tag.getByte("Reason")];
				Instant time = Instant.ofEpochSecond(tag.getLong("Time"));
				byte[] iconData = tag.hasKey("Icon", 7) ? tag.getByteArray("Icon") : null;
				String label = tag.hasKey("Label", 8) ? tag.getString("Label") : null;
				
				return new Backup(backupDir, reason, time, tag.getString("mcVersion"), iconData, label);
			} catch (IOException | IndexOutOfBoundsException ex) {
				return new Backup(backupDir, null, FileHelper.getDateCreated(backupDir), null, null, null);
			}
		}

		protected Backup(File dir, BackupReason reason, Instant time, String mcVersion, byte[] iconData, String label) {
			this.dir = dir.getAbsoluteFile();
			this.reason = reason;
			this.time = time;
			this.mcVersion = mcVersion;
			this.iconData = iconData;
			this.label = label;
			
			cache.put(this.dir, this);
		}
		
		protected void writeBackup() throws IOException {
			NBTTagCompound tag = new NBTTagCompound();
			
			tag.setByte("Reason", (byte) reason.ordinal());
			tag.setLong("Time", time.getEpochSecond());
			tag.setString("mcVersion", mcVersion);
			
			if (iconData != null) tag.setByteArray("Icon", iconData);
			if (label != null) tag.setString("Label", label);
			
			CompressedStreamTools.write(tag, new File(dir, "backupMetadata.dat"));
		}
		
		public void setLabel(String label) throws IOException {
			this.label = label;
			
			writeBackup();
		}
		
		public String getLabel() {
			return label;
		}
	}

}
