package dev.celestiacraft.railway_automation;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;

import dev.celestiacraft.railway_automation.common.block.track_placer.TrackPlacerRenderer;
import dev.celestiacraft.railway_automation.common.event.AddCreativeModeTabs;
import dev.celestiacraft.railway_automation.common.register.RABlock;
import dev.celestiacraft.railway_automation.common.register.RABlockEntity;
import dev.celestiacraft.railway_automation.common.register.RAItem;
import dev.celestiacraft.railway_automation.config.CommonConfig;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.ModContainer;

@Mod(RailwayAutomation.MODID)
public class RailwayAutomation {
	public static final String MODID = "railway_automation";
	public static final String NAME = "Create: Railway Automation";
	public static final Logger LOGGER = LogManager.getLogger(NAME);
	public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID)
			.setTooltipModifierFactory((item) -> {
				return new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
						.andThen(TooltipModifier.mapNull(KineticStats.create(item)));
			})
			.defaultCreativeTab((ResourceKey<CreativeModeTab>) null);

	public static ResourceLocation loadResource(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

	public RailwayAutomation(IEventBus modEventBus, ModContainer modContainer) {
		modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC, "nebula/" + MODID + "/common.toml");

		REGISTRATE.registerEventListeners(modEventBus);

		RABlock.register();
		RABlockEntity.register();
		RAItem.register();

		modEventBus.addListener(AddCreativeModeTabs::buildContents);
		modEventBus.addListener(RailwayAutomation::onClientSetup);
	}

	public static void onClientSetup(FMLClientSetupEvent event) {
		TrackPlacerRenderer.init();

		event.enqueueWork(() -> {
			ItemProperties.register(
					RAItem.MAP_LOCATOR.get(),
					RailwayAutomation.loadResource("filled"),
					(stack, level, entity, seed) -> {
						return stack.has(DataComponents.CUSTOM_DATA) ? 1.0F : 0.0F;
					}
			);
		});
	}
}
