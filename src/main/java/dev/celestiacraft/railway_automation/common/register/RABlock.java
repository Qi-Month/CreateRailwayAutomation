package dev.celestiacraft.railway_automation.common.register;

import com.simibubi.create.AllTags;
import com.simibubi.create.foundation.data.TagGen;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import dev.celestiacraft.railway_automation.RailwayAutomation;
import dev.celestiacraft.railway_automation.common.block.track_placer.TrackPlacerBlock;
import dev.celestiacraft.railway_automation.common.block.track_placer.TrackPlacerItem;
import net.minecraft.tags.BlockTags;

public class RABlock {
	public static final BlockEntry<TrackPlacerBlock> TRACK_PLACER;

	static {
		TRACK_PLACER = RailwayAutomation.REGISTRATE.block("track_placer", TrackPlacerBlock::new)
				.transform(TagGen.pickaxeOnly())
				.tag(BlockTags.MINEABLE_WITH_PICKAXE)
				.tag(BlockTags.NEEDS_STONE_TOOL)
				.tag(AllTags.AllBlockTags.WRENCH_PICKUP.tag)
				.item(TrackPlacerItem::new)
				.model(NonNullBiConsumer.noop())
				.build()
				.blockstate(NonNullBiConsumer.noop())
				.register();
	}

	public static void register() {
		RailwayAutomation.LOGGER.info("{} Blocks is Registered!", RailwayAutomation.NAME);
	}
}