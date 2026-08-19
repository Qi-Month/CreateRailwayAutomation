package dev.celestiacraft.railway_automation.common.event;

import dev.celestiacraft.railway_automation.common.event.placer.TrackStraightPlacer;
import dev.celestiacraft.railway_automation.common.event.placer.TrackTurnPlacer;
import com.simibubi.create.content.trains.track.TrackMaterial;
import dev.celestiacraft.railway_automation.common.register.RABlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class TrackPlacingHandler {
	public static Builder builder(Level level, BlockPos pos, Vec3i offset, Direction direction) {
		return new Builder(level, pos, offset, direction);
	}

	public static class Builder {
		private final Level level;
		private final BlockPos pos;
		private final Vec3i offset;
		private Vec3i straightOffset = new Vec3i(0, 0, 0);
		private TrackStraightPlacer.Builder straightBuilder = null;
		private TrackTurnPlacer.Builder turnBuilder = null;

		private Builder(Level level, BlockPos pos, Vec3i offset, Direction direction) {
			this.level = level;
			this.pos = pos;
			this.offset = offset;
			Vec3i straightOffset = new Vec3i(0, 0, 0);
			Vec3i turnOffset = new Vec3i(0, 0, 0);

			if (!this.level.getBlockState(this.pos).is(RABlock.TRACK_PLACER.get())) {
				return;
			}

			int offsetX = this.offset.getX();
			int offsetY = this.offset.getY();
			int offsetZ = this.offset.getZ();

			if (offsetX > 16 && direction == Direction.EAST) {
				straightOffset = new Vec3i(offsetX - 16, offsetY, 0);
				turnOffset = new Vec3i(16, 0, offsetZ);
			}
			if (offsetX < -16 && direction == Direction.WEST) {
				straightOffset = new Vec3i(offsetX + 16, offsetY, 0);
				turnOffset = new Vec3i(-16, 0, offsetZ);
			}
			if (offsetZ > 16 && direction == Direction.SOUTH) {
				straightOffset = new Vec3i(0, offsetY, offsetZ - 16);
				turnOffset = new Vec3i(offsetX, 0, 16);
			}
			if (offsetZ < -16 && direction == Direction.NORTH) {
				straightOffset = new Vec3i(0, offsetY, offsetZ + 16);
				turnOffset = new Vec3i(offsetX, 0, -16);
			}
			this.straightOffset = straightOffset;
			straightBuilder = TrackStraightPlacer.builder(this.level, this.pos, straightOffset);
			turnBuilder = TrackTurnPlacer.builder(this.level, this.pos.offset(straightOffset), turnOffset);
		}

		public int trackCount() {
			int straightCount = straightBuilder.trackCount();
			int turnCount = turnBuilder.trackCount();
			return straightCount + turnCount;
		}

		public Builder material(TrackMaterial material) {
			if (straightBuilder != null) {
				straightBuilder.material(material);
			}
			if (turnBuilder != null) {
				turnBuilder.material(material);
			}
			return this;
		}

		public int pave(Block paveBlock) {
			straightBuilder.pave(paveBlock);
			turnBuilder.pave(paveBlock);
			int straightPave = straightBuilder.paveCount();
			int turnPave = turnBuilder.paveCount();
			return straightPave + turnPave;
		}

		public void build() {
			if (straightBuilder == null || turnBuilder == null) {
				return;
			}
			straightBuilder.execute();

			boolean straightPlaced = straightBuilder.trackCount() > 0;
			boolean noStraightNeeded = straightOffset.getX() == 0
					&& straightOffset.getY() == 0
					&& straightOffset.getZ() == 0;

			if (straightPlaced || noStraightNeeded) {
				turnBuilder.execute();
			}
		}
	}
}