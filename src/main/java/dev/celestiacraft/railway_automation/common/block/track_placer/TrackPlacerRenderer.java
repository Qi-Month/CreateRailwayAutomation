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
	/**
	 * 单独开一个缓冲仓，不依赖 Create 的 CachedBuffers.GENERIC_BLOCK 是否已注册。
	 */
	private static final Compartment<BlockState> TRACK_COMPARTMENT = new Compartment<>();

	private static boolean trackCompartmentRegistered;

	/**
	 * 提示牌模型。必须在模型注册事件（ModelEvent.RegisterAdditional）之前完成登记，
	 * 因此由 {@link #init()} 在客户端初始化阶段提前触发本类的静态初始化。
	 */
	private static final PartialModel SIGN_LEFT = PartialModel.of(RailwayAutomation.loadResource("block/track_placer/sign_left"));
	private static final PartialModel SIGN_RIGHT = PartialModel.of(RailwayAutomation.loadResource("block/track_placer/sign_right"));

	/**
	 * 宽轨相对普通轨道的额外横向偏移：8 个模型单位 = 0.5 格。
	 */
	private static final double WIDE_GAUGE_SHIFT = 0.5D;

	/**
	 * 单轨悬轨（汽鸣铁道的 monorail）的轨距类型 id。
	 */
	private static final String MONORAIL_GAUGE = "monorail";

	/**
	 * 仅用于触发类加载，使 {@link #SIGN_LEFT} / {@link #SIGN_RIGHT} 在模型烘焙之前完成登记。
	 */
	public static void init() {
	}

	public TrackPlacerRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	protected void renderSafe(TrackPlacerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
		// The block is a full opaque cube, so the light at its own position is 0 and the filter item
		// would render pitch black. Use the light of the block above, where the filter slot sits.
		Level level = be.getLevel();
		if (level != null) {
			light = LevelRenderer.getLightColor(level, be.getBlockPos().above());
		}

		renderTrack(be, ms, buffer, light);
		renderSigns(be, ms, buffer, light);
		FilteringRenderer.renderOnBlockEntity(be, partialTicks, ms, buffer, light, overlay);
	}

	/**
	 * 在原地渲染一条与方块朝向同向的水平直轨：
	 * 朝南/朝北（水平朝向位于 Z 轴）使用 {@link TrackShape#ZO}，朝东/朝西（X 轴）使用 {@link TrackShape#XO}。
	 * 使用的 blockstate 与真实 create:track 完全一致，因此位置、模型、朝向都与直接放置的轨道相同。
	 */
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

	/**
	 * 在方块所在位置渲染两侧提示牌（sign_left / sign_right）。
	 * <p>
	 * 依据过滤槽中轨道物品的 {@link TrackMaterial.TrackType} 决定：
	 * <ul>
	 * <li>standard（Create 安山岩轨道、汽鸣铁道普通材质轨道）—— 原位渲染；</li>
	 * <li>wide_gauge（含各材质的 _wide 变体）—— 两侧各向外额外偏移 8 个模型单位（0.5 格）；</li>
	 * <li>其余（narrow_gauge、monorail、universal / 幻渺轨道）—— 不渲染。</li>
	 * </ul>
	 */
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

		double shift = layout == SignLayout.WIDE ? WIDE_GAUGE_SHIFT : 0.0D;
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

	/**
	 * 提示牌模型是以 facing=north（blockstate 中 y=0）为基准制作的：
	 * 轨道沿 Z 轴、提示牌分列局部 X 轴两侧。
	 * <p>
	 * blockstate 的 y 旋转值 north=0 / east=90 / south=180 / west=270 在 {@link PoseStack} 中
	 * 对应的绕 Y 轴角度为它的相反数（{@code BlockModelRotation} 使用 {@code rotationYXZ(-y)}），
	 * 因此这里直接使用 0 / -90 / 180 / 90。
	 */
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
	 * 提示牌局部 +X 轴（即 sign_right 所在的一侧）在旋转之后对应的世界方向，
	 * 也就是「面向 facing 时的右手方向」。
	 */
	private static Vec3 rightOf(Direction facing) {
		return switch (facing) {
			case EAST -> new Vec3(0.0D, 0.0D, 1.0D);
			case SOUTH -> new Vec3(-1.0D, 0.0D, 0.0D);
			case WEST -> new Vec3(0.0D, 0.0D, -1.0D);
			default -> new Vec3(1.0D, 0.0D, 0.0D);
		};
	}

	/**
	 * 过滤槽中轨道物品的轨距类型 id：standard（Create 普通轨道与汽鸣铁道普通材质轨道）、
	 * wide_gauge、narrow_gauge、monorail、universal（幻渺轨道）。
	 * 过滤槽为空或物品不是轨道时返回 null。
	 */
	@Nullable
	private static String getGauge(TrackPlacerBlockEntity be) {
		Block trackBlock = getFilteredTrackBlock(be);
		if (trackBlock == null) {
			return null;
		}
		return TrackMaterial.fromItem(trackBlock.asItem())
				.trackType.id.getPath();
	}

	private static SignLayout getSignLayout(TrackPlacerBlockEntity be) {
		String gauge = getGauge(be);
		if (gauge == null) {
			// 过滤槽为空时轨道渲染退回 Create 默认（安山岩）轨道，提示牌同样按普通轨道处理
			return SignLayout.STANDARD;
		}
		if (MONORAIL_GAUGE.equals(gauge)) {
			return SignLayout.NONE;
		}
		if ("standard".equals(gauge)) {
			return SignLayout.STANDARD;
		}
		if ("wide_gauge".equals(gauge)) {
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
		if (MONORAIL_GAUGE.equals(getGauge(be))) {
			// 单轨悬轨是一条悬挂的结构，与方块本体的空间关系完全不同，原地按直轨渲染只会穿模
			return null;
		}

		Direction facing = be.getBlockState()
				.getValue(TrackPlacerBlock.FACING);
		TrackShape shape = facing.getAxis() == Direction.Axis.Z ? TrackShape.ZO : TrackShape.XO;

		Block trackBlock = getFilteredTrackBlock(be);
		if (trackBlock == null) {
			// 过滤槽为空时退回 Create 默认（安山岩）轨道
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
	private static Block getFilteredTrackBlock(TrackPlacerBlockEntity be) {
		ItemStack stack = be.getFiltering()
				.getFilter();
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
