package silly511.backups.helpers;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

import org.apache.commons.io.FilenameUtils;

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
	
	public static File normalize(File file) {
		return new File(FilenameUtils.normalize(file.getAbsolutePath()));
	}
	
	public static Path relativize(File fileParent, File file, File newParent) {
		return newParent.toPath().resolve(fileParent.toPath().relativize(file.toPath()));
	}
	
	public static Path relativizeAdd(File fileParent, File file, File newParent, String extension) {
		return newParent.toPath().resolve(fileParent.toPath().relativize(file.toPath()) + extension);
	}
	
	public static Path relativizeRemove(File fileParent, File file, File newParent, String extension) {
		return newParent.toPath().resolve(FormatHelper.removeEnd(fileParent.toPath().relativize(file.toPath()).toString(), extension));
	}
	
	public static boolean equals(File f1, File f2) {
		return FilenameUtils.normalize(f1.getAbsolutePath()).equals(FilenameUtils.normalize(f2.getAbsolutePath()));
	}
	
	public static Instant getDateCreated(File file) {
		try {
			BasicFileAttributes attribs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
			
			return attribs.creationTime().toInstant();
		} catch (IOException ex) {
			return Instant.EPOCH;
		}
	}
	
	public static FileTime readGzipTime(Path file) throws IOException {
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 64))) {
			if (in.read() != 0x1F || in.read() != 0x8B) return null;
			if (in.readUnsignedByte() != Deflater.DEFLATED) return null;
			in.skip(1);
			
			return FileTime.from(Integer.reverseBytes(in.readInt()), TimeUnit.SECONDS);
		} catch (IOException ex) {
			System.out.println(file);
			throw ex;
		}
	}

}
