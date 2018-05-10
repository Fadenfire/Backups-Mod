package silly511.backups.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.google.common.collect.Lists;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ReportedException;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.storage.WorldSummary;
import silly511.backups.BackupsMod;
import silly511.backups.Config;
import silly511.backups.helpers.BackupHelper;
import silly511.backups.helpers.BackupHelper.Backup;
import silly511.backups.helpers.BackupHelper.BackupReason;
import silly511.backups.helpers.FileHelper;
import silly511.backups.helpers.FormatHelper;
import silly511.backups.helpers.ImageHelper;

public class GuiRestoreBackup extends GuiScreen {
	
	public static final ResourceLocation MISSING_ICON = new ResourceLocation("textures/misc/unknown_server.png");
	
	private GuiWorldSelection parentScreen;
	private String worldName;
	private File worldDir;
	private File backupsDir;
	
	private String calculatingText;
	private BackupsList list;
	
	private GuiButton restoreButton;
	private GuiButton clearLabelButton;
	private GuiButton setLabelButton;
	private GuiButton enterButton;
	private GuiButton deleteButton;
	
	public GuiRestoreBackup(GuiWorldSelection guiSelectWorld) {
		parentScreen = guiSelectWorld;
		
		WorldSummary worldSummary = guiSelectWorld.selectionList.getSelectedWorld().worldSummary;
		
		worldName = worldSummary.getDisplayName();
		worldDir = new File("saves", worldSummary.getFileName());
		backupsDir = new File(Config.backupsPath, worldSummary.getFileName());
	}
	
	@Override
	public void initGui() {
		calculatingText = I18n.format("gui.backups.calculating");
		
		list = new BackupsList(BackupHelper.listAllBackups(backupsDir));
		
		buttonList.add(restoreButton = new GuiButton(0, width / 2 - 154, height - 52, 150, 20, I18n.format("gui.backups.restore")));
		buttonList.add(clearLabelButton = new GuiButton(1, width / 2 - 154, height - 28, 72, 20, I18n.format("gui.backups.clearLabel")));
		buttonList.add(setLabelButton = new GuiButton(2, width / 2 - 76, height - 28, 72, 20, I18n.format("gui.backups.setLabel")));
		buttonList.add(enterButton = new GuiButton(3, width / 2 + 4, height - 52, 150, 20, I18n.format("gui.backups.enter")));
		buttonList.add(deleteButton = new GuiButton(4, width / 2 + 4, height - 28, 72, 20, I18n.format("gui.backups.delete")));
		buttonList.add(new GuiButton(5, width / 2 + 82, height - 28, 72, 20, I18n.format("gui.cancel")));
		
		restoreButton.enabled = false;
		clearLabelButton.enabled = false;
		setLabelButton.enabled = false;
		enterButton.enabled = false;
		deleteButton.enabled = false;
	}
	
	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();
		list.drawScreen(mouseX, mouseY, partialTicks);
		
		String size = list.totalDirSize != -1 ? FormatHelper.shortenNumber(list.totalDirSize, 1024) + "Bs" : calculatingText;
		
