package dev.celestiacraft.railway_automation.common.block.state.properties.track_placer;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum WorkingStatus implements StringRepresentable {
	IDLE("idle"),
	ACTIVATE("activate"),
	ERROR("error"),
	EMPTY("empty");

	private final String name;

	WorkingStatus(String name) {
		this.name = name;
	}

	public @NotNull String getSerializedName() {
		return name;
	}
}