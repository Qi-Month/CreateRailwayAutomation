package dev.celestiacraft.railway_automation.common.register;

import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import dev.celestiacraft.railway_automation.RailwayAutomation;
import dev.celestiacraft.railway_automation.common.item.map_locator.MapLocatorItem;

public class RAItem {
	public static final ItemEntry<MapLocatorItem> MAP_LOCATOR;

	static {
		MAP_LOCATOR = RailwayAutomation.REGISTRATE.item("map_locator", MapLocatorItem::new)
				.model(NonNullBiConsumer.noop())
				.register();
	}

	public static void register() {
		RailwayAutomation.LOGGER.info("{} Items is Registered!", RailwayAutomation.NAME);
	}
}