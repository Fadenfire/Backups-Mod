package silly511.backups;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;

import org.apache.logging.log4j.Logger;

import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
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

@Mod(modid = BackupsMod.modid, name = "Backups", version = "1.1", acceptableRemoteVersions = "*", updateJSON = "https://raw.githubusercontent.com/Silly511/Backups-Mod/master/update.json")
public class BackupsMod {
	
	public static final String modid = "backups";
		
	public static Logger logger;
		
	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		logger = event.getModLog();
		
		Config.load(event.getSuggestedConfigurationFile());
		
		MinecraftForge.EVENT_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(new BackupManager());
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
		File tempWorldsDir = new File(FMLCommonHandler.instance().getSavesDirectory(), "tempWorlds");
		
		if (Files.isDirectory(tempWorldsDir.toPath(), LinkOption.NOFOLLOW_LINKS))
			try {
				FileHelper.deleteDirectory(tempWorldsDir);
			} catch (IOException ex) {
				logger.error("Unable to delete temp worlds", ex);
			}
	}
	
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
		if (event.gui instanceof GuiSelectWorld) {
			GuiSelectWorld gui = (GuiSelectWorld) event.gui;
			
			event.buttonList.replaceAll(button -> button == gui.recreateButton ? gui.recreateButton = new BackupsButton(button, gui) : button);
		}
	}

}
