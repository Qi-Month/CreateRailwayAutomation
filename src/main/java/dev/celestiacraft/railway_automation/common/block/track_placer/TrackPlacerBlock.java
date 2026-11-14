package dev.celestiacraft.railway_automation.common.block.track_placer;

import com.simibubi.create.foundation.block.IBE;
import dev.celestiacraft.railway_automation.common.block.state.properties.track_placer.PlacerProperties;
import dev.celestiacraft.railway_automation.common.block.state.properties.track_placer.WorkingStatus;
import dev.celestiacraft.railway_automation.common.register.RABlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

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
	public Class<TrackPlacerBlockEntity> getBlockEntityClass() {
		return TrackPlacerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends TrackPlacerBlockEntity> getBlockEntityType() {
		return RABlockEntity.TRACK_PLACER.get();
	}
}