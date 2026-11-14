package dev.celestiacraft.railway_automation.common.event.placer;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackShape;
import dev.celestiacraft.railway_automation.utils.TrackPlacementUtils;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class TrackStraightPlacer {
	// 缓坡允许的最陡坡度比：水平距离至少为高差的该倍数，否则视为位置不可用
	private static final int GENTLE_LENGTH_FACTOR = 3;

	public static Builder builder(Level level, BlockPos start, Vec3i offset) {
		return new Builder(level, start, offset);
	}

	public static class Builder extends TrackPlacementUtils.PlacementBuilder {
		private Builder(Level level, BlockPos start, Vec3i offset) {
			super(level, start, offset, TrackStraightPlacer::placeStraight);
		}
	}

	private static int placeStraight(
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
		if (offset.getY() == 0) {
			return placeStraightLine(level, start, offset, material, paveBlock, paveCount, simulate);
		}
		int rise = Math.abs(offset.getY());
		if (rise > TrackPlacementUtils.maxVerticalRise()) {
			// 高度差超出配置上限，视为位置不可用
			return 0;
		}

		BlockPos end = start.offset(offset);
		BlockState state1 = level.getBlockState(start);
		BlockState state2 = level.getBlockState(end);
		boolean trackStart = state1.getBlock() instanceof ITrackBlock
				&& state1.hasProperty(TrackBlock.SHAPE);
		boolean trackEnd = state2.getBlock() instanceof ITrackBlock
				&& state2.hasProperty(TrackBlock.SHAPE);

		if (trackStart && trackEnd) {
			return Math.max(0, connectStraightTracks(
					level,
					start,
					end,
					state1,
					state2,
					material,
					false,
					paveBlock,
					paveCount, simulate
			));
		}

		int hx = offset.getX();
		int hz = offset.getZ();
		int length = Math.max(Math.abs(hx), Math.abs(hz));
		// 曲线只沿单一水平轴展开，斜向目标点仍交给原有分支处理
		boolean singleAxis = hx == 0 || hz == 0;
		int insertRise = TrackPlacementUtils.slopeInsertRise();

		// 高差未超过阈值时坡度比不得陡于 1:3，否则视为位置不可用
		if (singleAxis && rise <= insertRise && length < GENTLE_LENGTH_FACTOR * rise) {
			return 0;
		}

		if (length == 0) {
			return 0;
		}

		TrackShape shape = hx != 0 ? TrackShape.XO : TrackShape.ZO;
		BlockState flat = material.getBlock().defaultBlockState()
				.setValue(TrackBlock.SHAPE, shape)
				.setValue(TrackBlock.HAS_BE, false);

		// 缓坡优先：高差不超过阈值时，整段改用单条平缓曲线爬升，不再插入 45 度坡道与额外的过渡弯曲段。
		// 此时坡度比不得陡于 1:3，水平距离不足即视为位置不可用；满足时曲线尽量取到坡度比上限所需的长度
		int gentleLength = TrackPlacementUtils.gentleSlopeLength(rise);
		if (singleAxis && rise <= insertRise) {
			// 水平距离充足时用完整缓坡；不足时把全部水平距离交给曲线，尽可能压低坡度
			int curveH = Math.min(length, gentleLength);
			return Math.max(0, placeFittedStraight(
					level,
					start,
					offset,
					material,
					flat,
					paveBlock,
					paveCount,
					simulate,
					curveH
			));
		}

		// 混合曲线的两端弯曲段由配置决定：水平长度为配置值，垂直高差恒为其一半
		int curveLength = TrackPlacementUtils.slopeCurveLength();
		int curveRise = TrackPlacementUtils.slopeCurveRise();
		int slopeRise = rise - 2 * curveRise;
		int hybridMinLength = 2 * (curveLength + 1) + Math.max(0, slopeRise - 1);
		boolean hybridPossible = slopeRise >= 1 && length >= hybridMinLength;

		// 高差超过阈值时必须插入 45 度坡道：两端弯曲段与坡道放不下时视为位置不可用
		if (rise > insertRise) {
			if (!hybridPossible) {
				return 0;
			}
			return Math.max(0, placeHybridStraightWithCurves(
					level,
					start,
					offset,
					material,
					paveBlock,
					paveCount,
					simulate
			));
		}

		if (length < GENTLE_LENGTH_FACTOR * rise) {
			if (!hybridPossible) {
				// 弯曲段无法容纳时视为位置不可用，避免生成错位的曲线端点
				return 0;
			}
			return Math.max(0, placeHybridStraightWithCurves(
					level,
					start,
					offset,
					material,
					paveBlock,
					paveCount,
					simulate
			));
		}
		return Math.max(0, placeFittedStraight(level, start, offset, material, flat, paveBlock, paveCount, simulate, GENTLE_LENGTH_FACTOR * rise));
	}

	private static int placeStraightLine(
			Level level,
			BlockPos start,
			Vec3i offset,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		Direction.Axis axis = TrackPlacementUtils.getSingleAxis(offset);
		if (axis == null || axis == Direction.Axis.Y) {
			return 0;
		}

		BlockState sourceState = level.getBlockState(start);
		if (!(sourceState.getBlock() instanceof ITrackBlock)) {
			sourceState = material.getBlock().defaultBlockState();
		}

		TrackShape shape = axis == Direction.Axis.X ? TrackShape.XO : TrackShape.ZO;
		int stepX = Integer.signum(offset.getX());
		int stepY = Integer.signum(offset.getY());
		int stepZ = Integer.signum(offset.getZ());
		int length = Math.max(Math.abs(offset.getX()), Math.max(Math.abs(offset.getY()), Math.abs(offset.getZ())));

		int firstIndex = canGrowNewTrack(level, start) ? 0 : 1;
		int consumed = 0;
		for (int i = firstIndex; i <= length; i++) {
			BlockPos target = start.offset(stepX * i, stepY * i, stepZ * i);
			int block = TrackPlacementUtils.placeTrackBlock(level, target, axis, sourceState, material, shape, paveBlock, paveCount, simulate);
			if (block < 0) {
				return 0;
			}
			consumed += block;
		}

		return consumed;
	}

	private static boolean canGrowNewTrack(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.canBeReplaced() || state.is(BlockTags.FLOWERS);
	}

	private static int placeHybridStraightWithCurves(
			Level level,
			BlockPos start,
			Vec3i offset,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		int hx = offset.getX();
		int hy = offset.getY();
		int hz = offset.getZ();
		int length = Math.max(Math.abs(hx), Math.abs(hz));
		int rise = Math.abs(hy);
		int curveH = TrackPlacementUtils.slopeCurveLength();
		int curveV = TrackPlacementUtils.slopeCurveRise();
		int slopeRise = rise - 2 * curveV;
		if (slopeRise < 1) {
			// 目标高差不足以容纳两段弯曲段与中间的 45 度段
			return -1;
		}
		int flatTotal = length - 2 * (curveH + 1) - (slopeRise - 1);
		if (flatTotal < 0) {
			// 水平长度不足以容纳两端弯曲段
			return -1;
		}
		int bottomFlat = flatTotal / 2;
		boolean startOk = canGrowNewTrack(level, start);
		if (!startOk && flatTotal == 1) {
			bottomFlat = 1;
		}
		int topFlat = flatTotal - bottomFlat;

		Direction.Axis axis = hx != 0 ? Direction.Axis.X : Direction.Axis.Z;
		int stepX = Integer.signum(hx);
		int stepY = Integer.signum(hy);
		int stepZ = Integer.signum(hz);
		boolean ascending = stepY > 0;
		boolean girder = TrackPlacementUtils.isMetalGirder(paveBlock);

		TrackShape flatShape = axis == Direction.Axis.X
				? TrackShape.XO
				: TrackShape.ZO;

		TrackShape slopeShape = hx != 0
				? (stepX > 0 ? (ascending ? TrackShape.AE : TrackShape.AW)
				: (ascending ? TrackShape.AW : TrackShape.AE))
				: (stepZ > 0 ? (ascending ? TrackShape.AS : TrackShape.AN)
				: (ascending ? TrackShape.AN : TrackShape.AS));

		BlockState flat = material.getBlock().defaultBlockState()
				.setValue(TrackBlock.SHAPE, flatShape)
				.setValue(TrackBlock.HAS_BE, false);

		BlockState slopeState = material.getBlock().defaultBlockState()
				.setValue(TrackBlock.SHAPE, slopeShape)
				.setValue(TrackBlock.HAS_BE, false);

		Vec3 flatDir = new Vec3(stepX, 0, stepZ);
		Vec3 slopeDir = new Vec3(stepX, stepY, stepZ).normalize();
		Vec3 flatNormal = new Vec3(0, 1, 0);
		Vec3 slopeNormal = slopeShape.getNormal().normalize();

		int firstFlat = startOk ? 0 : 1;
		int consumed = 0;

		BlockPos p1 = start.offset(stepX * bottomFlat, 0, stepZ * bottomFlat);
		for (int i = firstFlat; i < bottomFlat; i++) {
			BlockPos target = start.offset(stepX * i, 0, stepZ * i);
			int block = TrackPlacementUtils.placeTrackBlock(
					level,
					target,
					axis,
					flat,
					material,
					flatShape,
					paveBlock,
					paveCount,
					simulate
			);
			if (block < 0) {
				return -1;
			}
			consumed += block;
		}

		Vec3 a = new Vec3(p1.getX() + 0.5, p1.getY(), p1.getZ() + 0.5).add(flatDir.scale(0.5));
		Vec3 b = a.add(new Vec3(stepX * curveH, stepY * curveV, stepZ * curveH));
		BlockPos ps = TrackPlacementUtils.slopeBlockAtEnd(b, stepX, stepZ, ascending, axis);

		BlockPos pl = ps.offset(stepX * (slopeRise - 1), stepY * (slopeRise - 1), stepZ * (slopeRise - 1));
		for (int i = 1; i < slopeRise - 1; i++) {
			BlockPos target = ps.offset(stepX * i, stepY * i, stepZ * i);
			int r = TrackPlacementUtils.placeTrackBlock(
					level,
					target,
					axis,
					slopeState,
					material,
					slopeShape,
					paveBlock,
					paveCount,
					simulate
			);
			if (r < 0) {
				return -1;
			}
			consumed += r;
		}

		Vec3 c = TrackPlacementUtils.slopeTipAtEnd(pl, stepX, stepZ, ascending, axis);
		Vec3 d = c.add(new Vec3(stepX * curveH, stepY * curveV, stepZ * curveH));
		BlockPos pt = TrackPlacementUtils.flatBlockAtEnd(d, stepX, stepZ, axis);

		for (int i = 1; i <= topFlat; i++) {
			BlockPos target = pt.offset(stepX * i, 0, stepZ * i);
			int block = TrackPlacementUtils.placeTrackBlock(
					level,
					target,
					axis,
					flat,
					material,
					flatShape,
					paveBlock,
					paveCount,
					simulate
			);
			if (block < 0) {
				return -1;
			}
			consumed += block;
		}

		BezierConnection curve1 = new BezierConnection(
				Couple.create(p1, ps),
				Couple.create(a, b),
				Couple.create(flatDir, slopeDir.scale(-1)),
				Couple.create(flatNormal, slopeNormal),
				true,
				girder,
				material
		);
		int curve = placeCurve(
				level,
				p1,
				ps,
				flat,
				slopeState,
				flatDir,
				slopeDir,
				curve1,
				material,
				paveBlock,
				paveCount,
				simulate
		);
		if (curve < 0) {
			return -1;
		}
		consumed += curve;

		BezierConnection curve2 = new BezierConnection(
				Couple.create(pl, pt),
				Couple.create(c, d),
				Couple.create(slopeDir, flatDir.scale(-1)),
				Couple.create(slopeNormal, flatNormal),
				true,
				girder,
				material
		);
		curve = placeCurve(
				level,
				pl,
				pt,
				slopeState,
				flat,
				slopeDir,
				flatDir,
				curve2,
				material,
				paveBlock,
				paveCount,
				simulate
		);
		if (curve < 0) {
			return -1;
		}
		consumed += curve;

		return consumed;
	}

	/**
	 * 以「平地 - 曲线 - 平地」的形式爬升，曲线段的水平长度由调用方给定
	 *
	 * @param curveH 曲线段的水平长度，必须不大于总水平长度
	 */
	private static int placeFittedStraight(
			Level level,
			BlockPos start,
			Vec3i offset,
			TrackMaterial material,
			BlockState flat,
			Block paveBlock,
			int[] paveCount,
			boolean simulate,
			int curveH
	) {
		int hx = offset.getX();
		int hy = offset.getY();
		int hz = offset.getZ();
		int length = Math.max(Math.abs(hx), Math.abs(hz));
		int rise = Math.abs(hy);
		if (curveH < 1 || curveH > length) {
			return -1;
		}
		int flatTotal = length - curveH;
		int bottomFlat = flatTotal / 2;
		boolean startOk = canGrowNewTrack(level, start);

		if (!startOk && flatTotal == 1) {
			bottomFlat = 1;
		}
		int topFlat = flatTotal - bottomFlat;

		Direction.Axis axis = hx != 0 ? Direction.Axis.X : Direction.Axis.Z;
		int stepX = Integer.signum(hx);
		int stepY = Integer.signum(hy);
		int stepZ = Integer.signum(hz);

		TrackShape flatShape = axis == Direction.Axis.X
				? TrackShape.XO
				: TrackShape.ZO;

		int firstFlat = startOk ? 0 : 1;
		BlockPos p1 = start.offset(stepX * bottomFlat, 0, stepZ * bottomFlat);
		int consumed = 0;

		for (int i = firstFlat; i < bottomFlat; i++) {
			BlockPos target = start.offset(stepX * i, 0, stepZ * i);
			int block = TrackPlacementUtils.placeTrackBlock(
					level,
					target,
					axis,
					flat,
					material,
					flatShape,
					paveBlock,
					paveCount,
					simulate
			);
			if (block < 0) {
				return -1;
			}
			consumed += block;
		}

		BlockPos p2 = start.offset(stepX * (bottomFlat + curveH), stepY * rise, stepZ * (bottomFlat + curveH));
		int tracks = connectStraightTracks(
				level,
				p1,
				p2,
				flat,
				flat,
				material,
				true,
				paveBlock,
				paveCount,
				simulate
		);
		if (tracks < 0) {
			return -1;
		}
		consumed += tracks;

		for (int i = 1; i <= topFlat; i++) {
			BlockPos target = start.offset(
					stepX * (bottomFlat + curveH + i),
					stepY * rise,
					stepZ * (bottomFlat + curveH + i)
			);
			tracks = TrackPlacementUtils.placeTrackBlock(
					level,
					target,
					axis,
					flat,
					material,
					flatShape,
					paveBlock,
					paveCount,
					simulate
			);
			if (tracks < 0) {
				return -1;
			}
			consumed += tracks;
		}

		return consumed;
	}

	private static int placeCurve(
			Level level,
			BlockPos pos1,
			BlockPos pos2,
			BlockState state1,
			BlockState state2,
			Vec3 axis1,
			Vec3 axis2,
			BezierConnection curve,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		if (!simulate) {
			TrackPlacementUtils.clearAboveCurveFoundation(level, curve, paveBlock);
		}
		return TrackPlacementUtils.placeSlopeTracks(
				level,
				pos1,
				pos2,
				state1,
				state2,
				axis1,
				axis2,
				0,
				0,
				pos1,
				pos2,
				curve,
				material,
				paveBlock,
				paveCount,
				simulate
		);
	}

	private static int connectStraightTracks(
			Level level,
			BlockPos start,
			BlockPos end,
			BlockState state1,
			BlockState state2,
			TrackMaterial material
	) {
		return connectStraightTracks(
				level,
				start,
				end,
				state1,
				state2,
				material,
				false,
				null,
				null,
				false
		);
	}

	private static int connectStraightTracks(
			Level level,
			BlockPos start,
			BlockPos end,
			BlockState state1,
			BlockState state2,
			TrackMaterial material,
			boolean allowSteep,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		TrackPlacementUtils.TrackConnection c = TrackPlacementUtils.prepareConnection(level, start, end, state1, state2);
		if (c == null || !c.parallel()) {
			return -1;
		}

		Vec3 cross2 = c.normedAxis2().cross(new Vec3(0, 1, 0));

		double ascend = c.end2().subtract(c.end1()).y;
		double absAscend = Math.abs(ascend);
		boolean slope = !c.normal1().equals(c.normal2());

		int end1Extent;
		int end2Extent = 0;
		double dist;

		double[] sTest = TrackPlacementUtils.intersect(
				c.end1(),
				c.end2(),
				c.normedAxis1(),
				cross2,
				Direction.Axis.Y
		);
		if (sTest == null || !Mth.equal(Math.abs(sTest[1]), 0)) {
			return -1;
		}
		boolean skipCurve = true;
		dist = VecHelper.getCenterOf(start).distanceTo(VecHelper.getCenterOf(end));
		end1Extent = (int) Math.round((dist + 1) / c.axis1().length());

		if (slope) {
			if (!skipCurve) {
				return -1;
			}
			if (Mth.equal(c.normal1().dot(c.normal2()), 0)) {
				return -1;
			}
			if ((c.axis1().y < 0 || c.axis2().y > 0) && ascend > 0) {
				return -1;
			}
			if ((c.axis1().y > 0 || c.axis2().y < 0) && ascend < 0) {
				return -1;
			}

			skipCurve = false;
			end1Extent = 0;
			end2Extent = 0;

			Direction.Axis plane = Mth.equal(c.axis1().x, 0) ? Direction.Axis.X : Direction.Axis.Z;
			double[] intersect = TrackPlacementUtils.intersect(c.end1(), c.end2(), c.normedAxis1(), c.normedAxis2(), plane);
			double dist1 = Math.abs(intersect[0] / c.axis1().length());
			double dist2 = Math.abs(intersect[1] / c.axis2().length());

			if (dist1 > dist2) {
				end1Extent = (int) Math.round(dist1 - dist2);
			}
			if (dist2 > dist1) {
				end2Extent = (int) Math.round(dist2 - dist1);
			}

			double turnSize = Math.min(dist1, dist2);
			if (intersect[0] < 0 || intersect[1] < 0) {
				return -1;
			}
			if (turnSize < 2) {
				return -1;
			}
		}

		if (skipCurve && !Mth.equal(ascend, 0)) {
			int hDistance = end1Extent;
			if (c.axis1().y == 0 || !Mth.equal(absAscend + 1, dist / c.axis1().length())) {
				if (c.axis1().y != 0 && c.axis1().y == -c.axis2().y) {
					return -1;
				}

				end1Extent = 0;
				double minHDistance = allowSteep
						? 2
						: Math.max(absAscend < 4
						? absAscend * 4 : absAscend * 3, 6)
						/ c.axis1().length();
				if (hDistance < minHDistance) {
					return -1;
				}
				skipCurve = false;
			}
		}

		boolean girder = TrackPlacementUtils.isMetalGirder(paveBlock);
		return TrackPlacementUtils.finishConnection(
				level, start, end, state1, state2, material, c,
				end1Extent, end2Extent, skipCurve, girder, paveBlock, paveCount, simulate
		);
	}
}