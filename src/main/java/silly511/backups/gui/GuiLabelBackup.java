package silly511.backups.gui;

import java.io.IOException;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import silly511.backups.helpers.BackupHelper.Backup;

public class GuiLabelBackup extends GuiScreen {
	
	private GuiScreen parent;
	private Backup backup;
	
	private GuiTextField textField;
	private GuiButton submitButton;
	
	public GuiLabelBackup(GuiScreen parent, Backup backup) {
		this.parent = parent;
		this.backup = backup;
	}
	
	@Override
	public void initGui() {
		Keyboard.enableRepeatEvents(true);
		
		buttonList.add(submitButton = new GuiButton(0, width / 2 - 100, height / 4 + 108, I18n.format("gui.backups.setLabel")));
		buttonList.add(new GuiButton(1, width / 2 - 100, height / 4 + 132, I18n.format("gui.cancel")));
		
		textField = new GuiTextField(2, fontRendererObj, this.width / 2 - 100, 60, 200, 20);
		textField.setFocused(true);
		
		if (submitButton.enabled = backup.getLabel() != null)
			textField.setText(backup.getLabel());
	}
	
	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		if (button.id == 0) {
			backup.setLabel(textField.getText().trim());
			
			mc.displayGuiScreen(parent);
		} else if (button.id == 1) {
			mc.displayGuiScreen(parent);
		}
	}
	
	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();
		textField.drawTextBox();
		
		super.drawScreen(mouseX, mouseY, partialTicks);
	}
	
	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		textField.textboxKeyTyped(typedChar, keyCode);
		submitButton.enabled = textField.getText().trim().length() > 0;
		
		if (submitButton.enabled && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER))
			actionPerformed(submitButton);
	}
	
	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		textField.mouseClicked(mouseX, mouseY, mouseButton);
	}
	
	@Override
	public void updateScreen() {
		textField.updateCursorCounter();
	}
	
	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
	}

}
