package dev.celestiacraft.railway_automation.common.event.placer;

import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackShape;
import dev.celestiacraft.railway_automation.utils.TrackPlacementUtils;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TrackTurnPlacer {
	public static Builder builder(Level level, BlockPos start, Vec3i offset) {
		return new Builder(level, start, offset);
	}

	public static class Builder extends TrackPlacementUtils.PlacementBuilder {
		private Builder(Level level, BlockPos start, Vec3i offset) {
			super(level, start, offset, TrackTurnPlacer::placeTurn);
		}
	}

	private static int placeTurn(
			Level level,
			BlockPos start,
			Vec3i offset,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		if (level == null || start == null || offset == null || material == null) {
			return 0;
		}
		int hx = offset.getX();
		int hz = offset.getZ();

		// 转弯段只处理真正的转弯；横向或主方向偏移为 0 时属于共线铺设，交由直线段负责
		// 转弯跨度已由铺设处理器按滑条设置换算好，这里只需要校验它能形成合法曲线
		int span = Math.min(Math.abs(hx), Math.abs(hz));
		if (span < TrackPlacementUtils.MIN_TURN_SPAN) {
			// 跨度不足以下限，无法形成合法的 90 度曲线，该位置信息视为不可用
			return 0;
		}

		// 弯道只负责曲线本身：转弯前后的直线段由直线铺设器承担，偏移与转弯跨度不一致时视为不可用
		if (Math.abs(hx) != span || Math.abs(hz) != span) {
			return 0;
		}
		BlockPos end = start.offset(offset);
		BlockState state1 = level.getBlockState(start);
		BlockState state2 = level.getBlockState(end);

		if (!isAxialTrack(state1)) {
			state1 = axialState(material, axialShapeForDirection(offset.getX(), offset.getZ()));
		}
		if (!isAxialTrack(state2)) {
			TrackShape endShape = state1.getValue(TrackBlock.SHAPE) == TrackShape.XO ? TrackShape.ZO : TrackShape.XO;
			state2 = axialState(material, endShape);
		}

		int consumed = connectTurnTracks(level, start, end, state1, state2, material, paveBlock, paveCount, simulate);
		return Math.max(0, consumed);
	}

	private static boolean isAxialTrack(BlockState state) {
		return state.getBlock() instanceof ITrackBlock
				&& state.hasProperty(TrackBlock.SHAPE)
				&& (state.getValue(TrackBlock.SHAPE) == TrackShape.XO
				|| state.getValue(TrackBlock.SHAPE) == TrackShape.ZO);
	}

	private static BlockState axialState(TrackMaterial material, TrackShape shape) {
		return material.getBlock().defaultBlockState()
				.setValue(TrackBlock.SHAPE, shape)
				.setValue(TrackBlock.HAS_BE, false);
	}

	private static TrackShape axialShapeForDirection(int hx, int hz) {
		// 转弯起点沿朝向方向延伸：从 X 方向来为 XO，从 Z 方向来为 ZO
		return hx != 0 ? TrackShape.XO : TrackShape.ZO;
	}

	private static int connectTurnTracks(
			Level level,
			BlockPos start,
			BlockPos end,
			BlockState state1,
			BlockState state2,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		TrackPlacementUtils.TrackConnection c = TrackPlacementUtils.prepareConnection(level, start, end, state1, state2);
		if (c == null || c.parallel() || !c.normal1().equals(c.normal2())) {
			return -1;
		}

		double a1 = Mth.atan2(c.normedAxis2().z(), c.normedAxis2().x());
		double a2 = Mth.atan2(c.normedAxis1().z(), c.normedAxis1().x());
		double angle = a1 - a2;
		double ascend = c.end2().subtract(c.end1()).y();
		double absAscend = Math.abs(ascend);

		float absAngle = Math.abs(AngleHelper.deg(angle));
		if (absAngle < 60 || absAngle > 300) {
			return -1;
		}

		double[] intersect = TrackPlacementUtils.intersect(c.end1(), c.end2(), c.normedAxis1(), c.normedAxis2(), Direction.Axis.Y);
		double dist1 = Math.abs(intersect[0]);
		double dist2 = Math.abs(intersect[1]);
		float ex1 = 0;
		float ex2 = 0;

		if (dist1 > dist2) {
			ex1 = (float) ((dist1 - dist2) / c.axis1().length());
		}
		if (dist2 > dist1) {
			ex2 = (float) ((dist2 - dist1) / c.axis2().length());
		}

		double turnSize = Math.min(dist1, dist2) - 0.1d;
		boolean ninety = (absAngle + 0.25f) % 90 < 1;

		if (intersect[0] < 0 || intersect[1] < 0) {
			return -1;
		}

		double minTurnSize = ninety ? 7 : 3.25;
		double turnSizeToFitAscend = minTurnSize
				+ (ninety ? Math.max(0, absAscend - 3) * 2.0f
				: Math.max(0, absAscend - 1.5f) * 1.5f);

		if (turnSize < minTurnSize) {
			return -1;
		}
		if (turnSize < turnSizeToFitAscend) {
			return -1;
		}
		int end1Extent = Mth.floor(ex1);
		int end2Extent = Mth.floor(ex2);

		boolean girder = TrackPlacementUtils.isMetalGirder(paveBlock);
		return TrackPlacementUtils.finishConnection(
				level, start, end, state1, state2, material, c,
				end1Extent, end2Extent, false, girder, paveBlock, paveCount, simulate
		);
	}
}