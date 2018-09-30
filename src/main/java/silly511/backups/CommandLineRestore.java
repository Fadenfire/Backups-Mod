package silly511.backups;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import silly511.backups.helpers.BackupHelper;

public class CommandLineRestore {

	public static void main(String[] args) throws IOException {
		if (args.length < 2) error("Usage: <backup dir> <world dir>");
		
		File backupDir = new File(args[0]).getAbsoluteFile();
		File worldDir = new File(args[1]).getAbsoluteFile();
		
		if (!backupDir.isDirectory() || !new File(backupDir, "backupMetadata.dat").isFile())
			error("Backup is invalid");
		
		System.out.println("Restoring...");
		
		BackupHelper.restoreBackup(backupDir, worldDir, null);
		
		try (OutputStream out = new FileOutputStream(new File(backupDir.getParentFile(), "Last"))) {
			out.write(backupDir.getName().getBytes(StandardCharsets.UTF_8));
		}
	}
	
	private static void error(String s) {
		System.err.println(s);
		System.exit(-1);
	}

}
