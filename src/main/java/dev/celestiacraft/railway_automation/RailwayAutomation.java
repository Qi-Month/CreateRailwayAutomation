package dev.celestiacraft.railway_automation;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.celestiacraft.railway_automation.common.register.RABlock;
import dev.celestiacraft.railway_automation.common.register.RABlockEntity;
import dev.celestiacraft.railway_automation.common.register.RAItem;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(RailwayAutomation.MODID)
@Mod.EventBusSubscriber(modid = RailwayAutomation.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RailwayAutomation {
	public static final String MODID = "railway_automation";
	public static final String NAME = "Create: Railway Automation";
	public static final Logger LOGGER = LogManager.getLogger(NAME);
	public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID)
			.setTooltipModifierFactory((item) -> {
				return new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
						.andThen(TooltipModifier.mapNull(KineticStats.create(item)));
			});

	public static ResourceLocation loadResource(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

	public RailwayAutomation(FMLJavaModLoadingContext context) {
		IEventBus bus = context.getModEventBus();

		REGISTRATE.registerEventListeners(bus);

		RABlock.register();
		RABlockEntity.register();
		RAItem.register();
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			ItemProperties.register(
					RAItem.MAP_LOCATOR.get(),
					RailwayAutomation.loadResource("filled"),
					(stack, level, entity, seed) -> {
						return stack.hasTag() ? 1.0F : 0.0F;
					}
			);
		});
	}
}