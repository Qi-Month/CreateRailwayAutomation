package dev.celestiacraft.railway_automation.common.item.map_locator;

import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.celestiacraft.railway_automation.common.block.track_placer.TrackPlacerBlock;
import dev.celestiacraft.railway_automation.common.block.track_placer.TrackPlacerBlockEntity;
import dev.celestiacraft.railway_automation.common.event.TrackPlacingHandler;
import dev.celestiacraft.railway_automation.common.register.RABlock;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MapLocatorItem extends Item {
	public MapLocatorItem(Properties properties) {
		super(properties);
	}

	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (level.isClientSide()) {
			return InteractionResultHolder.pass(stack);
		}

		if (player.isCrouching() && stack.hasTag()) {
			stack.setTag(null);
			player.swing(hand, true);
			player.sendSystemMessage(Component.translatable(
					"promp.railway_automation.map_locator.location_cleared"
			));

			return InteractionResultHolder.success(stack);
		}

		return InteractionResultHolder.pass(stack);
	}

	@Override
	public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
		Level level = context.getLevel();

		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}

		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}

		if (context.getHand() != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		if (player.isCrouching()) {
			return InteractionResult.PASS;
		}

		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		ItemStack locator = context.getItemInHand();
		ResourceLocation dimension = level.dimension().location();

		boolean hasTag = locator.hasTag();

		if (hasTag && state.is(RABlock.TRACK_PLACER.get())) {
			CompoundTag tag = locator.getTag();

			int destinationX = tag.getInt("x");
			int destinationY = tag.getInt("y");
			int destinationZ = tag.getInt("z");
			String destinationDim = tag.getString("dim");

			if (dimension.toString().equals(destinationDim)) {
				// 铺设器只能沿自身朝向铺设轨道，目标点不在正前方时拒绝写入，坐标仍保留在定位器上
				Direction facing = state.getValue(TrackPlacerBlock.FACING);
				Vec3i offset = new Vec3i(
						destinationX - pos.getX(),
						destinationY - pos.getY(),
						destinationZ - pos.getZ()
				);

				if (!TrackPlacingHandler.isFacingTarget(offset, facing)) {
					player.sendSystemMessage(Component.translatable(
							"promp.railway_automation.map_locator.unreachable"
					));

					return InteractionResult.PASS;
				}

				if (level.getBlockEntity(pos) instanceof TrackPlacerBlockEntity trackPlacer) {
					trackPlacer.setTarget(
							destinationX,
							destinationY,
							destinationZ,
							destinationDim
					);
				}

				locator.setTag(null);

				player.sendSystemMessage(Component.translatable(
						"promp.railway_automation.map_locator.location_written"
				));

				return InteractionResult.SUCCESS;
			}

			player.sendSystemMessage(Component.translatable(
					"promp.railway_automation.map_locator.different_dimension"
			));

			return InteractionResult.PASS;
		}

		if (!hasTag) {
			CompoundTag tag = new CompoundTag();
			tag.putInt("x", pos.getX());
			tag.putInt("y", pos.getY());
			tag.putInt("z", pos.getZ());
			tag.putString("dim", dimension.toString());

			locator.setTag(tag);

			player.swing(InteractionHand.MAIN_HAND, true);

			player.sendSystemMessage(Component.translatable(
					"promp.railway_automation.map_locator.location_stored"
			));

			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		CreateLang.translate("tooltip.holdForDescription", Component.literal("Shift").withStyle(Screen.hasShiftDown() ? ChatFormatting.WHITE : ChatFormatting.GRAY))
				.style(ChatFormatting.DARK_GRAY)
				.addTo(tooltip);

		if (Screen.hasShiftDown()) {
			tooltip.addAll(TooltipHelper.cutStringTextComponent(
					Component.translatable("tooltip.railway_automation.map_locator.condition1").getString(),
					FontHelper.Palette.ALL_GRAY
			));
			tooltip.addAll(TooltipHelper.cutStringTextComponent(
					Component.translatable("tooltip.railway_automation.map_locator.behaviour1").getString(),
					FontHelper.Palette.STANDARD_CREATE.primary(),
					FontHelper.Palette.STANDARD_CREATE.highlight(),
					0
			));

			tooltip.addAll(TooltipHelper.cutStringTextComponent(
					Component.translatable("tooltip.railway_automation.map_locator.condition2").getString(),
					FontHelper.Palette.ALL_GRAY
			));
			tooltip.addAll(TooltipHelper.cutStringTextComponent(
					Component.translatable("tooltip.railway_automation.map_locator.behaviour2").getString(),
					FontHelper.Palette.STANDARD_CREATE.primary(),
					FontHelper.Palette.STANDARD_CREATE.highlight(),
					0
			));
		}
	}
}