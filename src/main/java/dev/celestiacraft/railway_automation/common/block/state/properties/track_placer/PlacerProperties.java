package dev.celestiacraft.railway_automation.common.block.state.properties.track_placer;

import net.minecraft.world.level.block.state.properties.EnumProperty;

public class PlacerProperties {
	public static final EnumProperty<WorkingStatus> WORKING_STATUS;

	static {
		WORKING_STATUS = EnumProperty.create("working_status", WorkingStatus.class);
	}
}