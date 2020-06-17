package silly511.backups.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.SystemUtils;
import org.lwjgl.input.Keyboard;

import com.google.common.collect.Lists;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.crash.CrashReport;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ReportedException;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.storage.WorldSummary;
import silly511.backups.BackupsMod;
import silly511.backups.Config;
import silly511.backups.helpers.BackupHelper;
import silly511.backups.helpers.BackupHelper.Backup;
import silly511.backups.helpers.BackupHelper.BackupReason;
import silly511.backups.helpers.FileHelper;
import silly511.backups.helpers.FormatHelper;
import silly511.backups.helpers.ImageHelper;
import silly511.backups.util.LoadFromBackupMap;

public class GuiRestoreBackup extends GuiScreen {
	
	public static final ResourceLocation MISSING_ICON = new ResourceLocation("textures/misc/unknown_server.png");
	
	private GuiScreen parentScreen;
	private String worldName;
	private Path worldDir;
	private Path backupsDir;
	
	private String calculatingText;
	private String restoreTooltip;
	private String enterBackupTooltip;
	private BackupsList list;
	
	private GuiButton restoreButton;
	private GuiButton clearLabelButton;
	private GuiButton setLabelButton;
	private GuiButton enterButton;
	private GuiButton deleteButton;
	
	private int tooltipCounter;
	
	public GuiRestoreBackup(GuiScreen guiSelectWorld, WorldSummary worldSummary) {
		parentScreen = guiSelectWorld;
		
		worldName = worldSummary.getDisplayName();
		worldDir = Paths.get("saves", worldSummary.getFileName());
		backupsDir = Paths.get(Config.backupsDir, worldSummary.getFileName());
	}
	
	@Override
	public void initGui() {
		calculatingText = I18n.format("gui.backups.calculating");
		restoreTooltip = I18n.format("gui.backups.restore.tooltip");
		enterBackupTooltip = I18n.format("gui.backups.enter.tooltip");
		
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
		
		drawCenteredString(fontRenderer, I18n.format("gui.backups.title"), width / 2, 20, 0xFFFFFF);
		drawString(fontRenderer, list.totalDirSize, width - 5 - fontRenderer.getStringWidth(list.totalDirSize), 20, 0xFFFFFF);
		super.drawScreen(mouseX, mouseY, partialTicks);
		
		if (tooltipCounter >= 10) {
			if (restoreButton.isMouseOver())
				drawHoveringText(restoreTooltip, mouseX, mouseY);
			else if (enterButton.isMouseOver())
				drawHoveringText(enterBackupTooltip, mouseX, mouseY);
		}
	}
	
	@Override
	public void updateScreen() {
		if (restoreButton.isMouseOver() || enterButton.isMouseOver()) {
			if (tooltipCounter < 20)
				tooltipCounter++;
		} else {
			tooltipCounter = 0;
		}
		
		super.updateScreen();
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
			backup.writeBackup();
		} else if (button.id == 2) {
			mc.displayGuiScreen(new GuiLabelBackup(this, backup));
		} else if (button.id == 3) {
			Path tempWorldDir = Paths.get("tempWorlds", String.valueOf(new Random().nextInt()));
			
			mc.displayGuiScreen(new GuiRestoreTask(status -> {
				status.accept("gui.backups.loadingBackup");
				
				//Restore backup to temp dir, but without region files
				BackupHelper.restoreBackup(backup.dir, tempWorldDir, Paths.get("temp"), f -> f.endsWith(".mca"));
			}, () -> {
				//Add custom region file cache that will load region files from the backup
				synchronized (RegionFileCache.class) {
					RegionFileCache.clearRegionFileReferences();
					RegionFileCache.REGIONS_BY_FILE = new LoadFromBackupMap(backup.dir, tempWorldDir);
				}
				
				try {
					mc.launchIntegratedServer(".." + File.separator + tempWorldDir, worldName, null);
				} catch (Exception e) {
					BackupsMod.logger.error("ffdsf", e);
					mc.displayGuiScreen(null);
				}
			}));
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
				mc.displayGuiScreen(new GuiRestoreTask(parentScreen, status -> {
					if (Files.isDirectory(worldDir)) {
						status.accept("gui.backups.makingBackup");
						BackupHelper.backup(worldDir, backupsDir, BackupReason.RESTORE, null);
					}
					
					status.accept("gui.backups.restoring");
					BackupHelper.restoreBackup(backup.dir, worldDir, Paths.get("temp"), null);
					BackupHelper.setLastBackup(backupsDir, backup.dir);
				}));
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
	
	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (isKeyComboCtrlA(keyCode))
			list.selectAll();
		
		super.keyTyped(typedChar, keyCode);
	}
	
	private class BackupsList extends GuiSlot {
		
		public final List<BackupsListEntry> entries = new ArrayList<>();
		public final Set<BackupsListEntry> selected = new HashSet<>();
		
		public volatile String totalDirSize;
		
		public BackupsList(List<Backup> backups) {
			super(GuiRestoreBackup.this.mc, GuiRestoreBackup.this.width, GuiRestoreBackup.this.height, 32, GuiRestoreBackup.this.height - 64, 31);
			this.headerPadding = 2;
			this.totalDirSize = calculatingText;
			
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
			
			if (isDoubleClick && enterButton.enabled)
				try {
					GuiRestoreBackup.this.actionPerformed(enterButton);
				} catch (Exception ex) {
					CrashReport crashReport = CrashReport.makeCrashReport(ex, "Handling button press");
					throw new ReportedException(crashReport);
				}
		}
		
