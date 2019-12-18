package silly511.backups;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.Capability.IStorage;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@EventBusSubscriber(modid = BackupsMod.modid)
public class BackupsWorldCapability {
	
	@CapabilityInject(BackupsWorldCapability.class)
	public static final Capability<BackupsWorldCapability> capability = null;
	
	public int nextBackupTimer;

	public static void register() {
		CapabilityManager.INSTANCE.register(BackupsWorldCapability.class, new EmptyStorage(), BackupsWorldCapability::new);
	}
	
	@SubscribeEvent
	public static void attachCapability(AttachCapabilitiesEvent<World> event) {
		event.addCapability(new ResourceLocation("nextBackupTimer"), new ICapabilitySerializable<NBTTagCompound>() {
			BackupsWorldCapability inst = new BackupsWorldCapability();
			
			@Override
			public boolean hasCapability(Capability<?> cap, EnumFacing facing) {
				return cap == capability;
			}
			
			@Override
			public <T> T getCapability(Capability<T> cap, EnumFacing facing) {
				return cap == capability ? (T) inst : null;
			}

			@Override
			public NBTTagCompound serializeNBT() {
				NBTTagCompound nbt = new NBTTagCompound();
				nbt.setInteger("time", inst.nextBackupTimer);
				
				return nbt;
			}

			@Override
			public void deserializeNBT(NBTTagCompound nbt) {
				inst.nextBackupTimer = nbt.getInteger("time");
			}
		});
	}
	
	private static class EmptyStorage implements IStorage<BackupsWorldCapability> {
		@Override
		public NBTBase writeNBT(Capability<BackupsWorldCapability> capability, BackupsWorldCapability instance, EnumFacing side) {
			return null;
		}

		@Override
		public void readNBT(Capability<BackupsWorldCapability> capability, BackupsWorldCapability instance, EnumFacing side, NBTBase nbt) {}
	}

}
