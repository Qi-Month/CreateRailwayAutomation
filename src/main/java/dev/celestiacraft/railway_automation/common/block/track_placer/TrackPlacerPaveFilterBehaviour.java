package dev.celestiacraft.railway_automation.common.block.track_placer;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.nbt.CompoundTag;

/**
 * Track filter slot (which track item to place).
 */
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
	public void write(CompoundTag nbt, boolean clientPacket) {
		nbt.put("Track", getFilter().serializeNBT());
		nbt.putInt("TrackAmount", count);
		nbt.putBoolean("TrackUpTo", upTo);
	}

	@Override
	public void read(CompoundTag nbt, boolean clientPacket) {
		if (!nbt.contains("Track")) {
			return;
		}

		filter = FilterItemStack.of(nbt.getCompound("Track"));
		count = nbt.getInt("TrackAmount");
		upTo = nbt.getBoolean("TrackUpTo");

		if (count == 0) {
			upTo = true;
			count = filter.item().getMaxStackSize();
		}
	}
}