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
		if (Math.abs(hx) > 16 || Math.abs(hz) > 16) {
			return placeTurnWithStraightExtension(level, start, offset, material, paveBlock, paveCount, simulate);
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
		if (consumed >= 0) {
			return consumed;
		}
		return Math.max(0, placeStraightConnector(level, start, offset, material, paveBlock, paveCount, simulate));
	}

	private static int placeTurnWithStraightExtension(
			Level level,
			BlockPos start,
			Vec3i offset,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		int hx = offset.getX();
		int hz = offset.getZ();
		int cx = Integer.signum(hx) * Math.min(Math.abs(hx), 16);
		int cz = Integer.signum(hz) * Math.min(Math.abs(hz), 16);

		int consumed = placeTurn(level, start, new Vec3i(cx, 0, cz), material, paveBlock, paveCount, simulate);
		if (consumed <= 0) {
			return 0;
		}

		int rx = hx - cx;
		int rz = hz - cz;
		BlockState source = material.getBlock().defaultBlockState();

		if (rx != 0) {
			int step = Integer.signum(rx);
			for (int i = 1; i <= Math.abs(rx); i++) {
				BlockPos target = start.offset(cx + step * i, 0, cz);
				int block = TrackPlacementUtils.placeTrackBlock(
						level,
						target,
						Direction.Axis.X,
						source,
						material,
						TrackShape.XO,
						paveBlock,
						paveCount,
						simulate
				);
				if (block < 0) {
					return 0;
				}
				consumed += block;
			}
		}

		if (rz != 0) {
			BlockPos corner = start.offset(hx, 0, cz);
			int block = TrackPlacementUtils.placeTrackBlock(
					level,
					corner,
					Direction.Axis.Z,
					source,
					material,
					TrackShape.ZO,
					paveBlock,
					paveCount,
					simulate
			);
			if (block < 0) {
				return 0;
			}
			consumed += block;

			int step = Integer.signum(rz);
			for (int i = 1; i <= Math.abs(rz); i++) {
				BlockPos target = start.offset(hx, 0, cz + step * i);
				block = TrackPlacementUtils.placeTrackBlock(
						level,
						target,
						Direction.Axis.Z,
						source,
						material,
						TrackShape.ZO,
						paveBlock,
						paveCount,
						simulate
				);
				if (block < 0) {
					return 0;
				}
				consumed += block;
			}
		}

		return consumed;
	}

	private static int placeStraightConnector(
			Level level,
			BlockPos start,
			Vec3i offset,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		int hx = offset.getX();
		int hz = offset.getZ();
		if (hx != 0 && hz != 0) {
			return -1;
		}
		int length = Math.max(Math.abs(hx), Math.abs(hz));
		if (length == 0) {
			return -1;
		}
		int stepX = Integer.signum(hx);
		int stepZ = Integer.signum(hz);
		Direction.Axis axis = stepX != 0 ? Direction.Axis.X : Direction.Axis.Z;
		TrackShape shape = hx != 0 ? TrackShape.XO : TrackShape.ZO;
		BlockState source = material.getBlock().defaultBlockState();
		int consumed = 0;

		for (int i = 0; i <= length; i++) {
			BlockPos target = start.offset(stepX * i, 0, stepZ * i);
			int count = TrackPlacementUtils.placeTrackBlock(
					level,
					target,
					axis,
					source,
					material,
					shape,
					paveBlock,
					paveCount,
					simulate
			);
			if (count < 0) {
				return -1;
			}
			consumed += count;
		}
		return consumed;
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
		if (hx != 0 && hz != 0) {
			return TrackShape.ZO;
		}
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
