package silly511.backups;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.HashMap;

import org.apache.logging.log4j.Logger;

import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import silly511.backups.commands.BackupCommand;
import silly511.backups.commands.RestoreCommand;
import silly511.backups.gui.BackupsButton;
import silly511.backups.helpers.FileHelper;

@EventBusSubscriber
@Mod(modid = BackupsMod.modid, name = "Backups", version = "1.3", acceptableRemoteVersions = "*", updateJSON = "https://raw.githubusercontent.com/Silly511/Backups-Mod/master/update.json")
public class BackupsMod {
	
	public static final String modid = "backups";
		
	public static Logger logger;
		
	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		logger = event.getModLog();
	}
	
	@EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		event.registerServerCommand(new BackupCommand());
		event.registerServerCommand(new RestoreCommand());
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
	
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public static void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
		if (event.getGui() instanceof GuiWorldSelection) {
			GuiWorldSelection gui = (GuiWorldSelection) event.getGui();
			
			event.getButtonList().replaceAll(button -> button == gui.copyButton ? gui.copyButton = new BackupsButton(button, gui) : button);
		}
	}
	
	@SubscribeEvent
	public static void configChanged(ConfigChangedEvent event) {
		if (event.getModID().equals(modid)) ConfigManager.sync(modid, Config.Type.INSTANCE);
	}

}
