package silly511.backups.gui;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.storage.WorldSummary;

public class BackupsButtonFallback extends GuiButton {
	
	private GuiScreen parentScreen;
	
	public BackupsButtonFallback(GuiScreen parentScreen) {
		super(99999, parentScreen.width - 72, parentScreen.height - 20, 72, 20, I18n.format("gui.backups.title"));
		this.parentScreen = parentScreen;
	}
	
	@Override
	public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
		if (super.mousePressed(mc, mouseX, mouseY)) {
			WorldSummary worldSummary;
			
			try { //Why do people not extend the guis that they replace?
				Field selectionListF = parentScreen.getClass().getDeclaredField("selectionList");
				selectionListF.setAccessible(true);
				Object selectionList = selectionListF.get(parentScreen);
				Object selectedWorld = selectionList.getClass().getDeclaredMethod("getSelectedWorld").invoke(selectionList);
				if (selectedWorld == null) return false;
				Field worldSummaryF = selectedWorld.getClass().getDeclaredField("worldSummary");
				worldSummaryF.setAccessible(true);
				worldSummary = (WorldSummary) worldSummaryF.get(selectedWorld);
			} catch (ReflectiveOperationException ex) {
				throw new RuntimeException(ex);
			}
			
			mc.displayGuiScreen(new GuiRestoreBackup(parentScreen, worldSummary));
		}
		
		return false;
	}

}
