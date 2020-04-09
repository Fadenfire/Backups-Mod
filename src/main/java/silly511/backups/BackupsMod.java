package silly511.backups;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;

import net.minecraft.client.AnvilConverterException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.storage.WorldSummary;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.config.Config.Type;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import silly511.backups.commands.BackupsModCommand;
import silly511.backups.commands.RestoreWorldCommandClient;
import silly511.backups.gui.BackupsButton;
import silly511.backups.gui.BackupsButtonFallback;
import silly511.backups.gui.BackupsOnlyWorldEntry;
import silly511.backups.helpers.FileHelper;

@EventBusSubscriber
@Mod(modid = BackupsMod.modid, name = "Backups", version = "1.5.5", acceptableRemoteVersions = "*", updateJSON = "https://raw.githubusercontent.com/Silly511/Backups-Mod/master/update.json")
public class BackupsMod {
	
	public static final String modid = "backups";
		
	public static Logger logger;
		
	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		logger = event.getModLog();
		
		BackupsWorldCapability.register();
	}
	
	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		try {
			File backupsDir = new File(Config.backupsDir);
			File versionFile = new File(backupsDir, "version");
			int version = 0;
			
			try {
				if (versionFile.isFile())
					version = Integer.parseInt(FileUtils.readFileToString(versionFile, StandardCharsets.UTF_8));
			} catch (IOException | NumberFormatException ex) {
				
			}
			
			if (version <= 0 && backupsDir.exists()) {
				backupsDir.renameTo(new File("backups_oldformat"));
			}
			
			backupsDir.mkdirs();
			FileUtils.write(versionFile, "1", StandardCharsets.UTF_8);
		} catch (IOException ex) {
			logger.error("Error trying to convert backups", ex);
		}
	}
	
	@EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		event.registerServerCommand(new BackupsModCommand());
	}
	
	@EventHandler
	public void serverStarted(FMLServerStartedEvent event) {
		BackupManager.serverStarted();
	}
	
	@EventHandler
	public void serverStopped(FMLServerStoppedEvent event) {
		File tempWorldsDir = new File("tempWorlds");
		
		if (Files.isDirectory(tempWorldsDir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
			synchronized (RegionFileCache.class) {
				RegionFileCache.clearRegionFileReferences();
				RegionFileCache.REGIONS_BY_FILE = new HashMap<>();
			}
			
			try {
				FileHelper.deleteDirectory(tempWorldsDir);
			} catch (IOException ex) {
				logger.error("Unable to delete temp worlds", ex);
			}
		}
	}
	
	@SubscribeEvent(priority = EventPriority.LOW)
	@SideOnly(Side.CLIENT)
	public static void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
		String className = event.getGui().getClass().getName();
		
		if (event.getGui() instanceof GuiWorldSelection) {
			GuiWorldSelection gui = (GuiWorldSelection) event.getGui();
			Minecraft mc = Minecraft.getMinecraft();
			
			if (event.getButtonList().remove(gui.copyButton))
				event.getButtonList().add(gui.copyButton = new BackupsButton(gui.copyButton, gui));
			
			if (Config.showDeletedWorlds) {
				File savesDir = new File("saves");
				File allBackupsDir = new File(Config.backupsDir);
				Set<String> worlds;
				
				try {
					worlds = mc.getSaveLoader().getSaveList().stream().map(WorldSummary::getFileName).collect(Collectors.toSet());
				} catch (AnvilConverterException ex) {
					logger.error("Unable to get world list", ex);
					return;
				}
				
				L1:
				for (File backupsDir : allBackupsDir.listFiles()) {
					if (!backupsDir.isDirectory() || worlds.contains(backupsDir.getName())) continue;
					
					for (File backupDir : backupsDir.listFiles())
						if (new File(backupDir, "backupMetadata.dat").isFile()) {
							gui.selectionList.entries.add(new BackupsOnlyWorldEntry(backupsDir.getName(), gui.selectionList, mc.getSaveLoader()));
							continue L1;
						}
				}
			}
		} else if (className.equals("com.pg85.otg.forge.gui.OTGGuiWorldSelection") || className.equals("com.pg85.otg.forge.gui.mainmenu.OTGGuiWorldSelection")) {
			event.getButtonList().add(new BackupsButtonFallback(event.getGui()));
		}
	}
	
	@SubscribeEvent
	public static void onClientTick(ClientTickEvent event) {
		if (event.phase == Phase.START)
			RestoreWorldCommandClient.onPreClientTick();
	}
	
	@SubscribeEvent
	public static void configChanged(ConfigChangedEvent event) {
		if (event.getModID().equals(modid)) ConfigManager.sync(modid, Type.INSTANCE);
	}

}
