package silly511.backups.gui;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import silly511.backups.BackupsMod;
import silly511.backups.util.IOConsumer;

public class GuiRestoreTask extends GuiScreen {
	
	private volatile String status = "";
	private Runnable endTask;
	private final TaskThread thread;
	private boolean displayed;
	
	public GuiRestoreTask(GuiScreen parent, IOConsumer<Consumer<String>> task) {
		this.endTask = () -> mc.displayGuiScreen(parent);
		this.thread = new TaskThread(task);
	}
	
	public GuiRestoreTask(IOConsumer<Consumer<String>> task, Runnable endTask) {
		this.endTask = endTask;
		this.thread = new TaskThread(task);
	}
	
	@Override
	public void initGui() {
		if (!displayed) {
			thread.start();
			displayed = true;
		}
	}
	
	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		if (!thread.isAlive()) {
			if (thread.error != null) {
				BackupsMod.logger.error("Error restoring backup", thread.error);
				mc.displayGuiScreen(new GuiErrorScreen(I18n.format("gui.backups.errorRestoring1"), I18n.format("gui.backups.errorRestoring2")));
			} else {
				endTask.run();
				return;
			}
		}
		
		drawBackground(0);
		drawCenteredString(fontRenderer, status, width / 2, height / 2 - 20, 0xFFFFFF);
		
		super.drawScreen(mouseX, mouseY, partialTicks);
	}
	
	private class TaskThread extends Thread {
		
		private final IOConsumer<Consumer<String>> task;
		private Exception error;
		
		private TaskThread(IOConsumer<Consumer<String>> task) {
			this.task = task;
			
			this.setDaemon(true);
		}
		
		@Override
		public void run() {
			try {
				task.accept(message -> status = I18n.format(message));
			} catch (Exception ex) {
				error = ex;
			}
		}
	}

}
