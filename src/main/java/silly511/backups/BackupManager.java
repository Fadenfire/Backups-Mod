package silly511.backups;

import java.awt.image.BufferedImage;
import java.io.File;

import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
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
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
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

@EventBusSubscriber(modid = BackupsMod.modid)
public class BackupManager {
	
	private static WorldInfo worldInfo;
	private static boolean shouldBackup;
	private volatile static BackupThread thread;
	private static Int2BooleanMap oldSaveStates;
	
	public static void serverStarted() {
		worldInfo = null;
		shouldBackup = Config.backupInterval > 0 && !isTempWorld();
	}
	
	@SubscribeEvent
	public static void serverTick(TickEvent.ServerTickEvent event) {
		if (worldInfo == null) {
			World overworld = DimensionManager.getWorld(0);
			
			if (overworld != null)
				worldInfo = overworld.getWorldInfo();
		} else if (shouldBackup && thread == null && worldInfo.getWorldTotalTime() % (Config.backupInterval * (60 * 20)) == 0) {
			startBackup(BackupReason.SCHEDULED, null);
		}
		
		if (thread != null && !thread.isAlive()) {
			restoreSaving();
			
			if (thread.errored)
				postTagMessage("Backup failed! Check log for more details");
			else
				postTagMessage("Finished backup");
			
			thread = null;
		}
	}
	
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public static void playerJoin(PlayerLoggedInEvent event) {
		if (FMLCommonHandler.instance().getSide() == Side.CLIENT && Minecraft.getMinecraft().isSingleplayer()) {
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
		postTagMessage("Started backup");
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
		
		oldSaveStates = new Int2BooleanOpenHashMap(server.worlds.length);
		
		for (WorldServer worldServer : server.worlds) {
			if (worldServer == null) continue;
			
			oldSaveStates.put(worldServer.provider.getDimension(), worldServer.disableLevelSaving);
			
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
		
		for (WorldServer worldServer : server.worlds) {
			int dimId = worldServer.provider.getDimension();
			
			if (worldServer != null && oldSaveStates.containsKey(dimId))
				worldServer.disableLevelSaving = oldSaveStates.get(dimId);
		}
		
		oldSaveStates = null;
	}
	
	private static void postTagMessage(String msg) {
		if (Config.announceBackups == AnnounceBackupsMode.OFF) return;
		
		PlayerList playerList = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList();
		Style prefixStyle = new Style().setColor(TextFormatting.BLUE).setBold(true);
		
		ITextComponent text = new TextComponentString("")
				.appendSibling(new TextComponentString("[Backups]: ").setStyle(prefixStyle))
				.appendSibling(new TextComponentString(msg));
		
		for (EntityPlayerMP player : playerList.getPlayers())
			if (Config.announceBackups == AnnounceBackupsMode.ALL_PLAYERS || (Config.announceBackups == AnnounceBackupsMode.OPS_ONLY && playerList.canSendCommands(player.getGameProfile())))
				player.sendMessage(text);
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
