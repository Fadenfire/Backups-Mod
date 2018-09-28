package silly511.backups.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.attribute.FileTime;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class GzipOutputStream extends DeflaterOutputStream {
	
	private CRC32 crc = new CRC32();
	
	public GzipOutputStream(OutputStream stream, FileTime modTime) throws IOException {
		this(stream, modTime, 512);
	}
	
	public GzipOutputStream(OutputStream stream, FileTime modTime, int bufSize) throws IOException {
		super(stream, new Deflater(Deflater.BEST_COMPRESSION, true), bufSize);
		
		ByteBuffer b = ByteBuffer.allocate(10);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.put((byte) 0x1F);
		b.put((byte) 0x8B);
		b.put((byte) Deflater.DEFLATED);
		b.put((byte) 0);
		b.putInt((int) (modTime.toMillis() / 1000));
		b.put((byte) 2);
		b.put((byte) 255);
		
		stream.write(b.array());
	}
	
	@Override
	public void write(int b) throws IOException {
		super.write(b);
		crc.update(b);
	}
	
	@Override
	public void write(byte[] b) throws IOException {
		super.write(b);
		crc.update(b);
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		super.write(b, off, len);
		crc.update(b, off, len);
	}
	
	@Override
	public void finish() throws IOException {
		if (!def.finished()) {
			super.finish();
			
			ByteBuffer b = ByteBuffer.allocate(8);
			b.order(ByteOrder.LITTLE_ENDIAN);
			b.putInt((int) crc.getValue());
			b.putInt(def.getTotalIn());
			
			out.write(b.array());
		}
	}

}
