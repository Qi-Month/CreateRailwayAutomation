package dev.celestiacraft.railway_automation.utils;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TrackPlacementUtils {
	/**
	 * 判断地基方块是否为金属梁
	 *
	 * @param paveBlock 地基方块
	 * @return
	 */
	public static boolean isMetalGirder(Block paveBlock) {
		return paveBlock != null && AllBlocks.METAL_GIRDER.has(paveBlock.defaultBlockState());
	}

	/**
	 * 单个轨道的放置
	 *
	 * @param level
	 * @param target
	 * @param axis
	 * @param source
	 * @param material
	 * @param shape
	 * @param paveBlock
	 * @param paveCount
	 * @param simulate
	 * @return
	 */
	public static int placeTrackBlock(
			Level level,
			BlockPos target,
			Direction.Axis axis,
			BlockState source,
			TrackMaterial material,
			TrackShape shape,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		if (!simulate) {
			clearAboveFoundation(level, target, axis);
		}
		BlockState stateAtPos = level.getBlockState(target);

		int count;
		if (simulate) {
			count = simulateTrackPlacement(stateAtPos, level, target);
			if (count < 0) {
				return -1;
			}
		} else {
			if (!stateAtPos.canBeReplaced()
					&& !stateAtPos.is(BlockTags.FLOWERS)
					&& !(stateAtPos.getBlock() instanceof ITrackBlock)) {
				return -1;
			}

			BlockState toPlace = BlockHelper.copyProperties(source, material.getBlock().defaultBlockState())
					.setValue(TrackBlock.SHAPE, shape)
					.setValue(TrackBlock.HAS_BE, false);

			if (stateAtPos.getBlock() instanceof ITrackBlock existingTrack) {
				toPlace = existingTrack.overlay(level, target, stateAtPos, toPlace);
			}

			level.setBlock(target, ProperWaterloggedBlock.withWater(level, toPlace, target), 3);
			count = stateAtPos.canBeReplaced() || stateAtPos.is(BlockTags.FLOWERS) ? 1 : 0;
		}

		if (paveBlock != null && paveCount != null) {
			Vec3 paveAxis = axis == Direction.Axis.X ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
			paveCount[0] += TrackPaver.paveStraight(
					level,
					target.below(),
					paveAxis,
					1,
					paveBlock,
					simulate,
					new HashSet<>()
			);
		}
		return count;
	}

	@FunctionalInterface
	public interface PlacerFunction {
		int place(
				Level level,
				BlockPos start,
				Vec3i offset,
				TrackMaterial material,
				Block paveBlock,
				int[] paveCount,
				boolean simulate
		);
	}

	/**
	 * 直线/转弯铺轨共用的 Builder
	 */
	public static PlacementBuilder builder(Level level, BlockPos start, Vec3i offset, PlacerFunction placer) {
		return new PlacementBuilder(level, start, offset, placer);
	}

	public static class PlacementBuilder {
		private final Level level;
		private final BlockPos start;
		private final Vec3i offset;
		private final PlacerFunction placer;
		private TrackMaterial material;
		private Block paveBlock;
		private int cachedTrackCount = -1;
		private int cachedPaveCount = -1;
		private boolean planned = false;
		private boolean executed = false;

		protected PlacementBuilder(Level level, BlockPos start, Vec3i offset, PlacerFunction placer) {
			this.level = level;
			this.start = start;
			this.offset = offset;
			this.placer = placer;
			BlockState state = level.getBlockState(start);
			material = state.getBlock() instanceof ITrackBlock track
					? track.getMaterial()
					: TrackMaterial.ANDESITE;
		}

		public PlacementBuilder material(TrackMaterial material) {
			this.material = material;
			return this;
		}

		public PlacementBuilder pave(Block paveBlock) {
			if (this.paveBlock != paveBlock) {
				this.paveBlock = paveBlock;
				planned = false;
				cachedTrackCount = -1;
				cachedPaveCount = -1;
			}
			return this;
		}

		private void plan() {
			if (planned) {
				return;
			}
			int[] paveCount = new int[1];
			cachedTrackCount = placer.place(level, start, offset, material, paveBlock, paveCount, true);
			cachedPaveCount = paveCount[0];
			planned = true;
		}

		public int trackCount() {
			plan();
			return cachedTrackCount;
		}

		public int paveCount() {
			plan();
			return cachedPaveCount;
		}

		public void execute() {
			if (executed) {
				return;
			}
			int[] paveCount = new int[1];
			cachedTrackCount = placer.place(level, start, offset, material, paveBlock, paveCount, false);
			cachedPaveCount = paveCount[0];
			planned = true;
			executed = true;
		}
	}

	/**
	 * 轴选取、法线、曲线起点与方向翻转
	 */
	public record TrackConnection(
			Vec3 axis1,
			Vec3 axis2,
			Vec3 normedAxis1,
			Vec3 normedAxis2,
			Vec3 normal1,
			Vec3 normal2,
			Vec3 end1,
			Vec3 end2,
			boolean parallel
	) {
	}

	/**
	 * 两端轨道连接所需的几何数据
	 *
	 * @return
	 */
	public static TrackConnection prepareConnection(
			Level level,
			BlockPos start,
			BlockPos end,
			BlockState state1,
			BlockState state2
	) {
		if (!(state1.getBlock() instanceof ITrackBlock track1)
				|| !(state2.getBlock() instanceof ITrackBlock track2)
				|| !state1.hasProperty(TrackBlock.SHAPE)
				|| !state2.hasProperty(TrackBlock.SHAPE)) {
			return null;
		}

		Vec3 axis1 = pickAxis(level, start, state1, end);
		Vec3 axis2 = pickAxis(level, end, state2, start);
		if (axis1 == null || axis2 == null) {
			return null;
		}

		Vec3 normal1 = track1.getUpNormal(level, start, state1).normalize();
		Vec3 normal2 = track2.getUpNormal(level, end, state2).normalize();
		Vec3 normedAxis1 = axis1.normalize();
		Vec3 normedAxis2 = axis2.normalize();
		Vec3 end1 = track1.getCurveStart(level, start, state1, axis1);
		Vec3 end2 = track2.getCurveStart(level, end, state2, axis2);

		if (axis1.dot(end2.subtract(end1)) < 0) {
			axis1 = axis1.scale(-1);
			normedAxis1 = normedAxis1.scale(-1);
			end1 = track1.getCurveStart(level, start, state1, axis1);
		}

		double[] intersect = intersect(end1, end2, normedAxis1, normedAxis2, Direction.Axis.Y);
		boolean parallel = intersect == null;
		if ((parallel && normedAxis1.dot(normedAxis2) > 0)
				|| (!parallel && (intersect[0] < 0
				|| intersect[1] < 0))) {
			axis2 = axis2.scale(-1);
			normedAxis2 = normedAxis2.scale(-1);
			end2 = track2.getCurveStart(level, end, state2, axis2);
		}

		return new TrackConnection(
				axis1,
				axis2,
				normedAxis1,
				normedAxis2,
				normal1,
				normal2,
				end1,
				end2,
				parallel
		);
	}

	/**
	 * 依据两端延长量构建曲线（或直线直连）
	 *
	 * @return
	 */
	public static int finishConnection(
			Level level,
			BlockPos start,
			BlockPos end,
			BlockState state1,
			BlockState state2,
			TrackMaterial material,
			TrackConnection connection,
			int end1Extent,
			int end2Extent,
			boolean skipCurve,
			boolean girder,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		Vec3 offset1 = connection.axis1().scale(end1Extent);
		Vec3 offset2 = connection.axis2().scale(end2Extent);
		BlockPos targetPos1 = start.offset(BlockPos.containing(offset1));
		BlockPos targetPos2 = end.offset(BlockPos.containing(offset2));

		BezierConnection curve = skipCurve
				? null
				: new BezierConnection(
				Couple.create(targetPos1, targetPos2),
				Couple.create(connection.end1().add(offset1), connection.end2().add(offset2)),
				Couple.create(connection.normedAxis1(), connection.normedAxis2()),
				Couple.create(connection.normal1(), connection.normal2()),
				true,
				girder,
				material
		);

		if (!simulate && curve != null) {
			clearAboveCurveFoundation(level, curve);
		}

		return placeSlopeTracks(
				level,
				start,
				end,
				state1,
				state2,
				connection.axis1(),
				connection.axis2(),
				end1Extent,
				end2Extent,
				targetPos1,
				targetPos2,
				curve,
				material,
				paveBlock,
				paveCount,
				simulate
		);
	}

	/**
	 * 地基上方4格空间清理
	 *
	 * @param level
	 * @param pos
	 * @param axis
	 */
	public static void clearAboveFoundation(Level level, BlockPos pos, Direction.Axis axis) {
		for (int lateral = -1; lateral <= 1; lateral++) {
			for (int height = 0; height <= 3; height++) {
				BlockPos target = switch (axis) {
					case X -> pos.offset(0, height, lateral);
					case Z -> pos.offset(lateral, height, 0);
					default -> null;
				};
				if (target == null) {
					continue;
				}
				BlockState state = level.getBlockState(target);
				if (state.getBlock() instanceof ITrackBlock) {
					continue;
				}
				level.destroyBlock(target, false);
			}
		}
	}

	/**
	 * 地基上方3格空间清理
	 *
	 * @param level
	 * @param curve
	 */
	public static void clearAboveCurveFoundation(Level level, BezierConnection curve) {
		if (level == null || curve == null) {
			return;
		}

		BlockPos origin = curve.bePositions.getFirst();
		Vec3 start1 = curve.starts.getFirst().subtract(Vec3.atLowerCornerOf(origin)).add(0, 0.1875, 0);
		Vec3 start2 = curve.starts.getSecond().subtract(Vec3.atLowerCornerOf(origin)).add(0, 0.1875, 0);
		Vec3 axis1 = curve.axes.getFirst();
		Vec3 axis2 = curve.axes.getSecond();
		double handleLength = curve.getHandleLength();
		Vec3 finish1 = axis1.scale(handleLength).add(start1);
		Vec3 finish2 = axis2.scale(handleLength).add(start2);
		Vec3 faceNormal1 = curve.normals.getFirst();
		Vec3 faceNormal2 = curve.normals.getSecond();
		int segCount = curve.getSegmentCount();
		float[] lut = curve.getStepLUT();

		Map<Pair<Integer, Integer>, Double> yLevels = new HashMap<>();
		for (int i = 0; i < segCount; i++) {
			float t = i == segCount ? 1 : i * lut[i] / segCount;
			t += 0.5f / segCount;

			Vec3 result = VecHelper.bezier(start1, start2, finish1, finish2, t);
			Vec3 derivative = VecHelper.bezierDerivative(start1, start2, finish1, finish2, t).normalize();
			Vec3 faceNormal = faceNormal1.equals(faceNormal2) ? faceNormal1 : VecHelper.slerp(t, faceNormal1, faceNormal2);
			Vec3 normal = faceNormal.cross(derivative).normalize();
			Vec3 below = result.add(faceNormal.scale(-1.125f));
			Vec3 rail1 = below.add(normal.scale(0.97f));
			Vec3 rail2 = below.subtract(normal.scale(0.97f));
			Vec3 railMiddle = rail1.add(rail2).scale(0.5);

			for (Vec3 vec : new Vec3[]{rail1, rail2, railMiddle}) {
				BlockPos pos = BlockPos.containing(vec);
				Pair<Integer, Integer> key = Pair.of(pos.getX(), pos.getZ());
				if (!yLevels.containsKey(key) || yLevels.get(key) > vec.y) {
					yLevels.put(key, vec.y);
				}
			}
		}

		for (Map.Entry<Pair<Integer, Integer>, Double> entry : yLevels.entrySet()) {
			double y = entry.getValue();
			int floor = Mth.floor(y);
			int foundationY = floor + (y - floor >= 0.5 ? 1 : 0);
			BlockPos foundation = new BlockPos(
					origin.getX() + entry.getKey().getFirst(),
					origin.getY() + foundationY,
					origin.getZ() + entry.getKey().getSecond()
			);
			for (int height = 1; height <= 3; height++) {
				BlockPos target = foundation.offset(0, height, 0);
				BlockState state = level.getBlockState(target);
				if (state.getBlock() instanceof ITrackBlock) {
					continue;
				}
				level.destroyBlock(target, false);
			}
		}
	}

	/**
	 * 获取输入向量指向的方向轴
	 *
	 * @param offset
	 * @return
	 */
	public static Direction.Axis getSingleAxis(Vec3i offset) {
		int nonZero = 0;
		if (offset.getX() != 0) {
			nonZero++;
		}
		if (offset.getY() != 0) {
			nonZero++;
		}
		if (offset.getZ() != 0) {
			nonZero++;
		}
		if (nonZero != 1) {
			return null;
		}
		if (offset.getX() != 0) {
			return Direction.Axis.X;
		}
		if (offset.getY() != 0) {
			return Direction.Axis.Y;
		}
		return Direction.Axis.Z;
	}

	/**
	 * 获取目标轨道指向的方向轴
	 *
	 * @param level
	 * @param pos
	 * @param state
	 * @param other
	 * @return
	 */
	public static Vec3 pickAxis(Level level, BlockPos pos, BlockState state, BlockPos other) {
		if (!(state.getBlock() instanceof ITrackBlock track)) {
			return null;
		}
		Vec3 toOther = Vec3.atCenterOf(other).subtract(Vec3.atCenterOf(pos)).normalize();
		Vec3 best = null;
		double bestDot = -2;
		for (Vec3 axis : track.getTrackAxes(level, pos, state)) {
			Vec3 normed = axis.normalize();
			for (double sign : new double[]{1, -1}) {
				double dot = normed.scale(sign).dot(toOther);
				if (dot > bestDot) {
					bestDot = dot;
					best = axis.scale(sign);
				}
			}
		}
		return best;
	}

	public static double[] intersect(Vec3 p1, Vec3 p2, Vec3 r1, Vec3 r2, Direction.Axis plane) {
		p1 = project(p1, plane);
		p2 = project(p2, plane);
		r1 = project(r1, plane);
		r2 = project(r2, plane);

		Vec3 diff = p2.subtract(p1);
		double det = r1.x * r2.z - r1.z * r2.x;
		if (Mth.equal(det, 0)) {
			return null;
		}
		double t = (diff.x * r2.z - diff.z * r2.x) / det;
		double u = (diff.x * r1.z - diff.z * r1.x) / det;
		return new double[]{t, u};
	}

	public static Vec3 project(Vec3 v, Direction.Axis plane) {
		return switch (plane) {
			case X -> new Vec3(v.y(), 0, v.z());
			case Z -> new Vec3(v.x(), 0, v.y());
			default -> v;
		};
	}

	public static Direction.Axis horizontalAxis(Vec3 axis) {
		return axis.x != 0 ? Direction.Axis.X : Direction.Axis.Z;
	}

	public static BlockPos slopeBlockAtEnd(Vec3 targetTip, int stepX, int stepZ, boolean ascending, Direction.Axis axis) {
		int y = (int) Math.floor(targetTip.y) - (ascending ? 0 : 1);
		if (axis == Direction.Axis.X) {
			int x = (int) Math.floor(targetTip.x) - (stepX == 1 ? 0 : 1);
			int z = (int) Math.floor(targetTip.z - 0.5);
			return new BlockPos(x, y, z);
		}
		int x = (int) Math.floor(targetTip.x - 0.5);
		int z = (int) Math.floor(targetTip.z) - (stepZ == 1 ? 0 : 1);
		return new BlockPos(x, y, z);
	}

	public static Vec3 slopeTipAtEnd(BlockPos pos, int stepX, int stepZ, boolean ascending, Direction.Axis axis) {
		int y = pos.getY() + (ascending ? 1 : 0);
		if (axis == Direction.Axis.X) {
			int x = pos.getX() + (stepX == 1 ? 1 : 0);
			return new Vec3(x, y, pos.getZ() + 0.5);
		}
		int z = pos.getZ() + (stepZ == 1 ? 1 : 0);
		return new Vec3(pos.getX() + 0.5, y, z);
	}

	public static BlockPos flatBlockAtEnd(Vec3 targetTip, int stepX, int stepZ, Direction.Axis axis) {
		int y = (int) Math.floor(targetTip.y);
		if (axis == Direction.Axis.X) {
			int x = (int) Math.floor(targetTip.x) - (stepX == 1 ? 0 : 1);
			int z = (int) Math.floor(targetTip.z - 0.5);
			return new BlockPos(x, y, z);
		}
		int x = (int) Math.floor(targetTip.x - 0.5);
		int z = (int) Math.floor(targetTip.z) - (stepZ == 1 ? 0 : 1);
		return new BlockPos(x, y, z);
	}

	public static int placeSlopeTracks(
			Level level,
			BlockPos pos1,
			BlockPos pos2,
			BlockState state1,
			BlockState state2,
			Vec3 axis1,
			Vec3 axis2,
			int end1Extent,
			int end2Extent,
			BlockPos targetPos1,
			BlockPos targetPos2,
			BezierConnection curve,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		int consumed = 0;
		Set<BlockPos> visited = paveBlock != null && paveCount != null ? new HashSet<>() : null;

		for (boolean first : Iterate.trueAndFalse) {
			int extent = first ? end1Extent : end2Extent;
			Vec3 axis = first ? axis1 : axis2;
			BlockPos pos = first ? pos1 : pos2;
			BlockState state = first ? state1 : state2;
			if (state.hasProperty(TrackBlock.HAS_BE)) {
				state = state.setValue(TrackBlock.HAS_BE, false);
			}

			switch (state.getValue(TrackBlock.SHAPE)) {
				case TE, TW -> state = state.setValue(TrackBlock.SHAPE, TrackShape.XO);
				case TN, TS -> state = state.setValue(TrackBlock.SHAPE, TrackShape.ZO);
				default -> {
				}
			}

			for (int i = 0; i < (curve != null ? extent + 1 : extent); i++) {
				Vec3 offset = axis.scale(i);
				BlockPos offsetPos = pos.offset(BlockPos.containing(offset));
				if (!simulate) {
					clearAboveFoundation(level, offsetPos, horizontalAxis(axis));
				}
				BlockState stateAtPos = level.getBlockState(offsetPos);
				BlockState toPlace = BlockHelper.copyProperties(state, material.getBlock().defaultBlockState());

				if (simulate) {
					int s = simulateTrackPlacement(stateAtPos, level, offsetPos);
					if (s < 0) {
						return -1;
					}
					consumed += s;
				} else {
					if (stateAtPos.getBlock() instanceof ITrackBlock trackAtPos) {
						toPlace = trackAtPos.overlay(level, offsetPos, stateAtPos, toPlace);
					} else if (!stateAtPos.canBeReplaced() && !stateAtPos.is(BlockTags.FLOWERS)) {
						return -1;
					} else {
						consumed++;
					}
					level.setBlock(offsetPos, ProperWaterloggedBlock.withWater(level, toPlace, offsetPos), 3);
				}
				if (paveBlock != null && paveCount != null) {
					paveCount[0] += TrackPaver.paveStraight(level, offsetPos.below(), axis, 1, paveBlock, simulate, visited);
				}
			}
		}

		if (curve == null) {
			return consumed;
		}

		BlockState onto = material.getBlock().defaultBlockState();
		for (boolean first : Iterate.trueAndFalse) {
			BlockPos targetPos = first ? targetPos1 : targetPos2;
			BlockState stateAtPos = level.getBlockState(targetPos);
			if (stateAtPos.canBeReplaced() || stateAtPos.is(BlockTags.FLOWERS)) {
				consumed++;
			}
			BlockState endpointState = (stateAtPos.getBlock() instanceof ITrackBlock ? stateAtPos : BlockHelper.copyProperties(first ? state1 : state2, onto)).setValue(TrackBlock.HAS_BE, true);
			if (!simulate) {
				clearAboveFoundation(level, targetPos, horizontalAxis(first ? axis1 : axis2));
				level.setBlock(targetPos, ProperWaterloggedBlock.withWater(level, endpointState, targetPos), 3);
			}
		}

		if (paveBlock != null && paveCount != null) {
			paveCount[0] += TrackPaver.paveCurve(level, curve, paveBlock, simulate, visited);
		}

		if (simulate) {
			return consumed + (curve.getSegmentCount() + 1) / 2;
		}

		BlockEntity te1 = level.getBlockEntity(targetPos1);
		BlockEntity te2 = level.getBlockEntity(targetPos2);
		if (!(te1 instanceof TrackBlockEntity tte1) || !(te2 instanceof TrackBlockEntity tte2)) {
			return -1;
		}

		tte1.addConnection(curve);
		tte2.addConnection(curve.secondary());
		tte1.tilt.tryApplySmoothing();
		tte2.tilt.tryApplySmoothing();
		return consumed + (curve.getSegmentCount() + 1) / 2;
	}

	public static int simulateTrackPlacement(BlockState stateAtPos, BlockGetter level, BlockPos pos) {
		if (stateAtPos.getBlock() instanceof ITrackBlock) {
			return 0;
		}
		if (stateAtPos.canBeReplaced() || stateAtPos.is(BlockTags.FLOWERS)) {
			return 1;
		}
		return stateAtPos.getDestroySpeed(level, pos) >= 0 ? 1 : -1;
	}
}