		public void selectAll() {
			selected.addAll(entries);
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
			
			fontRenderer.drawString(entry.title, x + 27, y + 1, 0xFFFFFF);
			fontRenderer.drawString(entry.desc + entry.size + ")", x + 27, y + 12, 0x808080);
			
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
						
						for (Path path : FileHelper.listFilesDeep(entry.backup.dir, false)) {
							BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
							
							if (!attr.isDirectory() && fileKeys.add(getFileKey(attr)))
								size += attr.size();
						}
						
						entry.size = FormatHelper.shortenNumber(size, 1024) + "Bs";
						total += size;
					}
					
					totalDirSize = FormatHelper.shortenNumber(total, 1024) + "Bs";
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
			
			public Object getFileKey(BasicFileAttributes attr) {
				if (SystemUtils.IS_OS_WINDOWS && winFileAttribsClass != null && winFileAttribsClass.isInstance(attr)) {
					try {
						WindowsFileKey key = new WindowsFileKey();
						key.volSerialNumber = winVolSerialNumberF.getInt(attr);
						key.fileIndexHigh = winFileIndexHighF.getInt(attr);
						key.fileIndexLow = winFileIndexLowF.getInt(attr);
						
						return key;
					} catch (IllegalAccessException ex) {}
				}
				
				return attr.fileKey();
			}
		}
		
		private class BackupsListEntry {
			
			public final Backup backup;
			public final String header;
			public final String title;
			public final String desc;
			public volatile String size;
			
			public ResourceLocation iconLoc;
			
			public BackupsListEntry(Backup backup, String header) {
				this.backup = backup;
				this.header = header;
				this.size = calculatingText;
				
				String time = backup.time.atZone(ZoneId.systemDefault()).format(FormatHelper.dateTimeFormat);
				this.title = backup.getLabel() == null ? time : TextFormatting.UNDERLINE + backup.getLabel() + TextFormatting.RESET + "" + TextFormatting.GRAY + " (" + time + ")";
				
				String reason = I18n.format(backup.reason != null ? backup.reason.tranKey : "backups.reason.unknown");
				this.desc = reason + ", " + backup.mcVersion + " (";
				
				ResourceLocation iconLoc = new ResourceLocation(BackupsMod.modid, "backup/" + worldDir.getFileName() + "/" + backup.dir.getFileName() + "/icon");
				Path iconFile = backup.dir.resolve("icon.png");
				BufferedImage image = null;
				
				try {
					if (Files.isRegularFile(iconFile)) {
						image = ImageIO.read(iconFile.toFile());
					} else {
						Path metadataFile = backup.dir.resolve("backupMetadata.dat");
						
						if (Files.isRegularFile(metadataFile)) {
							NBTTagCompound tag = CompressedStreamTools.read(metadataFile.toFile());
							
							if (tag.hasKey("Icon"))
								image = ImageHelper.fromCompressedBytes(tag.getByteArray("Icon"), 64, 64);
						}
					}
				} catch (IOException ex) {
					BackupsMod.logger.error("Unable to load icon for backup " + backup.time, ex);
				}
				
				if (image != null) {
					mc.getTextureManager().loadTexture(iconLoc, new DynamicTexture(image));
					this.iconLoc = iconLoc;
				}
			}
			
		}
		
	}
	
	//I have to use this mess because for some reason BasicFileAttributes.fileKey() just returns null on windows
	
	private static Class<?> winFileAttribsClass;
	private static Field winVolSerialNumberF, winFileIndexHighF, winFileIndexLowF;
	
	static {
		if (SystemUtils.IS_OS_WINDOWS) {
			Class<?> fileAttribsClass = null;
			
			try {
				fileAttribsClass = Class.forName("sun.nio.fs.WindowsFileAttributes");
			} catch (ClassNotFoundException ex) {}
			
			if (fileAttribsClass != null) {
				Field volSerialNumber = null, fileIndexHigh = null, fileIndexLow = null;
				boolean foundAll = false;
				
				try {
					volSerialNumber = fileAttribsClass.getDeclaredField("volSerialNumber");
					volSerialNumber.setAccessible(true);
					
					fileIndexHigh = fileAttribsClass.getDeclaredField("fileIndexHigh");
					fileIndexHigh.setAccessible(true);
					
					fileIndexLow = fileAttribsClass.getDeclaredField("fileIndexLow");
					fileIndexLow.setAccessible(true);
					
					foundAll = true;
				} catch (NoSuchFieldException ex) {}
				
				if (foundAll) {
					winFileAttribsClass = fileAttribsClass;
					winVolSerialNumberF = volSerialNumber;
					winFileIndexHighF = fileIndexHigh;
					winFileIndexLowF = fileIndexLow;
				}
			}
		}
	}
	
	private static class WindowsFileKey {
		public int volSerialNumber, fileIndexHigh, fileIndexLow;
		
		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (obj == null || !(obj instanceof WindowsFileKey)) return false;
			
			WindowsFileKey o = (WindowsFileKey) obj;
			
			return volSerialNumber == o.volSerialNumber && fileIndexHigh == o.fileIndexHigh && fileIndexLow == o.fileIndexLow;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + fileIndexHigh;
			result = prime * result + fileIndexLow;
			result = prime * result + volSerialNumber;
			return result;
		}
	}

}
