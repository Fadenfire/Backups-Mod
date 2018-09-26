package silly511.backups;

import java.awt.image.BufferedImage;
import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.text.ITextComponent;
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
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import silly511.backups.Config.AnnounceBackupsMode;
import silly511.backups.helpers.BackupHelper;
import silly511.backups.helpers.BackupHelper.Backup;
import silly511.backups.helpers.BackupHelper.BackupReason;
import silly511.backups.helpers.FileHelper;
import silly511.backups.helpers.ImageHelper;

@EventBusSubscriber
public class BackupManager {
		
	private static long nextBackupTime;
	private volatile static BackupThread thread;
	private static boolean[] oldSaveStates;
	
	public static void serverStarted() {
		thread = null;
		
		if (isTempWorld())
			nextBackupTime = System.nanoTime() + Long.MAX_VALUE;
		else if (Config.backupInterval > 0)
			startBackup(BackupReason.WORLD_JOIN, null);
	}
	
	@SubscribeEvent
	public static void serverTick(TickEvent.ServerTickEvent event) {
		if (Config.backupInterval > 0 && thread == null && System.nanoTime() - nextBackupTime >= 0)
			startBackup(BackupReason.SCHEDULED, null);
		
		if (thread != null && !thread.isAlive()) {
			restoreSaving();
			postTagMessage(thread.errored ? "backups.failed" : "backups.finished");
			
			nextBackupTime = System.nanoTime() + Config.backupInterval * 60_000_000_000L;
			thread = null;
		}
	}
	
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public static void playerJoin(PlayerLoggedInEvent event) {
		if (FMLCommonHandler.instance().getSide() == Side.CLIENT && Minecraft.getMinecraft().isSingleplayer()) {
			if (thread != null)
				postTagMessage("backups.started");
			
			if (isTempWorld())
				event.player.sendStatusMessage(new TextComponentTranslation("backups.temp_world_warning").setStyle(new Style().setColor(TextFormatting.GOLD)), false);
		}
	}
	
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public static void worldRenderLast(RenderWorldLastEvent event) {
		if (thread != null && thread.needsIcon && thread.icon == null)
			thread.icon = ImageHelper.createIcon(64);
	}
	
	public static void startBackup(BackupReason reason, String label) {
		if (thread != null) return;
		
		BackupsMod.logger.info("Starting backup");
		postTagMessage("backups.started");
		disableSaving();
		
		File saveDir = DimensionManager.getCurrentSaveRootDirectory();
		thread = new BackupThread(saveDir, new File(Config.backupsDir, saveDir.getName()), reason, label);
		thread.start();
	}
	
	public static File getCurrentBackupsDir() {
		return new File(Config.backupsDir, DimensionManager.getCurrentSaveRootDirectory().getName());
	}
	
	public static boolean isBackingUp() {
		return thread != null;
	}
	
	public static boolean isTempWorld() {
		return FileHelper.equals(DimensionManager.getCurrentSaveRootDirectory().getParentFile(), new File("tempWorlds"));
	}
	
	private static void disableSaving() {
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		BackupsMod.logger.info("Disabling world saving");
		
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
		BackupsMod.logger.info("Restoring world saving");
		
		for (int i = 0; i < oldSaveStates.length; i++) {
			WorldServer worldServer = server.worlds[i];
			
			if (worldServer != null)
				worldServer.disableLevelSaving = oldSaveStates[i];
		}
	}
	
	private static void postTagMessage(String msg) {
		if (Config.announceBackups == AnnounceBackupsMode.OFF) return;
		
		PlayerList playerList = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList();
		ITextComponent component = new TextComponentString("")
				.appendSibling(new TextComponentTranslation("backups.prefix").setStyle(new Style().setColor(TextFormatting.BLUE).setBold(true)))
				.appendText(" ")
				.appendSibling(new TextComponentTranslation(msg));
		
		for (EntityPlayerMP player : playerList.getPlayers())
			if (Config.announceBackups == AnnounceBackupsMode.ALL_PLAYERS || (Config.announceBackups == AnnounceBackupsMode.OPS_ONLY && playerList.canSendCommands(player.getGameProfile())))
				player.sendMessage(component);
	}
	
	public static class BackupThread extends Thread {
		private File worldDir;
		private File backupsDir;
		private BackupReason reason;
		private String label;
		
		@SideOnly(Side.CLIENT)
		private volatile BufferedImage icon;
		private volatile boolean needsIcon;
		
		private boolean errored;

		public BackupThread(File worldDir, File backupsDir, BackupReason reason, String label) {
			this.worldDir = worldDir;
			this.backupsDir = backupsDir;
			this.reason = reason;
			this.label = label;
			
			this.setDaemon(true);
			this.setName("Backup Thread");
		}
		
		@Override
		public void run() {
			try {
				boolean isClient = FMLCommonHandler.instance().getSide() == Side.CLIENT;
				
				if (Config.trimming.trimmingEnabled)
					BackupHelper.trimBackups(backupsDir);
				
				Backup backup = BackupHelper.backup(worldDir, backupsDir, reason, isClient ? () -> fetchIcon() : null);
				if (label != null) backup.setLabel(label);
				
				BackupsMod.logger.info("Finished backup");
			} catch (Exception ex) {
				errored = true;
				
				BackupsMod.logger.error("Backup failed", ex);
			}
		}
		
		@SideOnly(Side.CLIENT)
		private BufferedImage fetchIcon() {
			needsIcon = true;
			
			for (int i = 0; i < 50 && icon == null; i++)
				try { sleep(20); } catch (InterruptedException ex) {}
			
			return icon;
		}
		
	}

}
