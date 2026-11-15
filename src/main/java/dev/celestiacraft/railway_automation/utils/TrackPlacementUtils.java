package dev.celestiacraft.railway_automation.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.utility.BlockHelper;

import dev.celestiacraft.railway_automation.config.CommonConfig;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class TrackPlacementUtils {

	public static boolean isMetalGirder(Block paveBlock) {
		return paveBlock != null && AllBlocks.METAL_GIRDER.has(paveBlock.defaultBlockState());
	}

	/**
	 * 放置器滑条可调的转弯半径（跨度）下限，Create 自身要求 90 度转弯至少跨 8 格
	 */
	public static final int MIN_TURN_SPAN = 8;
	/**
	 * 放置器滑条可调的转弯半径（跨度）上限
	 */
	public static final int MAX_TURN_SPAN = 64;
	/**
	 * 放置器出厂时的转弯半径（跨度），沿用旧配置的默认上限
	 */
	public static final int DEFAULT_TURN_SPAN = 32;

	public static int clampTurnSpan(int configured) {
		return Mth.clamp(configured, MIN_TURN_SPAN, MAX_TURN_SPAN);
	}

	public static int maxVerticalRise() {
		return CommonConfig.maxVerticalRise();
	}
	public static int slopeCurveLength() {
		return CommonConfig.slopeCurveLength();
	}
	public static double gentleSlopeRatio() {
		return CommonConfig.gentleSlopeRatio();
	}
	public static int slopeInsertRise() {
		return CommonConfig.slopeInsertRise();
	}

	/**
	 * 45 度坡道两端过渡曲线允许的最短水平长度（垂直高差恒为其一半）
	 */
	public static final int MIN_SLOPE_CURVE_LENGTH = 4;

	/**
	 * 按可用水平距离为 45 度坡道挑一段尽量平缓、且不超过配置值的过渡曲线长度。
	 * <p>
	 * 每侧过渡曲线的水平长度为 curveH、垂直高差为 curveH/2 时，中间的 45 度段长度为
	 * {@code rise - curveH}，整条爬升占用 {@code rise + curveH + 1} 格水平；为了让两端
	 * 平段合计至少留出 1 格，需要 {@code curveH <= length - rise - 2}。空间不足时返回 0，
	 * 由调用方判定位置不可用。
	 *
	 * @param rise   两端的高差（格）
	 * @param length 可用的水平距离（格）
	 * @return 4 至配置值之间的偶数，放不下时返回 0
	 */
	public static int fitSlopeCurveLength(int rise, int length) {
		int room = length - rise - 2;
		if (room < MIN_SLOPE_CURVE_LENGTH || rise <= MIN_SLOPE_CURVE_LENGTH) {
			return 0;
		}
		// 曲线段本身要占掉一部分高差，45 度段至少还要 1 格，因此曲线长度不得达到高差本身
		int fitted = Math.min(slopeCurveLength(), Math.min(room, rise - 1));
		fitted -= fitted % 2;
		return fitted >= MIN_SLOPE_CURVE_LENGTH ? fitted : 0;
	}

	public static int gentleSlopeLength(int rise) {
		if (rise <= 0) {
			return 0;
		}
		return Math.max(1, (int) Math.ceil(rise / gentleSlopeRatio()));
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
		Vec3 paveAxis = axis == Direction.Axis.X ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
		if (!simulate) {
			clearAboveFoundation(level, target, paveAxis, paveBlock);
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
			clearAboveCurveFoundation(level, curve, paveBlock);
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
	 * 地基上方需要清理的高度（格）
	 */
	public static final int FOUNDATION_CLEAR_HEIGHT = 4;

	/**
	 * 地基上方空间清理（直线段）
	 *
	 * @param level         世界
	 * @param trackPos      轨道方块所在位置
	 * @param paveDirection 轨道延伸方向（与铺设地基时使用的方向一致）
	 * @param paveBlock     地基方块，为 null 时退化为仅清理轨道所在列及两侧
	 */
	public static void clearAboveFoundation(Level level, BlockPos trackPos, Vec3 paveDirection, Block paveBlock) {
		Set<BlockPos> foundations = collectStraightFoundation(level, trackPos.below(), paveDirection, 1, paveBlock);
		if (foundations.isEmpty()) {
			// 地基不会被铺设时，退化为「轨道列 + 两侧」的清理范围，保持原有的空间整理行为
			BlockPos base = trackPos.below();
			Vec3 cross = new Vec3(0, 1, 0).cross(paveDirection);
			Vec3 lateral = cross.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : cross.normalize();
			Vec3 center = Vec3.atCenterOf(base);
			foundations.add(base);
			foundations.add(BlockPos.containing(center.add(lateral)));
			foundations.add(BlockPos.containing(center.subtract(lateral)));
		}
		clearAboveFoundations(level, foundations);
	}

	/**
	 * 以 {@link TrackPaver#paveStraight} 的模拟结果收集地基位置
	 */
	public static Set<BlockPos> collectStraightFoundation(
			Level level,
			BlockPos start,
			Vec3 direction,
			int extent,
			Block paveBlock
	) {
		Set<BlockPos> foundations = new HashSet<>();
		if (level == null || start == null || direction == null || paveBlock == null) {
			return foundations;
		}
		TrackPaver.paveStraight(level, start, direction, extent, paveBlock, true, foundations);
		return foundations;
	}

	/**
	 * 地基上方空间清理（曲线段）
	 *
	 * @param level     世界
	 * @param curve     贝塞尔连接
	 * @param paveBlock 地基方块
	 */
	public static void clearAboveCurveFoundation(Level level, BezierConnection curve, Block paveBlock) {
		if (level == null || curve == null) {
			return;
		}
		Set<BlockPos> foundations = collectCurveFoundation(level, curve, paveBlock);
		if (foundations.isEmpty()) {
			foundations = estimateCurveFoundation(curve);
		}
		clearAboveFoundations(level, foundations);
	}

	/**
	 * 以 {@link TrackPaver#paveCurve} 的模拟结果收集曲线地基位置
	 */
	public static Set<BlockPos> collectCurveFoundation(Level level, BezierConnection curve, Block paveBlock) {
		Set<BlockPos> foundations = new HashSet<>();
		if (level == null || curve == null || paveBlock == null) {
			return foundations;
		}
		TrackPaver.paveCurve(level, curve, paveBlock, true, foundations);
		return foundations;
	}

	/**
	 * 清理地基位置上方的方块
	 */
	private static void clearAboveFoundations(Level level, Set<BlockPos> foundations) {
		Map<Pair<Integer, Integer>, Set<Integer>> columns = new HashMap<>();
		for (BlockPos pos : foundations) {
			columns.computeIfAbsent(Pair.of(pos.getX(), pos.getZ()), key -> new HashSet<>()).add(pos.getY());
		}
		for (Map.Entry<Pair<Integer, Integer>, Set<Integer>> entry : columns.entrySet()) {
			Set<Integer> foundationYs = entry.getValue();
			// 半砖地基会占据两格的各一半，因此从最低地基格之上清到最高地基格之上 FOUNDATION_CLEAR_HEIGHT 格，
			// 保证两种地基形态下都恰好让出 4 格轨道空间
			int bottomY = Collections.min(foundationYs) + 1;
			int topY = Collections.max(foundationYs) + FOUNDATION_CLEAR_HEIGHT;
			for (int y = bottomY; y <= topY; y++) {
				if (foundationYs.contains(y)) {
					continue;
				}
				BlockPos target = new BlockPos(entry.getKey().getFirst(), y, entry.getKey().getSecond());
				BlockState state = level.getBlockState(target);
				if (state.isAir() || state.getBlock() instanceof ITrackBlock) {
					continue;
				}
				level.destroyBlock(target, false);
			}
		}
	}

	/**
	 * 按曲线几何推算地基位置（不含半砖偏移），仅用于地基不会被实际铺设的情况
	 */
	private static Set<BlockPos> estimateCurveFoundation(BezierConnection curve) {
		Set<BlockPos> foundations = new HashSet<>();

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
			foundations.add(new BlockPos(
					origin.getX() + entry.getKey().getFirst(),
					origin.getY() + Mth.floor(entry.getValue()),
					origin.getZ() + entry.getKey().getSecond()
			));
		}
		return foundations;
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

			// 1.21.1 的 TrackShape 已移除 teleport 形状（TE/TW/TN/TS），这里不再需要形状归一

			for (int i = 0; i < (curve != null ? extent + 1 : extent); i++) {
				Vec3 offset = axis.scale(i);
				BlockPos offsetPos = pos.offset(BlockPos.containing(offset));
				if (!simulate) {
					clearAboveFoundation(level, offsetPos, axis, paveBlock);
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
				clearAboveFoundation(level, targetPos, first ? axis1 : axis2, paveBlock);
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
