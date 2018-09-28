package silly511.backups.helpers;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

import org.apache.commons.io.FileUtils;

import com.google.common.collect.ImmutableList;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.ProgressManager.ProgressBar;
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
	
	public static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d-M-yyyy h-mm-ss a");
	public static final int FORMAT_VERSION = 1;
	
	public static Backup backup(File sourceDir, File backupsDir, BackupReason reason, Supplier<BufferedImage> iconFetcher) throws IOException {
		File currentBackup = new File(backupsDir, "In-Progress");
		File lastBackup = getLastBackup(backupsDir);
		Instant time = Instant.now();
		
		Files.createDirectories(currentBackup.toPath());
		FileHelper.cleanDirectory(currentBackup);
				
		for (File file : FileHelper.listFiles(sourceDir, false)) {
			Path sourceFile = file.toPath();
			Path currentFile = FileHelper.relativize(sourceDir, file, currentBackup); //File inside current backup
			
			if (Files.isDirectory(sourceFile, LinkOption.NOFOLLOW_LINKS))
				Files.createDirectory(currentFile);
			else if (Files.isRegularFile(sourceFile, LinkOption.NOFOLLOW_LINKS)) {
				Path lastFile = FileHelper.relativizeAdd(sourceDir, file, lastBackup, ".gz"); //File inside last backup
				currentFile = currentFile.resolveSibling(currentFile.getFileName() + ".gz");
				
				if (Files.isRegularFile(lastFile, LinkOption.NOFOLLOW_LINKS) && Files.getLastModifiedTime(sourceFile).equals(FileHelper.readGzipTime(lastFile)))
					Files.createLink(currentFile, lastFile);
				else
					try (OutputStream out = new GzipOutputStream(Files.newOutputStream(currentFile), Files.getLastModifiedTime(sourceFile, LinkOption.NOFOLLOW_LINKS), 8192)) {
						Files.copy(sourceFile, out);
					}
			}
		}
		
		File finalBackupDir = new File(backupsDir, time.atZone(ZoneId.systemDefault()).format(dateFormat));
		Files.move(currentBackup.toPath(), finalBackupDir.toPath());
		
		Backup backup = new Backup(FileHelper.normalize(finalBackupDir), FORMAT_VERSION, reason, time, ForgeVersion.mcVersion, iconFetcher != null ? ImageHelper.toCompressedBytes(iconFetcher.get()) : null, null);
		backup.writeBackup();
		
		//Update last backup to newly created backup
		setLastBackup(backupsDir, finalBackupDir);
		
		return backup;
	}
	
	public static void restoreBackup(File backupDir, File targetDir, Predicate<String> filter) throws IOException {
		Files.createDirectories(targetDir.toPath());
		FileHelper.cleanDirectory(targetDir);
		
		List<IORunnable> attributeCopyTasks = new LinkedList<>();
		
		for (File file : FileHelper.listFiles(backupDir, false)) {
			Path backupFile = file.toPath();
			Path targetFile = FileHelper.relativizeRemove(backupDir, file, targetDir, ".gz");
			
			if (Files.isDirectory(backupFile))
				Files.createDirectory(targetFile);
			else if (Files.isRegularFile(backupFile, LinkOption.NOFOLLOW_LINKS) && file.getPath().endsWith(".gz")) {
				if (filter != null && filter.test(targetFile.getFileName().toString())) continue;
				
				try (GzipInputStream stream = new GzipInputStream(Files.newInputStream(backupFile), 8192)) {
					Files.copy(stream, targetFile, StandardCopyOption.REPLACE_EXISTING);
					
					attributeCopyTasks.add(() -> Files.setLastModifiedTime(targetFile, stream.getModTime()));
				}
			}
		}
		
		for (IORunnable t : attributeCopyTasks) t.run();
	}
	
	public static void trimBackups(File backupsDir) {
		Set<Long> set1 = new HashSet<>();
		Set<Long> set2 = new HashSet<>();
		Set<Long> set3 = new HashSet<>();
		
		ZoneId timeZone = ZoneId.systemDefault();
		long currentSec = Instant.now().getEpochSecond();
		
		for (Backup backup : listAllBackups(backupsDir)) {
			if (backup.label != null) continue;
			
			long day = backup.time.atZone(timeZone).toLocalDate().toEpochDay();
			long sec = backup.time.getEpochSecond();
			long secAgo = currentSec - sec;
			
			boolean shouldDelete = false;
			
			if (secAgo >= Config.trimming.perWeek * 86400) //After 8+ days, trim to every week
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
		File backupsDir = backup.dir.getParentFile();
		
		if (getLastBackup(backupsDir).equals(backup.dir)) {
			List<Backup> backups = listAllBackups(backupsDir);
			int index = backups.indexOf(backup);
			
			if (backups.size() > 1)
				setLastBackup(backupsDir, backups.get(index + (index == 0 ? 1 : -1)).dir);
		}
		
		FileHelper.deleteDirectory(backup.dir);
	}
	
	public static void setLastBackup(File backupsDir, File newLastBackup) throws IOException {
		File last = new File(backupsDir, "Last");
		
		if (Files.isSymbolicLink(last.toPath())) last.delete();
		FileUtils.write(last, newLastBackup.getName(), StandardCharsets.UTF_8);
	}
	
	public static File getLastBackup(File backupsDir) throws IOException {
		File last = new File(backupsDir, "Last");
		
		return last.isFile() ? new File(backupsDir, FileUtils.readFileToString(last, StandardCharsets.UTF_8)) : last;
	}
	
	public static void updateBackups(File allBackupsDir) {
		try {
			Path tempFile = Files.createTempFile(null, null);
			
			try {
				BackupsMod.logger.info("Converting backups to new format");
				
				List<File> backupsDirs = Arrays.stream(allBackupsDir.listFiles()).filter(File::isDirectory).collect(Collectors.toList());
				ProgressBar bar = ProgressManager.push("Converting Backups", backupsDirs.size());
				
				for (File backupsDir : backupsDirs) {
					bar.step(backupsDir.getName());
					
					for (File backupDir : backupsDir.listFiles()) {
						File metadataFile = new File(backupDir, "backupMetadata.dat");
						
						if (!metadataFile.isFile()) continue;
						BackupsMod.logger.info("Converting backup: " + backupsDir.getName() + "/" + backupDir.getName());
						
						for (File file : FileHelper.listFiles(backupDir, true)) {
							Path path = file.toPath();
							file.setWritable(true);
							
							if (!file.isFile() || file.equals(metadataFile) || Files.isSymbolicLink(path)) continue;
							
							//Check if file is already in new format
							try (InputStream in = new BufferedInputStream(new FileInputStream(file), 3)) {
								if (in.read() == 0x1F && in.read() == 0x8B && in.read() == Deflater.DEFLATED) {
									if (!file.toString().endsWith(".gz"))
										Files.move(path, path.resolveSibling(path.getFileName() + ".gz"));
									
									continue;
								}
							}
							
							try (InputStream in = new InflaterInputStream(new FileInputStream(file))) {
								Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
							} catch (ZipException ex) {
								continue; //Ignore files that aren't compressed
							}
							
							try (OutputStream out = new GzipOutputStream(new FileOutputStream(file), Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS))) {
								Files.copy(tempFile, out);
							}
							
							Files.move(path, path.resolveSibling(path.getFileName() + ".gz"));
						}
					}
				}
				
				ProgressManager.pop(bar);
			} finally {
				Files.delete(tempFile);
			}
			
			BackupsMod.logger.info("Finished converting backups");
		} catch (Exception ex) {
			throw new IllegalStateException("Error while converting backups, backups may be corrupted!", ex);
		}
	}
	
	public static List<Backup> listAllBackups(File backupsDir) {
		if (!backupsDir.isDirectory()) return ImmutableList.of();
		
		List<Backup> list = new ArrayList<>();
		
		for (File file : backupsDir.listFiles())
			if (new File(file, "backupMetadata.dat").isFile())
				list.add(Backup.getBackup(file));
		
		list.sort((b1, b2) -> b2.time.compareTo(b1.time));
		
		return ImmutableList.copyOf(list);
	}
	
	public static class Backup {
		public final File dir;
		public final int format;
		public final BackupReason reason;
		public final Instant time;
		public final String mcVersion;
		public final byte[] iconData;
		protected String label;
		
		private static final Map<File, Backup> cache = new HashMap<>();
		
		public static Backup getBackup(File backupDir) {
			backupDir = FileHelper.normalize(backupDir);
			File metadataFile = new File(backupDir, "backupMetadata.dat");
			
			if (!metadataFile.isFile()) return null;
			if (cache.containsKey(backupDir)) return cache.get(backupDir);
			
			try {
				NBTTagCompound tag = CompressedStreamTools.read(metadataFile);
				BackupReason reason = BackupReason.values()[tag.getByte("Reason")];
				Instant time = Instant.ofEpochSecond(tag.getLong("Time"));
				byte[] iconData = tag.hasKey("Icon", 7) ? tag.getByteArray("Icon") : null;
				String label = tag.hasKey("Label", 8) ? tag.getString("Label") : null;
				
				return new Backup(backupDir, tag.getInteger("Format"), reason, time, tag.getString("mcVersion"), iconData, label);
			} catch (IOException | IndexOutOfBoundsException ex) {
				return new Backup(backupDir, FORMAT_VERSION, null, FileHelper.getDateCreated(backupDir), null, null, null);
			}
		}

		protected Backup(File dir, int format, BackupReason reason, Instant time, String mcVersion, byte[] iconData, String label) {
			this.dir = dir;
			this.format = format;
			this.reason = reason;
			this.time = time;
			this.mcVersion = mcVersion;
			this.iconData = iconData;
			this.label = label;
			
			cache.put(this.dir, this);
		}
		
		protected void writeBackup() throws IOException {
			NBTTagCompound tag = new NBTTagCompound();
			
			tag.setInteger("Format", format);
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
