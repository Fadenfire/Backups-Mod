package silly511.backups.helpers;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;

public final class FileHelper {
	
	public static List<Path> listFiles(Path dir) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			return files.collect(Collectors.toList());
		}
	}
	
	public static List<Path> listFilesDeep(Path dir, boolean includeSelf) throws IOException {
		List<Path> list = new LinkedList<>();
		if (includeSelf) list.add(dir);
		
		for (Path path : listFiles(dir))
			if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
				list.addAll(listFilesDeep(path, true));
			else
				list.add(path);
		
		return list;
	}
	
	public static void cleanDirectory(Path dir) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			for (Path path : files.collect(Collectors.toList()))
				if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
					deleteDirectory(path);
				else
					Files.delete(path);
		}
	}
	
	public static void deleteDirectory(Path dir) throws IOException {
		cleanDirectory(dir);
		
		Files.delete(dir);
	}
	
	public static void deleteIfExists(Path path) throws IOException {
		if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
			if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
				deleteDirectory(path);
			else
				Files.delete(path);
	}
	
	public static Path relativize(Path oldParent, Path path, Path newParent) {
		return newParent.resolve(oldParent.relativize(path));
	}
	
	public static Path relativizeAdd(Path oldParent, Path path, Path newParent, String extension) {
		return newParent.resolve(oldParent.relativize(path) + extension);
	}
	
	public static Path relativizeRemove(Path oldParent, Path path, Path newParent, String extension) {
		return newParent.resolve(FormatHelper.removeEnd(oldParent.relativize(path).toString(), extension));
	}
	
	public static Instant getDateCreated(Path path) {
		try {
			BasicFileAttributes attribs = Files.readAttributes(path, BasicFileAttributes.class);
			
			return attribs.creationTime().toInstant();
		} catch (IOException ex) {
			return Instant.EPOCH;
		}
	}
	
	public static FileTime readGzipTime(Path path) throws IOException {
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path), 64))) {
			if (in.read() != 0x1F || in.read() != 0x8B) return null;
			if (in.readUnsignedByte() != Deflater.DEFLATED) return null;
			in.skip(1);
			
			return FileTime.from(Integer.reverseBytes(in.readInt()), TimeUnit.SECONDS);
		}
	}

}
