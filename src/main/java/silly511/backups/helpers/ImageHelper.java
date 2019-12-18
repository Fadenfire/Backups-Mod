package silly511.backups.helpers;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.InflaterInputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;

public final class ImageHelper {
	
	public static BufferedImage createIcon(int resultSize) {
		Minecraft mc = Minecraft.getMinecraft();
		BufferedImage screenShot = ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
		
		int size = Math.min(screenShot.getWidth(), screenShot.getHeight());
		int xOffset = screenShot.getWidth() / 2 - size / 2;
		int yOffset = screenShot.getHeight() / 2 - size / 2;
		
		BufferedImage result = new BufferedImage(resultSize, resultSize, BufferedImage.TYPE_INT_RGB);
		Graphics graphics = result.createGraphics();
		
		graphics.drawImage(screenShot, 0, 0, resultSize, resultSize, xOffset, yOffset, xOffset + size, yOffset + size, null);
		graphics.dispose();
		
		return result;
	}
	
	public static BufferedImage fromCompressedBytes(byte[] data, int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		
		try (InflaterInputStream stream = new InflaterInputStream(new ByteArrayInputStream(data))) {
			for (int x = 0; x < image.getWidth(); x++)
				for (int y = 0; y < image.getHeight(); y++)
					image.setRGB(x, y, new Color(stream.read(), stream.read(), stream.read(), 0xFF).getRGB());
		}
		
		return image;
	}

}