		drawCenteredString(fontRenderer, I18n.format("gui.backups.title"), width / 2, 20, 0xFFFFFF);
		drawString(fontRenderer, size, width - 5 - fontRenderer.getStringWidth(size), 20, 0xFFFFFF);
		super.drawScreen(mouseX, mouseY, partialTicks);
	}
	
	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		Backup backup = list.getSelected();
		
		if (button.id == 0) {
			String time = backup.time.atZone(ZoneId.systemDefault()).format(FormatHelper.dateTimeFormat);
			String message1 = I18n.format("gui.backups.confirmRestore1", worldName, time);
			String message2 = I18n.format("gui.backups.confirmRestore2");
			
			mc.displayGuiScreen(new GuiYesNo(this, message1, message2, I18n.format("gui.backups.restore"), I18n.format("gui.cancel"), button.id));
		} else if (button.id == 1) {
			backup.setLabel(null);
		} else if (button.id == 2) {
			mc.displayGuiScreen(new GuiLabelBackup(this, backup));
		} else if (button.id == 3) {
			String tempWorldDir = "tempWorlds" + File.separatorChar + new Random().nextInt();
			
			BackupHelper.restoreBackup(backup.dir, new File("saves", tempWorldDir));
			
			mc.launchIntegratedServer(tempWorldDir, worldName, null);
		} else if (button.id == 4) {
			String message = I18n.format("gui.backups.confirmDelete", list.selected.size());
			
			mc.displayGuiScreen(new GuiYesNo(this, message, "", I18n.format("gui.backups.delete"), I18n.format("gui.cancel"), button.id));
		} else if (button.id == 5) {
			mc.displayGuiScreen(parentScreen);
		}
	}
	
	@Override
	public void confirmClicked(boolean result, int id) {
		super.confirmClicked(result, id);
		
		try {
			Backup backup = list.getSelected();
			
			if (!result) {
				mc.displayGuiScreen(this);
			} else if (id == 0) {
				BackupHelper.backup(worldDir, backupsDir, BackupReason.RESTORE, null);
				BackupHelper.restoreBackup(backup.dir, worldDir);
				BackupHelper.setLastBackup(backupsDir, backup.dir);
				
				mc.displayGuiScreen(parentScreen);
			} else if (id == 4) {
				for (BackupsList.BackupsListEntry entry : list.selected)
					BackupHelper.deleteBackup(entry.backup);
				
				mc.displayGuiScreen(this);
			}
		} catch (IOException ex) {
			CrashReport crashReport = CrashReport.makeCrashReport(ex, "Handling button press");
			throw new ReportedException(crashReport);
		}
	}
	
	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		list.handleMouseInput();
	}
	
	private class BackupsList extends GuiSlot {
		
		public final List<BackupsListEntry> entries = new ArrayList<>();
		public final Set<BackupsListEntry> selected = new HashSet<>();
		
		public long totalDirSize = -1;
		
		public BackupsList(List<Backup> backups) {
			super(GuiRestoreBackup.this.mc, GuiRestoreBackup.this.width, GuiRestoreBackup.this.height, 32, GuiRestoreBackup.this.height - 64, 31);
			headerPadding = 2;
			
			ZoneId timeZone = ZoneId.systemDefault();
			long currentDay = LocalDate.now().toEpochDay();
			LocalDate lastDate = LocalDate.MIN;
			
			for (Backup backup : backups) {
				LocalDate date = backup.time.atZone(timeZone).toLocalDate();
				long fromNow = currentDay - date.toEpochDay();
				String header = null;
				
				if (!date.equals(lastDate))
					if (fromNow >= 30) {
						if (date.getYear() != lastDate.getYear() || date.getMonth() != lastDate.getMonth()) {
							header = I18n.format("backups.misc.month." + date.getMonth().toString().toLowerCase()) + ", " + date.getYear();
							lastDate = date;
						}
					} else if ((fromNow >= 3 && (date.toEpochDay() + 3) / 7 != (lastDate.toEpochDay() + 3) / 7) || fromNow < 3) {
						header = FormatHelper.relativeDateFormat(date);
						lastDate = date;
					}
				
				entries.add(new BackupsListEntry(backup, header));
			}
			
			new BackupsSizeThread().start();
		}
		
		@Override
		protected int getSize() {
			return entries.size();
		}
		
		@Override
		protected void elementClicked(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY) {
			if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
				selected.clear();
			
			selected.add(entries.get(slotIndex));
			
			restoreButton.enabled = clearLabelButton.enabled = setLabelButton.enabled = enterButton.enabled = selected.size() == 1;
			deleteButton.enabled = selected.size() > 0;
		}
		
		@Override
		protected boolean isSelected(int slotIndex) {
			return selected.contains(entries.get(slotIndex));
		}
		
		@Override
		protected void drawBackground() {}
		
		@Override
		protected void drawSlot(int entryID, int x, int y, int height, int mouseX, int mouseY, float partialTicks) {
			BackupsListEntry entry = entries.get(entryID);
			
			Backup backup = entry.backup;
			String time = backup.time.atZone(ZoneId.systemDefault()).format(FormatHelper.dateTimeFormat);
			String reason = I18n.format(backup.reason != null ? backup.reason.tranKey : "backups.reason.unknown");
			String size = entry.size != -1 ? FormatHelper.shortenNumber(entry.size, 1024) + "Bs" : calculatingText;
			String title = backup.getLabel() == null ? time : TextFormatting.UNDERLINE + backup.getLabel() + TextFormatting.RESET + "" + TextFormatting.GRAY + " (" + time + ")";
			
			fontRenderer.drawString(title, x + 27, y + 1, 0xFFFFFF);
			fontRenderer.drawString(reason + ", " + backup.mcVersion + " (" + size + ")", x + 27, y + 12, 0x808080);
			
			if (entry.header != null) {
				int x2 = Math.max(x - 120, 5);
				
				drawHorizontalLine(x2, x + getListWidth(), y - 4, 0xFFA9A9A9);
				fontRenderer.drawString(entry.header, x2, y - 2, 0xA9A9A9);
			}
			
			mc.getTextureManager().bindTexture(entry.iconLoc == null ? MISSING_ICON : entry.iconLoc);
			GlStateManager.color(1, 1, 1);
			GlStateManager.enableBlend();
			
			drawModalRectWithCustomSizedTexture(x, y, 0, 0, 24, 24, 24, 24);
			
			GlStateManager.disableBlend();
		}
		
		@Override
		protected void drawSelectionBox(int x, int y, int mouseX, int mouseY, float partialTicks) {
			int size = getSize();
			
			for (int i = 0; i < size; i++) {
				int slotY = y + i * slotHeight + headerPadding;
				
				if (showSelectionBox && isSelected(i)) {
					int minX = left + (width / 2 - getListWidth() / 2);
					int maxX = left + (width / 2 + getListWidth() / 2);
					
					drawRect(minX, slotY, maxX, slotY + slotHeight - 3, 0xFF808080);
					drawRect(minX + 1, slotY + 1, maxX - 1, slotY + slotHeight - 4, 0xFF000000);
				}
				
				drawSlot(i, x, slotY + 2, slotHeight - 4, mouseX, mouseY, partialTicks);
			}
		}
		
		public Backup getSelected() {
			return selected.isEmpty() ? null : selected.iterator().next().backup;
		}
		
		private class BackupsSizeThread extends Thread {
			
			public BackupsSizeThread() {
				this.setDaemon(true);
			}
			
			@Override
			public void run() {
				try {
					Set<Object> fileKeys = new HashSet<>();
					long total = 0;
					
					for (BackupsListEntry entry : Lists.reverse(entries)) {
						long size = 0;
						
						for (File file : FileHelper.listFiles(entry.backup.dir, false)) {
							BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
							
							if (!attr.isDirectory() && fileKeys.add(attr.fileKey()))
								size += attr.size();
						}
						
						total += entry.size = size;
					}
					
					totalDirSize = total;
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
		
		private class BackupsListEntry {
			
			public final Backup backup;
			public final String header;
			public long size = -1;
			
			public ResourceLocation iconLoc;
			
			public BackupsListEntry(Backup backup, String header) {
				this.backup = backup;
				this.header = header;
				
				if (backup.iconData != null) {
					iconLoc = new ResourceLocation(BackupsMod.modid, "backup/" + worldDir.getName() + "/" + backup.dir.getName() + "/icon");
					
					if (mc.getTextureManager().getTexture(iconLoc) == null)
						try {
							BufferedImage image = ImageHelper.fromCompressedBytes(backup.iconData, 64, 64);
							
							mc.getTextureManager().loadTexture(iconLoc, new DynamicTexture(image));
						} catch (IOException ex) {
							BackupsMod.logger.warn("Unable to load icon for backup " + backup.time, ex);
							
							iconLoc = null;
						}
				}
			}
			
		}
		
	}

}
