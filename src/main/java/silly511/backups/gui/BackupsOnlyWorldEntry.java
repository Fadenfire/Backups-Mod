package silly511.backups.gui;

import java.nio.file.Paths;
import java.time.ZoneId;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiListWorldSelection;
import net.minecraft.client.gui.GuiListWorldSelectionEntry;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.WorldInfo;
import net.minecraft.world.storage.WorldSummary;
import silly511.backups.Config;
import silly511.backups.helpers.BackupHelper;
import silly511.backups.helpers.BackupHelper.Backup;
import silly511.backups.helpers.FormatHelper;

public class BackupsOnlyWorldEntry extends GuiListWorldSelectionEntry {
	
	protected GuiListWorldSelection worldList;
	protected Backup latestBackup;

	public BackupsOnlyWorldEntry(String worldName, GuiListWorldSelection worldList, ISaveFormat saveFormat) {
		super(worldList, createWorldSummary(worldName), saveFormat);
		this.worldList = worldList;
		this.latestBackup = BackupHelper.listAllBackups(Paths.get(Config.backupsDir, worldName)).get(0);
	}
	
	private static WorldSummary createWorldSummary(String worldName) {
		WorldInfo worldInfo = new WorldInfo(new WorldSettings(0, GameType.SURVIVAL, false, false, WorldType.DEFAULT), worldName);
		
		return new WorldSummary(worldInfo, worldName, worldName, 0, false);
	}
	
	@Override
	public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
		Minecraft mc = Minecraft.getMinecraft();
		String time = latestBackup.time.atZone(ZoneId.systemDefault()).format(FormatHelper.dateTimeFormat);
		
		mc.fontRenderer.drawString(worldSummary.getDisplayName(), x + 3, y + 1, 0xFFFFFF);
		mc.fontRenderer.drawString(time, x + 3, y + mc.fontRenderer.FONT_HEIGHT + 3, 0x808080);
		mc.fontRenderer.drawString("Backups Only", x + 3, y + mc.fontRenderer.FONT_HEIGHT * 2 + 3, 0x808080);
	}
	
	@Override
	public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
		worldList.selectedIdx = slotIndex;
		worldList.getGuiWorldSelection().selectWorld(null);
		worldList.getGuiWorldSelection().copyButton.enabled = true;
		
		return false;
	}

}
