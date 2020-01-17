package silly511.backups;

import java.io.File;
import java.io.IOException;

import silly511.backups.helpers.BackupHelper;

public class CommandLineRestore {

	public static void main(String[] args) throws IOException {
		if (args.length < 2) error("Usage: <backup dir> <world dir>");
		
		File backupDir = new File(args[0]).getAbsoluteFile();
		File worldDir = new File(args[1]).getAbsoluteFile();
		
		if (!backupDir.isDirectory() || !new File(backupDir, "backupMetadata.dat").isFile())
			error("Backup is invalid");
		
		System.out.println("Restoring...");
		
		BackupHelper.restoreBackup(backupDir, worldDir, worldDir.getParentFile(), null);
		//BackupHelper.setLastBackup(backupDir.getParentFile(), backupDir);
	}
	
//	private static void restore(File backupDir, File targetDir) throws IOException {
//		Files.createDirectories(targetDir.toPath());
//		FileHelper.cleanDirectory(targetDir);
//		
//		List<IORunnable> attributeCopyTasks = new LinkedList<>();
//		
//		for (File file : FileHelper.listFiles(backupDir, false)) {
//			Path backupFile = file.toPath();
//			Path targetFile = FileHelper.relativizeRemove(backupDir, file, targetDir, ".gz");
//			
//			if (Files.isDirectory(backupFile))
//				Files.createDirectory(targetFile);
//			else if (Files.isRegularFile(backupFile, LinkOption.NOFOLLOW_LINKS) && file.getPath().endsWith(".gz")) {
//				try (InputStream stream = new GZIPInputStream(Files.newInputStream(backupFile))) {
//					Files.copy(stream, targetFile, StandardCopyOption.REPLACE_EXISTING);
//				}
//				
//				attributeCopyTasks.add(() -> Files.setLastModifiedTime(targetFile, FileHelper.readGzipTime(backupFile)));
//			}
//		}
//		
//		for (IORunnable t : attributeCopyTasks) t.run();
//		
//		try (OutputStream out = new FileOutputStream(new File(backupDir.getParentFile(), "Last"))) {
//			out.write(backupDir.getName().getBytes(StandardCharsets.UTF_8));
//		}
//	}
	
	private static void error(String s) {
		System.err.println(s);
		System.exit(-1);
	}

}
