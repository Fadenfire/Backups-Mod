package silly511.backups.helpers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.SystemUtils;

public final class FileHelper {
	
	public static List<File> listFiles(File dir, boolean includeSelf) {
		File[] files = dir.listFiles();
		if (files == null) throw new IllegalArgumentException("File must be a directory");
		
		List<File> list = new LinkedList<>();
		if (includeSelf) list.add(dir);
		
		for (File file : files)
			if (Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS))
				list.addAll(listFiles(file, true));
			else
				list.add(file);
		
		return list;
	}
	
	public static void cleanDirectory(File dir) throws IOException {
		File[] files = dir.listFiles();
		if (files == null) throw new IllegalArgumentException("File must be a directory");
		
		for (File file : files)
			if (Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS))
				deleteDirectory(file);
			else
				Files.delete(file.toPath());
	}
	
	public static void deleteDirectory(File dir) throws IOException {
		cleanDirectory(dir);
		
		Files.delete(dir.toPath());
	}
	
	public static Path relativize(File fileParent, File file, File newParent) {
		return newParent.toPath().resolve(fileParent.toPath().relativize(file.toPath()));
	}
	
	public static boolean equals(File f1, File f2) {
		return f1.getAbsoluteFile().equals(f2.getAbsoluteFile());
	}
	
	public static void copyAttributes(Path source, Path target) throws IOException {
		BasicFileAttributes attribs = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		Files.getFileAttributeView(target, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).setTimes(
				attribs.lastModifiedTime(),
				attribs.lastAccessTime(),
				attribs.creationTime());
		
		//See https://bugs.openjdk.java.net/browse/JDK-8151430
		if (SystemUtils.IS_OS_MAC_OSX) {
			String fetchTimeCommand = "GetFileInfo -P -d \"" + source.toAbsolutePath() + "\"";
			String setTimeCommand = "SetFile -P -d \"$(" + fetchTimeCommand + ")\" \"" + target.toAbsolutePath() + "\"";
			
			new ProcessBuilder("bash", "-c", setTimeCommand).start();
		}
	}
	
	public static Instant getDateCreated(File file) {
		try {
			BasicFileAttributes attribs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
			
			return attribs.creationTime().toInstant();
		} catch (IOException ex) {
			return Instant.EPOCH;
		}
	}
	
	public static boolean hasSameModifyTime(Path file1, Path file2) throws IOException {
		if (!Files.exists(file1, LinkOption.NOFOLLOW_LINKS)) return false;
		if (!Files.exists(file2, LinkOption.NOFOLLOW_LINKS)) return false;
		
		FileTime time1 = Files.getLastModifiedTime(file1, LinkOption.NOFOLLOW_LINKS);
		FileTime time2 = Files.getLastModifiedTime(file2, LinkOption.NOFOLLOW_LINKS);
		
		return time1.equals(time2);
	}

}
