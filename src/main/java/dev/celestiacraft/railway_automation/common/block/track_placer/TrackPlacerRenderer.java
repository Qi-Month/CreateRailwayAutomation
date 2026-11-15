package dev.celestiacraft.railway_automation.common.block.track_placer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackShape;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.celestiacraft.railway_automation.RailwayAutomation;
import dev.celestiacraft.railway_automation.compat.RACompat;
import dev.celestiacraft.railway_automation.compat.railways.RailwaysCompat;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBufferCache;
import net.createmod.catnip.render.SuperByteBufferCache.Compartment;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class TrackPlacerRenderer extends SafeBlockEntityRenderer<TrackPlacerBlockEntity> {
	private static final Compartment<BlockState> TRACK_COMPARTMENT = new Compartment<>();
	private static boolean trackCompartmentRegistered;
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

		ensureCompartmentRegistered();
		VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());
		CachedBuffers.block(TRACK_COMPARTMENT, trackState)
				.light(light)
				.renderInto(ms, vb);
	}

	private void renderSigns(TrackPlacerBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
		SignLayout layout = getSignLayout(be);
		if (layout == SignLayout.NONE) {
			return;
		}

		Direction facing = be.getBlockState()
				.getValue(TrackPlacerBlock.FACING);
		if (facing.getAxis() == Direction.Axis.Y) {
			return;
		}

		double shift = layout == SignLayout.WIDE ? RailwaysCompat.WIDE_GAUGE_SHIFT : 0.0D;
		Vec3 right = rightOf(facing)
				.scale(shift);
		VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());

		renderSign(SIGN_LEFT, be, ms, vb, light, facing, right.scale(-1.0D));
		renderSign(SIGN_RIGHT, be, ms, vb, light, facing, right);
	}

	private static void renderSign(PartialModel model, TrackPlacerBlockEntity be, PoseStack ms, VertexConsumer vb, int light, Direction facing, Vec3 offset) {
		if (model.get() == null) {
			// 模型尚未烘焙（例如资源缺失），跳过而不是抛出异常
			return;
		}

		ms.pushPose();
		ms.translate(offset.x, offset.y, offset.z);
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

	private static Vec3 rightOf(Direction facing) {
		return switch (facing) {
			case EAST -> new Vec3(0.0D, 0.0D, 1.0D);
			case SOUTH -> new Vec3(-1.0D, 0.0D, 0.0D);
			case WEST -> new Vec3(0.0D, 0.0D, -1.0D);
			default -> new Vec3(1.0D, 0.0D, 0.0D);
		};
	}

	@Nullable
	private static String getGauge(TrackPlacerBlockEntity be) {
		// SnR 未安装时没有其轨道类型可检测，一律按 Create 原版轨道处理
		if (!RACompat.isRailwaysLoaded()) {
			return null;
		}
		Block trackBlock = getConfiguredTrackBlock(be);
		if (trackBlock == null) {
			return null;
		}
		return TrackMaterial.fromItem(trackBlock.asItem())
				.trackType.id.getPath();
	}

	private static SignLayout getSignLayout(TrackPlacerBlockEntity be) {
		String gauge = getGauge(be);
		if (gauge == null) {
			// 未设置轨道或 SnR 未安装时，提示牌按 Create 原版轨道处理
			return SignLayout.STANDARD;
		}
		if (RailwaysCompat.isMonorail(gauge)) {
			return SignLayout.NONE;
		}
		if ("standard".equals(gauge)) {
			return SignLayout.STANDARD;
		}
		if (RailwaysCompat.isWideGauge(gauge)) {
			return SignLayout.WIDE;
		}
		return SignLayout.NONE;
	}

	private static void ensureCompartmentRegistered() {
		if (!trackCompartmentRegistered) {
			trackCompartmentRegistered = true;
			SuperByteBufferCache.getInstance()
					.registerCompartment(TRACK_COMPARTMENT);
		}
	}

	@Nullable
	private static BlockState getTrackState(TrackPlacerBlockEntity be) {
		// SnR 未安装时忽略已设置的轨道，只渲染 Create 原版轨道
		boolean railwaysLoaded = RACompat.isRailwaysLoaded();
		if (railwaysLoaded && RailwaysCompat.isMonorail(getGauge(be))) {
			// 单轨悬轨是一条悬挂的结构，与方块本体的空间关系完全不同，原地按直轨渲染只会穿模
			return null;
		}

		Direction facing = be.getBlockState()
				.getValue(TrackPlacerBlock.FACING);
		TrackShape shape = facing.getAxis() == Direction.Axis.Z ? TrackShape.ZO : TrackShape.XO;

		Block trackBlock = railwaysLoaded ? getConfiguredTrackBlock(be) : null;
		if (trackBlock == null) {
			// 未设置轨道时退回 Create 默认（安山岩）轨道
			trackBlock = AllBlocks.TRACK.get();
		}

		BlockState state = trackBlock.defaultBlockState();
		if (!state.hasProperty(TrackBlock.SHAPE)) {
			// 无法确定朝向的轨道方块不渲染，避免画出一条方向错误的轨道
			return null;
		}
		return state.setValue(TrackBlock.SHAPE, shape);
	}

	@Nullable
	private static Block getConfiguredTrackBlock(TrackPlacerBlockEntity be) {
		ItemStack stack = be.getPlacedTrack();
		if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof ITrackBlock) {
			return blockItem.getBlock();
		}
		return null;
	}

	private enum SignLayout {
		NONE, STANDARD, WIDE
	}
}
