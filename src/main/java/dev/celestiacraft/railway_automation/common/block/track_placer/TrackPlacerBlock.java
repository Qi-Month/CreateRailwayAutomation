package dev.celestiacraft.railway_automation.common.block.track_placer;

import com.simibubi.create.foundation.block.IBE;

import dev.celestiacraft.railway_automation.common.block.state.properties.track_placer.PlacerProperties;
import dev.celestiacraft.railway_automation.common.block.state.properties.track_placer.WorkingStatus;
import dev.celestiacraft.railway_automation.common.register.RABlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public class TrackPlacerBlock extends Block implements IBE<TrackPlacerBlockEntity> {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<WorkingStatus> WORKING_STATUS = PlacerProperties.WORKING_STATUS;

	public TrackPlacerBlock(Properties properties) {
		super(properties.strength(3.0f, 3.0f)
				.sound(SoundType.WOOD)
				.requiresCorrectToolForDrops());
		registerDefaultState(defaultBlockState()
				.setValue(FACING, Direction.NORTH)
				.setValue(WORKING_STATUS, WorkingStatus.EMPTY)
		);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
		builder.add(WORKING_STATUS);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof TrackPlacerBlockEntity placer)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (player.isShiftKeyDown() && stack.isEmpty()) {
			if (!level.isClientSide()) {
				placer.setPlacedTrack(ItemStack.EMPTY);
				level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide());
		}
		if (TrackPlacerBlockEntity.isValidTrackFilter(stack)) {
			if (!level.isClientSide()) {
				placer.setPlacedTrack(stack);
				level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide());
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	public Class<TrackPlacerBlockEntity> getBlockEntityClass() {
		return TrackPlacerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends TrackPlacerBlockEntity> getBlockEntityType() {
		return RABlockEntity.TRACK_PLACER.get();
	}
}
