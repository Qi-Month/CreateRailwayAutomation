package dev.celestiacraft.railway_automation.common.block.track_placer;

import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TrackPlacerItem extends BlockItem {
	public TrackPlacerItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		CreateLang.translate("tooltip.holdForDescription", Component.literal("Shift").withStyle(Screen.hasShiftDown() ? ChatFormatting.WHITE : ChatFormatting.GRAY))
				.style(ChatFormatting.DARK_GRAY)
				.addTo(tooltip);

		if (Screen.hasShiftDown()) {
			tooltip.addAll(TooltipHelper.cutStringTextComponent(
					Component.translatable("tooltip.railway_automation.track_placer.condition1").getString(),
					FontHelper.Palette.ALL_GRAY
			));
			tooltip.addAll(TooltipHelper.cutStringTextComponent(
					Component.translatable("tooltip.railway_automation.track_placer.behaviour1").getString(),
					FontHelper.Palette.STANDARD_CREATE.primary(),
					FontHelper.Palette.STANDARD_CREATE.highlight(),
					0
			));

			tooltip.addAll(TooltipHelper.cutStringTextComponent(
					Component.translatable("tooltip.railway_automation.track_placer.condition2").getString(),
					FontHelper.Palette.ALL_GRAY
			));
			tooltip.addAll(TooltipHelper.cutStringTextComponent(
					Component.translatable("tooltip.railway_automation.track_placer.behaviour2").getString(),
					FontHelper.Palette.STANDARD_CREATE.primary(),
					FontHelper.Palette.STANDARD_CREATE.highlight(),
					0
			));
		}
	}
}