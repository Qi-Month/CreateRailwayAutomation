package dev.celestiacraft.railway_automation.common.event;

import com.simibubi.create.AllCreativeModeTabs;

import dev.celestiacraft.railway_automation.common.register.RABlock;
import dev.celestiacraft.railway_automation.common.register.RAItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

public final class AddCreativeModeTabs {
	private AddCreativeModeTabs() {
	}

	public static void buildContents(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey().equals(AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey())) {
			event.accept(RABlock.TRACK_PLACER.get().asItem());
			event.accept(RAItem.MAP_LOCATOR.get());
		}
	}
}
