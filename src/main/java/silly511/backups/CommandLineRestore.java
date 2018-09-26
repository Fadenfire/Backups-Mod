package silly511.backups;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import silly511.backups.helpers.FileHelper;
import silly511.backups.util.IORunnable;

public class CommandLineRestore {

	public static void main(String[] args) throws IOException {
		if (args.length < 2) error("Usage: <backup dir> <world dir>");
		
		File backupDir = new File(args[0]).getAbsoluteFile();
		File worldDir = new File(args[1]).getAbsoluteFile();
		
		if (!backupDir.isDirectory() || !new File(backupDir, "backupMetadata.dat").isFile())
			error("Backup is invalid");
		
		System.out.println("Restoring...");
		
		restore(backupDir, worldDir);
	}
	
	private static void restore(File backupDir, File targetDir) throws IOException {
		Files.createDirectories(targetDir.toPath());
		FileHelper.cleanDirectory(targetDir);
		
		List<IORunnable> attributeCopyTasks = new LinkedList<>();
		
		for (File file : FileHelper.listFiles(backupDir, false)) {
			Path backupFile = file.toPath();
			Path targetFile = FileHelper.relativizeRemove(backupDir, file, targetDir, ".gz");
			
			if (Files.isDirectory(backupFile))
				Files.createDirectory(targetFile);
			else if (Files.isRegularFile(backupFile, LinkOption.NOFOLLOW_LINKS) && file.getPath().endsWith(".gz")) {
				try (InputStream stream = new GZIPInputStream(Files.newInputStream(backupFile))) {
					Files.copy(stream, targetFile, StandardCopyOption.REPLACE_EXISTING);
				}
				
				attributeCopyTasks.add(() -> Files.setLastModifiedTime(targetFile, FileHelper.readGzipTime(backupFile)));
			}
		}
		
		for (IORunnable t : attributeCopyTasks) t.run();
		
		try (OutputStream out = new FileOutputStream(new File(backupDir.getParentFile(), "Last"))) {
			out.write(backupDir.getName().getBytes(StandardCharsets.UTF_8));
		}
	}
	
	private static void error(String s) {
		System.err.println(s);
		System.exit(-1);
	}

}
