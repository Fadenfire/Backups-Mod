package silly511.backups;

import java.awt.image.BufferedImage;
import java.io.File;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import silly511.backups.helpers.BackupHelper;
import silly511.backups.helpers.BackupHelper.BackupReason;
import silly511.backups.helpers.ImageHelper;

@EventBusSubscriber
public class BackupManager {
		
	private static long nextBackupTime;
	private volatile static BackupThread thread;
	private static boolean[] oldSaveStates;
	
	public static void serverStarted() {
		thread = null;
		nextBackupTime = System.nanoTime() + (isTempWorld() ? Long.MAX_VALUE : 5_000_000_000L);
	}
	
	@SubscribeEvent
	public static void serverTick(TickEvent.ServerTickEvent event) {
		if (Config.backupInterval > 0 && thread == null && System.nanoTime() - nextBackupTime >= 0)
			startBackup(BackupReason.SCHEDULED);
		
		if (thread != null && !thread.isAlive()) {
			restoreSaving();
			postTagMessage(thread.errored ? "backups.failed" : "backups.finished");
			
			nextBackupTime = System.nanoTime() + Config.backupInterval * 60_000_000_000L;
			thread = null;
		}
	}
	
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public static void worldRenderLast(RenderWorldLastEvent event) {
		if (thread != null && thread.icon == null)
			thread.icon = ImageHelper.createIcon(64);
	}
	
	public static void startBackup(BackupReason reason) {
		if (thread != null) return;
		
		postTagMessage("backups.started");
		disableSaving();
		
		thread = new BackupThread(DimensionManager.getCurrentSaveRootDirectory(), getCurrentBackupsDir(), reason);
		thread.start();
	}
	
	public static File getCurrentBackupsDir() {
		return new File(Config.backupsPath, DimensionManager.getCurrentSaveRootDirectory().getName());
	}
	
	public static boolean isBackingUp() {
		return thread != null;
	}
	
	public static boolean isTempWorld() {
		File savesDir = FMLCommonHandler.instance().getSavesDirectory();
		File worldDir = DimensionManager.getCurrentSaveRootDirectory();
		
		return worldDir.getAbsolutePath().startsWith(new File(savesDir, "tempWorlds").getAbsolutePath());
	}
	
	private static void disableSaving() {
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		
		server.getPlayerList().saveAllPlayerData();
		
		oldSaveStates = new boolean[server.worlds.length];
		
		for (int i = 0; i < oldSaveStates.length; i++) {
			WorldServer worldServer = server.worlds[i];
			if (worldServer == null) continue;
			
			oldSaveStates[i] = worldServer.disableLevelSaving;
			
			try {
				worldServer.saveAllChunks(true, null);
				worldServer.flush();
			} catch (MinecraftException ex) {
				BackupsMod.logger.warn(ex.getMessage());
			}
			
			worldServer.disableLevelSaving = true;
		}
	}
	
	private static void restoreSaving() {
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		
		for (int i = 0; i < oldSaveStates.length; i++) {
			WorldServer worldServer = server.worlds[i];
			
			if (worldServer != null)
				worldServer.disableLevelSaving = oldSaveStates[i];
		}
	}
	
	private static void postTagMessage(String msg) {
		FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().sendMessage(new TextComponentString("")
				.appendSibling(new TextComponentTranslation("backups.prefix").setStyle(new Style().setColor(TextFormatting.BLUE).setBold(true)))
				.appendText(" ")
				.appendSibling(new TextComponentTranslation(msg)));
	}
	
	public static class BackupThread extends Thread {
		private File worldDir;
		private File backupsDir;
		private BackupReason reason;
		
		private volatile BufferedImage icon;
		
		private boolean errored;

		public BackupThread(File worldDir, File backupsDir, BackupReason reason) {
			this.worldDir = worldDir;
			this.backupsDir = backupsDir;
			this.reason = reason;
			
			this.setDaemon(true);
			this.setName("Backup Thread");
		}
		
		@Override
		public void run() {
			try {
				if (FMLCommonHandler.instance().getSide() == Side.CLIENT)
					while (icon == null)
						try { sleep(20); } catch (InterruptedException ex) {}
				
				BackupHelper.trimBackups(backupsDir);
				BackupHelper.backup(worldDir, backupsDir, reason, icon);
			} catch (Exception ex) {
				errored = true;
				
				BackupsMod.logger.error("Backup failed", ex);
			}
		}
		
	}

}
