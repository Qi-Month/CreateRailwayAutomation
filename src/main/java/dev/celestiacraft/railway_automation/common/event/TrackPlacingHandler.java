package dev.celestiacraft.railway_automation.common.event;

import dev.celestiacraft.railway_automation.common.event.placer.TrackStraightPlacer;
import dev.celestiacraft.railway_automation.common.event.placer.TrackTurnPlacer;
import dev.celestiacraft.railway_automation.utils.TrackPlacementUtils;
import com.simibubi.create.content.trains.track.TrackMaterial;
import dev.celestiacraft.railway_automation.common.register.RABlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class TrackPlacingHandler {
	/**
	 * 目标点是否位于铺设器面向方向的正前方。
	 */
	public static boolean isFacingTarget(Vec3i offset, Direction direction) {
		if (offset == null || direction == null) {
			return false;
		}
		return switch (direction) {
			case EAST -> offset.getX() > 0;
			case WEST -> offset.getX() < 0;
			case SOUTH -> offset.getZ() > 0;
			case NORTH -> offset.getZ() < 0;
			default -> false;
		};
	}

	public static Builder builder(Level level, BlockPos pos, Vec3i offset, Direction direction, int turnSpan) {
		return new Builder(level, pos, offset, direction, turnSpan);
	}

	public static class Builder {
		private final Level level;
		private final BlockPos pos;
		private final Vec3i offset;
		/**
		 * 本次铺设允许使用的转弯半径（格），来自放置器顶部的滑条
		 */
		private final int turnSpan;
		private TrackStraightPlacer.Builder straightBuilder = null;
		private TrackTurnPlacer.Builder turnBuilder = null;
		private TrackStraightPlacer.Builder tailBuilder = null;

		private Builder(Level level, BlockPos pos, Vec3i offset, Direction direction, int turnSpan) {
			this.level = level;
			this.pos = pos;
			this.offset = offset;
			this.turnSpan = TrackPlacementUtils.clampTurnSpan(turnSpan);

			if (!this.level.getBlockState(this.pos).is(RABlock.TRACK_PLACER.get())) {
				return;
			}

			int offsetX = this.offset.getX();
			int offsetY = this.offset.getY();
			int offsetZ = this.offset.getZ();

			// 目标点必须位于面向方向的正前方，位于反方向（或侧向）时该位置信息不可用
			if (!isFacingTarget(this.offset, direction)) {
				return;
			}

			// 朝向方向与横向的水平可用距离，横向的正负表示目标点偏离哪一侧
			int facing = switch (direction) {
				case EAST -> offsetX;
				case WEST -> -offsetX;
				case SOUTH -> offsetZ;
				case NORTH -> -offsetZ;
				default -> 0;
			};
			int lateral = switch (direction) {
				case EAST, WEST -> offsetZ;
				case SOUTH, NORTH -> offsetX;
				default -> 0;
			};

			Vec3i firstOffset;
			Vec3i bendOffset = new Vec3i(0, 0, 0);
			Vec3i secondOffset = new Vec3i(0, 0, 0);

			if (lateral != 0) {
				int absLateral = Math.abs(lateral);
				int shortSide = Math.min(facing, absLateral);

				// 较短一边达不到最小曲线跨度时，该位置信息不可用
				if (shortSide < TrackPlacementUtils.MIN_TURN_SPAN) {
					return;
				}

				// 转弯半径取滑条设置值，可用距离更短时向较短的一边收敛
				int radius = Math.min(shortSide, turnSpan);
				int firstLen = facing - radius;
				int secondLen = absLateral - radius;
				boolean facingLonger = facing >= absLateral;

				// 高差只能铺在较长的直段上，较长直段为空时该位置信息不可用
				if (offsetY != 0 && (facingLonger ? firstLen : secondLen) <= 0) {
					return;
				}

				int stepX = direction.getStepX();
				int stepZ = direction.getStepZ();
				int sideX = stepX != 0 ? 0 : Integer.signum(lateral);
				int sideZ = stepX != 0 ? Integer.signum(lateral) : 0;

				// 直轨道（朝向） - 弯轨道 - 直轨道（横向），高差铺在较长的直段上
				firstOffset = new Vec3i(stepX * firstLen, facingLonger ? offsetY : 0, stepZ * firstLen);
				bendOffset = new Vec3i((stepX + sideX) * radius, 0, (stepZ + sideZ) * radius);
				secondOffset = new Vec3i(sideX * secondLen, facingLonger ? 0 : offsetY, sideZ * secondLen);
			} else {
				// 横向偏移为 0 时目标点与朝向共线，属于纯直线铺设，不需要转弯段
				firstOffset = new Vec3i(offsetX, offsetY, offsetZ);
			}

			if (isNonZero(firstOffset)) {
				straightBuilder = TrackStraightPlacer.builder(this.level, this.pos, firstOffset);
			}
			if (isNonZero(bendOffset)) {
				turnBuilder = TrackTurnPlacer.builder(this.level, this.pos.offset(firstOffset), bendOffset);
			}
			if (isNonZero(secondOffset)) {
				tailBuilder = TrackStraightPlacer.builder(
						this.level,
						this.pos.offset(firstOffset).offset(bendOffset),
						secondOffset
				);
			}
		}

		private static boolean isNonZero(Vec3i offset) {
			return offset.getX() != 0 || offset.getY() != 0 || offset.getZ() != 0;
		}

		/**
		 * 任一段无法铺设时整条线路都视为不可用，避免只铺出半截断轨
		 */
		private boolean segmentsValid() {
			if (straightBuilder != null && straightBuilder.trackCount() <= 0) {
				return false;
			}
			if (turnBuilder != null && turnBuilder.trackCount() <= 0) {
				return false;
			}
			return tailBuilder == null || tailBuilder.trackCount() > 0;
		}

		public int trackCount() {
			if (!segmentsValid()) {
				return 0;
			}
			int count = 0;
			if (straightBuilder != null) {
				count += straightBuilder.trackCount();
			}
			if (turnBuilder != null) {
				count += turnBuilder.trackCount();
			}
			if (tailBuilder != null) {
				count += tailBuilder.trackCount();
			}
			return count;
		}

		public Builder material(TrackMaterial material) {
			if (straightBuilder != null) {
				straightBuilder.material(material);
			}
			if (turnBuilder != null) {
				turnBuilder.material(material);
			}
			if (tailBuilder != null) {
				tailBuilder.material(material);
			}
			return this;
		}

		public int pave(Block paveBlock) {
			if (straightBuilder != null) {
				straightBuilder.pave(paveBlock);
			}
			if (turnBuilder != null) {
				turnBuilder.pave(paveBlock);
			}
			if (tailBuilder != null) {
				tailBuilder.pave(paveBlock);
			}
			if (!segmentsValid()) {
				return 0;
			}
			int count = 0;
			if (straightBuilder != null) {
				count += straightBuilder.paveCount();
			}
			if (turnBuilder != null) {
				count += turnBuilder.paveCount();
			}
			if (tailBuilder != null) {
				count += tailBuilder.paveCount();
			}
			return count;
		}

		public void build() {
			if (!segmentsValid()) {
				return;
			}
			if (straightBuilder != null) {
				straightBuilder.execute();
			}
			if (turnBuilder != null) {
				turnBuilder.execute();
			}
			if (tailBuilder != null) {
				tailBuilder.execute();
			}
		}
	}
}