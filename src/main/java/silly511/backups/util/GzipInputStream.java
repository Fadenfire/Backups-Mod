package silly511.backups.util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class GzipInputStream extends InflaterInputStream {
	
	private FileTime modTime;
	
	private CRC32 crc = new CRC32();
	private boolean eos;
	
	public GzipInputStream(InputStream stream) throws IOException {
		this(stream, 512);
	}

	public GzipInputStream(InputStream stream, int bufSize) throws IOException {
		super(stream, new Inflater(true), bufSize);
		
		DataInputStream in = new DataInputStream(stream);
		
		if (in.read() != 0x1F || in.read() != 0x8B) throw new IOException("Corrupted GZIP file");
		if (in.readUnsignedByte() != Deflater.DEFLATED) throw new IOException("Unsupported compression method");
		int flag = in.readUnsignedByte();
		
		modTime = FileTime.from(Integer.reverseBytes(in.readInt()), TimeUnit.SECONDS);
		in.skip(2);
		
		if ((flag & 4) != 0) in.skip(Short.reverseBytes(in.readShort()));
		if ((flag & 8) != 0) while (in.readUnsignedByte() != 0);
		if ((flag & 16) != 0) while (in.readUnsignedByte() != 0);
		if ((flag & 2) != 0) in.skip(2);
	}
	
	@Override
	public int read() throws IOException {
		if (eos) return -1;
		
		int b = super.read();
		
		if (b == -1)
			readFooter();
		else
			crc.update(b);
		
		return b;
	}
	
	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (eos) return -1;
		
		int l = super.read(b, off, len);
		
		if (l == -1)
			readFooter();
		else
			crc.update(b, off, l);
		
		return l;
	}
	
	private byte[] skipBuf = new byte[512];
	
	@Override
	public long skip(long n) throws IOException {
		int len;
		
		while (n > 0) {
			if ((len = read(skipBuf, 0, (int) Math.min(skipBuf.length, n))) == -1) break;
			
			n -= len;
		}
		
		return n;
	}
	
	@SuppressWarnings("resource")
	private void readFooter() throws IOException {
		int remaining = inf.getRemaining();
		InputStream in1;
		
		if (remaining >= 8)
			in1 = new ByteArrayInputStream(buf, len - remaining, len);
		else if (remaining > 0)
			in1 = new SequenceInputStream(new ByteArrayInputStream(buf, len - remaining, len), new FilterInputStream(in) { @Override public void close() {} });
		else
			in1 = in;
		
		DataInputStream in = new DataInputStream(in1);
		long checksum = 0xffffffffL & Integer.reverseBytes(in.readInt());
		int size = Integer.reverseBytes(in.readInt());
		
		if (checksum != crc.getValue()) throw new IOException("Checksum doesn't match");
		if (size != inf.getBytesWritten()) throw new IOException("Checksum doesn't match");
		
		eos = true;
	}
	
	public FileTime getModTime() {
		return modTime;
	}

}
