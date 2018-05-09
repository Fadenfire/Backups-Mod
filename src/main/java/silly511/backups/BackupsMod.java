package silly511.backups;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;

import org.apache.logging.log4j.Logger;

import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
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
@Mod(modid = "backups", name = "Backups", version = "1.0", acceptableRemoteVersions = "*")
public class BackupsMod {
		
	public static Logger logger;
		
	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		logger = event.getModLog();
		
		Config.load(event.getSuggestedConfigurationFile());
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
	public static void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
		if (event.getGui() instanceof GuiWorldSelection) {
			GuiWorldSelection gui = (GuiWorldSelection) event.getGui();
			
			event.getButtonList().replaceAll(button -> button == gui.copyButton ? gui.copyButton = new BackupsButton(button, gui) : button);
		}
	}

}
