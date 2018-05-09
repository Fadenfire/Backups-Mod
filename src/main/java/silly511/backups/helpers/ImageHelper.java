package silly511.backups.helpers;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.shader.Framebuffer;

public final class ImageHelper {
	
	public static BufferedImage createScreenshot() {
		Minecraft mc = Minecraft.getMinecraft();
		Framebuffer framebuffer = mc.getFramebuffer();
		
		int width = OpenGlHelper.isFramebufferEnabled() ? framebuffer.framebufferTextureWidth : mc.displayWidth;
		int height = OpenGlHelper.isFramebufferEnabled() ? framebuffer.framebufferTextureHeight : mc.displayHeight;
		IntBuffer buffer = BufferUtils.createIntBuffer(width * height);
		
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		
		if (OpenGlHelper.isFramebufferEnabled()) {
			GlStateManager.bindTexture(framebuffer.framebufferTexture);
			
			GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
		} else {
			GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
		}
		
		int[] array = new int[buffer.capacity()];
		buffer.get(array);
		TextureUtil.processPixelValues(array, width, height);
		
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, width, height, array, 0, width);
		
		return image;
	}
	
	public static BufferedImage createIcon(int resultSize) {
		BufferedImage screenShot = createScreenshot();
		
		int size = Math.min(screenShot.getWidth(), screenShot.getHeight());
		int xOffset = screenShot.getWidth() / 2 - size / 2;
		int yOffset = screenShot.getHeight() / 2 - size / 2;
		
		BufferedImage result = new BufferedImage(resultSize, resultSize, BufferedImage.TYPE_INT_RGB);
		Graphics graphics = result.createGraphics();
		
		graphics.drawImage(screenShot, 0, 0, resultSize, resultSize, xOffset, yOffset, xOffset + size, yOffset + size, null);
		graphics.dispose();
		
		return result;
	}
	
	public static byte[] toCompressedBytes(BufferedImage image) {
		ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
		
		try (DeflaterOutputStream stream = new DeflaterOutputStream(byteArrayStream)) {
			for (int x = 0; x < image.getWidth(); x++) {
				for (int y = 0; y < image.getHeight(); y++) {
					Color color = new Color(image.getRGB(x, y));
					
					stream.write(color.getRed());
					stream.write(color.getGreen());
					stream.write(color.getBlue());
				}
			}
		} catch (IOException ex) {}
		
		return byteArrayStream.toByteArray();
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
