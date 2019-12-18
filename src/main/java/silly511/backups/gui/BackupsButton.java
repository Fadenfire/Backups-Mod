package silly511.backups.gui;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListWorldSelectionEntry;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;

public class BackupsButton extends GuiButton {
	
	private GuiWorldSelection parentScreen;
	
	private final String recreateText = I18n.format("selectWorld.recreate");
	private final String backupsText = I18n.format("gui.backups.title");
	private final String tooltip = I18n.format("gui.backups.backupsbutton.tooltip");

	public BackupsButton(GuiButton oldButton, GuiWorldSelection parentScreen) {
		super(oldButton.id, oldButton.x, oldButton.y, oldButton.width, oldButton.height, "");
		this.enabled = oldButton.enabled;
		this.parentScreen = parentScreen;
	}
	
	@Override
	public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
		boolean shiftKeyDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
		GuiListWorldSelectionEntry selected = parentScreen.selectionList.getSelectedWorld();
		
		this.displayString = shiftKeyDown ? recreateText : backupsText;
		this.enabled = selected == null ? false : shiftKeyDown ? !(selected instanceof BackupsOnlyWorldEntry) : true;
		
		super.drawButton(mc, mouseX, mouseY, partialTicks);
		
		if (isMouseOver()) {
			parentScreen.drawHoveringText(tooltip, mouseX, mouseY);
			GlStateManager.disableLighting();
			RenderHelper.disableStandardItemLighting();
		}
	}
	
	@Override
	public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
		if (super.mousePressed(mc, mouseX, mouseY))
			if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
				return true;
			else
				mc.displayGuiScreen(new GuiRestoreBackup(parentScreen, parentScreen.selectionList.getSelectedWorld().worldSummary));
		
		return false;
	}

}
