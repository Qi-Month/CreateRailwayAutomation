package dev.celestiacraft.railway_automation.common.block.track_placer;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public class TrackPlacerPaveFilterBehaviour extends FilteringBehaviour {
	public static final BehaviourType<TrackPlacerPaveFilterBehaviour> TYPE = new BehaviourType<>();

	public TrackPlacerPaveFilterBehaviour(SmartBlockEntity be, ValueBoxTransform slot) {
		super(be, slot);
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

	@Override
	public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
		nbt.put("PaveBlock", getFilter().saveOptional(registries));
		nbt.putInt("PaveBlockAmount", count);
		nbt.putBoolean("PaveBlockUpTo", upTo);
	}

	@Override
	public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
		if (!nbt.contains("PaveBlock")) {
			return;
		}
		filter = FilterItemStack.of(registries, nbt.getCompound("PaveBlock"));
		count = nbt.getInt("PaveBlockAmount");
		upTo = nbt.getBoolean("PaveBlockUpTo");

		if (count == 0) {
			upTo = true;
			count = filter.item().getMaxStackSize();
		}
	}
}
