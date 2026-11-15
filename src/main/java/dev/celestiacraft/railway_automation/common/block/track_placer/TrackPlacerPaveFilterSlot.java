package dev.celestiacraft.railway_automation.common.block.track_placer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 方块顶面的数值框位置：x = 5 处是转弯半径滑条，x = 11 处是地基过滤槽
 */
public class TrackPlacerPaveFilterSlot extends ValueBoxTransform.Sided {
	private final float xOffset;

	public TrackPlacerPaveFilterSlot(float xOffset) {
		this.xOffset = xOffset;
	}

	@Override
	protected boolean isSideActive(BlockState state, Direction direction) {
		return direction.equals(Direction.UP);
	}

	@Override
	public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
		return VecHelper.rotateCentered(
				VecHelper.voxelSpace(xOffset, 16, 11),
				angleY(state),
				Direction.Axis.Y
		);
	}

	@Override
	public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
		TransformStack.of(ms)
				.rotateYDegrees(angleY(state) + 180)
				.rotateXDegrees(90);
	}

	@Override
	protected Vec3 getSouthLocation() {
		return VecHelper.voxelSpace(xOffset, 16, 14);
	}

	protected float angleY(BlockState state) {
		return AngleHelper.horizontalAngle(state.getValue(TrackPlacerBlock.FACING).getOpposite());
	}
}