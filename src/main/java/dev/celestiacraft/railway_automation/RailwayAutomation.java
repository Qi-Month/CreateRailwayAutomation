package dev.celestiacraft.railway_automation;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(RailwayAutomation.MODID)
public class RailwayAutomation {
	public static final String MODID = "railway_automation";

	private static final Logger LOGGER = LogUtils.getLogger();

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

	public RailwayAutomation(IEventBus modEventBus) {
		BLOCKS.register(modEventBus);
		ITEMS.register(modEventBus);
	}
}
