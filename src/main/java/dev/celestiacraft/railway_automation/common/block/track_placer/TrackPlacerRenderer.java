package dev.celestiacraft.railway_automation.common.block.track_placer;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import dev.celestiacraft.railway_automation.RailwayAutomation;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

public class TrackPlacerRenderer extends SafeBlockEntityRenderer<TrackPlacerBlockEntity> {
	private static final PartialModel SIGN_LEFT = PartialModel.of(RailwayAutomation.loadResource("block/track_placer/sign_left"));
	private static final PartialModel SIGN_RIGHT = PartialModel.of(RailwayAutomation.loadResource("block/track_placer/sign_right"));

	public static void init() {
	}

	public TrackPlacerRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	protected void renderSafe(TrackPlacerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
		Level level = be.getLevel();
		if (level != null) {
			light = LevelRenderer.getLightColor(level, be.getBlockPos().above());
		}

		renderTrack(be, ms, buffer, light);
		renderSigns(be, ms, buffer, light);
		FilteringRenderer.renderOnBlockEntity(be, partialTicks, ms, buffer, light, overlay);
	}

	private void renderTrack(TrackPlacerBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
		BlockState trackState = getTrackState(be);
		if (trackState == null) {
			return;
		}

		VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());
		CachedBuffers.block(trackState)
				.light(light)
				.renderInto(ms, vb);
	}

	private void renderSigns(TrackPlacerBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
		Direction facing = be.getBlockState()
				.getValue(TrackPlacerBlock.FACING);
		if (facing.getAxis() == Direction.Axis.Y) {
			return;
		}

		VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());

		renderSign(SIGN_LEFT, ms, vb, light, facing);
		renderSign(SIGN_RIGHT, ms, vb, light, facing);
	}

	private static void renderSign(PartialModel model, PoseStack ms, VertexConsumer vb, int light, Direction facing) {
		if (model.get() == null) {
			// 模型尚未烘焙（例如资源缺失），跳过而不是抛出异常
			return;
		}

		ms.pushPose();
		CachedBuffers.partialDirectional(model, Blocks.AIR.defaultBlockState(), facing, signTransform(facing))
				.light(light)
				.renderInto(ms, vb);
		ms.popPose();
	}

	private static Supplier<PoseStack> signTransform(Direction facing) {
		float angle = switch (facing) {
			case EAST -> -90.0F;
			case SOUTH -> 180.0F;
			case WEST -> 90.0F;
			default -> 0.0F;
		};
		return () -> {
			PoseStack stack = new PoseStack();
			stack.translate(0.5D, 0.0D, 0.5D);
			stack.mulPose(Axis.YP.rotationDegrees(angle));
			stack.translate(-0.5D, 0.0D, -0.5D);
			return stack;
		};
	}

	/**
	 * 预览只画 Create 本体的轨道：轨道种类由方块决定、材质存在轨道方块实体里，
	 * 因此无论手上选的是哪种 Create 轨道，预览都用同一个轨道方块
	 */
	@Nullable
	private static BlockState getTrackState(TrackPlacerBlockEntity be) {
		Direction facing = be.getBlockState()
				.getValue(TrackPlacerBlock.FACING);
		TrackShape shape = facing.getAxis() == Direction.Axis.Z ? TrackShape.ZO : TrackShape.XO;

		BlockState state = AllBlocks.TRACK.get()
				.defaultBlockState();
		if (!state.hasProperty(TrackBlock.SHAPE)) {
			// 无法确定朝向的轨道方块不渲染，避免画出一条方向错误的轨道
			return null;
		}
		return state.setValue(TrackBlock.SHAPE, shape);
	}
}
