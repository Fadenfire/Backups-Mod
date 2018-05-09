package silly511.backups.gui;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.resources.I18n;

public class BackupsButton extends GuiButton {
	
	private GuiSelectWorld parentScreen;
	
	private final String recreateText = I18n.format("selectWorld.recreate");
	private final String backupsText = I18n.format("gui.backups.title");

	public BackupsButton(GuiButton oldButton, GuiSelectWorld parentScreen) {
		super(oldButton.id, oldButton.xPosition, oldButton.yPosition, oldButton.width, oldButton.height, "");
		this.enabled = oldButton.enabled;
		this.parentScreen = parentScreen;
	}
	
	@Override
	public void drawButton(Minecraft mc, int mouseX, int mouseY) {
		boolean optionKeyDown = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
		this.displayString = optionKeyDown ? recreateText : backupsText;
		
		super.drawButton(mc, mouseX, mouseY);
	}
	
	@Override
	public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
		if (super.mousePressed(mc, mouseX, mouseY))
			if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU))
				return true;
			else
				mc.displayGuiScreen(new GuiRestoreBackup(parentScreen));
		
		return false;
	}

}
