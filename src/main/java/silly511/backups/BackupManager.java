package silly511.backups;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
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
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import silly511.backups.Config.AnnounceBackupsMode;
import silly511.backups.helpers.BackupHelper;
import silly511.backups.helpers.BackupHelper.Backup;
import silly511.backups.helpers.BackupHelper.BackupReason;
import silly511.backups.helpers.ImageHelper;

@EventBusSubscriber(modid = BackupsMod.modid)
public class BackupManager {
	
	private static BackupsWorldCapability worldCapability;
	private static boolean shouldBackup;
	private volatile static BackupThread thread;
	private static Map<WorldServer, Boolean> oldSaveStates;
	
	public static void serverStarted() {
		worldCapability = null;
		shouldBackup = Config.backupInterval > 0 && !isTempWorld();
	}
	
	@SubscribeEvent
	public static void serverTick(TickEvent.ServerTickEvent event) {
		if (event.phase != Phase.END) return;
		
		PlayerList playerList = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList();
		
		if (worldCapability == null) {
			World overworld = DimensionManager.getWorld(0);
			
			if (overworld != null)
				worldCapability = overworld.getCapability(BackupsWorldCapability.capability, null);
		} else if (shouldBackup && thread == null && worldCapability.nextBackupTimer >= (Config.backupInterval * (60 * 20)) &&
				(Config.backupWhenServerEmpty || playerList.getPlayers().size() > 0)) {
			worldCapability.nextBackupTimer = 0;
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
		
		if (worldCapability.nextBackupTimer % 6000 == 300 && isTempWorld())
			for (EntityPlayer player : playerList.getPlayers())
				player.sendStatusMessage(new TextComponentTranslation("backups.temp_world_warning").setStyle(new Style().setColor(TextFormatting.GOLD)), false);
		
		worldCapability.nextBackupTimer++;
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
		
		Set<ISaveHandler> saveHandlers = disableSaving();
		File saveDir = DimensionManager.getCurrentSaveRootDirectory();
		
		thread = new BackupThread(saveHandlers, saveDir.toPath(), Paths.get(Config.backupsDir, saveDir.getName()), reason, label);
		thread.start();
	}
	
	public static Path getCurrentBackupsDir() {
		return Paths.get(Config.backupsDir, DimensionManager.getCurrentSaveRootDirectory().getName());
	}
	
	public static boolean isBackingUp() {
		return thread != null;
	}
	
	public static boolean isTempWorld() {
		return Paths.get("tempWorlds").toAbsolutePath().equals(DimensionManager.getCurrentSaveRootDirectory().getParentFile().toPath().toAbsolutePath().normalize());
	}
	
	private static Set<ISaveHandler> disableSaving() {
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		BackupsMod.logger.info("Disabling world saving");
		
		server.getPlayerList().saveAllPlayerData();
		
		oldSaveStates = new WeakHashMap<>();
		Set<ISaveHandler> saveHandlers = new HashSet<>();
		
		for (WorldServer worldServer : server.worlds) {
			if (worldServer == null) continue;
			
			oldSaveStates.put(worldServer, worldServer.disableLevelSaving);
			
			try {
				worldServer.saveAllChunks(true, null);
			} catch (MinecraftException ex) {
				BackupsMod.logger.warn("Error saving chunks", ex);
			}
			
			saveHandlers.add(worldServer.getSaveHandler());
			worldServer.disableLevelSaving = true;
		}
		
		return saveHandlers;
	}
	
	private static void restoreSaving() {
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		BackupsMod.logger.info("Restoring world saving");
		
		for (WorldServer worldServer : server.worlds) {
			if (worldServer != null) {
				Boolean oldSaveState = oldSaveStates.get(worldServer);
				
				if (oldSaveState != null) {
					worldServer.disableLevelSaving = oldSaveState;
				}
			}
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
		private Set<ISaveHandler> saveHandlers;
		
		private Path worldDir;
		private Path backupsDir;
		private BackupReason reason;
		private String label;
		
		@SideOnly(Side.CLIENT)
		private volatile BufferedImage icon;
		private volatile boolean needsIcon;
		
		private boolean errored;

		public BackupThread(Set<ISaveHandler> saveHandlers, Path worldDir, Path backupsDir, BackupReason reason, String label) {
			this.saveHandlers = saveHandlers;
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
				BackupsMod.logger.info("Flushing world data to disk");
				
				for (ISaveHandler saveHandler : saveHandlers) {
					saveHandler.flush();
				}
				
				saveHandlers.clear();
				
				boolean isClient = FMLCommonHandler.instance().getSide() == Side.CLIENT;
				
				if (Config.trimming.trimmingEnabled) {
					BackupsMod.logger.info("Trimming old backups");
					BackupHelper.trimBackups(backupsDir);
				}
				
				BackupsMod.logger.info("Creating backup");
				Backup backup = BackupHelper.backup(worldDir, backupsDir, reason, isClient ? () -> fetchIcon() : null);
				
				if (label != null) {
					backup.setLabel(label);
					backup.writeBackup();
				}
				
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
