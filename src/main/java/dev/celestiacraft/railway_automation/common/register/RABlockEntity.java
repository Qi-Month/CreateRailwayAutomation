package dev.celestiacraft.railway_automation.common.register;

import com.tterrag.registrate.util.entry.BlockEntityEntry;
import dev.celestiacraft.railway_automation.RailwayAutomation;
import dev.celestiacraft.railway_automation.common.block.track_placer.TrackPlacerBlockEntity;
import dev.celestiacraft.railway_automation.common.block.track_placer.TrackPlacerRenderer;

public class RABlockEntity {
	public static final BlockEntityEntry<TrackPlacerBlockEntity> TRACK_PLACER;

	static {
		TRACK_PLACER = RailwayAutomation.REGISTRATE.blockEntity("track_placer", TrackPlacerBlockEntity::new)
				.validBlock(RABlock.TRACK_PLACER)
				.renderer(() -> TrackPlacerRenderer::new)
				.register();
	}

	public static void register() {
		RailwayAutomation.LOGGER.info("{} BlockEntities is Registered!", RailwayAutomation.NAME);
	}
}