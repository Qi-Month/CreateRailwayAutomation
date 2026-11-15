package dev.celestiacraft.railway_automation.common.block.track_placer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllTags;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.crate.BottomlessItemHandler;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import dev.celestiacraft.railway_automation.common.block.state.properties.track_placer.WorkingStatus;
import dev.celestiacraft.railway_automation.common.event.TrackPlacingHandler;
import dev.celestiacraft.railway_automation.RailwayAutomation;
import dev.celestiacraft.railway_automation.utils.TrackPlacementUtils;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public class TrackPlacerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
	public TrackPlacerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/**
	 * 要铺设的轨道，只作模板、不消耗物品；为空时按 Create 默认（安山岩）轨道处理
	 */
	private ItemStack placedTrack = ItemStack.EMPTY;
	private TrackPlacerPaveFilterBehaviour paveFilter2;
	/**
	 * 顶部滑条：本次铺设允许使用的转弯半径，范围 8 至 64
	 */
	private ScrollValueBehaviour turnRadiusBehaviour;

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		paveFilter2 = new TrackPlacerPaveFilterBehaviour(this, new TrackPlacerPaveFilterSlot(11));
		paveFilter2.withPredicate(TrackPlacerBlockEntity::isValidPaveFilter);
		behaviours.add(paveFilter2);

		// 原本的轨道过滤槽位置（x = 5）改放转弯半径滑条，数值随方块一起写进 NBT
		turnRadiusBehaviour = new ScrollValueBehaviour(
				Component.translatable("behaviour.railway_automation.track_placer.turn_radius"),
				this,
				new TrackPlacerPaveFilterSlot(5)
		);
		turnRadiusBehaviour.between(TrackPlacementUtils.MIN_TURN_SPAN, TrackPlacementUtils.MAX_TURN_SPAN);
		// 直接写字段而不是 setValue()，避免在方块实体构造阶段触发同步
		turnRadiusBehaviour.value = TrackPlacementUtils.DEFAULT_TURN_SPAN;
		turnRadiusBehaviour.withCallback(value -> {
			if (level != null && !level.isClientSide()) {
				refreshBuilder(level);
			}
		});
		turnRadiusBehaviour.withClientCallback(value -> {
			if (level != null && level.isClientSide()) {
				refreshBuilder(level);
			}
		});
		behaviours.add(turnRadiusBehaviour);

		behaviours.add(new DirectBeltInputBehaviour(this));
	}

	/**
	 * 手持轨道方块点击放置器时用它校验：要铺设的轨道同时决定铺设的材质
	 * <p>
	 * 只认 Create 本体的轨道。其它模组（例如汽鸣铁道）的轨道同样实现 {@link ITrackBlock}，
	 * 但它们的轨距与模型和原版轨道不通用，放进来会让预览与实际铺设的形状对不上，
	 * 所以在显式支持之前一律拒绝。
	 */
	public static boolean isValidTrackFilter(ItemStack stack) {
		return !stack.isEmpty()
				&& stack.getItem() instanceof BlockItem blockItem
				&& isCreateTrack(blockItem.getBlock());
	}

	/**
	 * 是否为 Create 本体的轨道方块：既实现 {@link ITrackBlock}，又注册在 {@code create} 命名空间下
	 */
	public static boolean isCreateTrack(Block block) {
		return block instanceof ITrackBlock
				&& "create".equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace());
	}

	/**
	 * 地基槽只接受普通方块：要铺设的轨道由手持轨道右键指定，不能拿来当铺路材料
	 * <p>
	 * Create 的 {@code invalid_for_track_paving} 标签只排除了支架，所以这里额外排除所有实现
	 * {@link ITrackBlock} 的方块，避免把任何模组的轨道当成地基材料。
	 */
	public static boolean isValidPaveFilter(ItemStack stack) {
		return !stack.isEmpty()
				&& stack.getItem() instanceof BlockItem blockItem
				&& !(blockItem.getBlock() instanceof ITrackBlock)
				&& !AllTags.AllItemTags.INVALID_FOR_TRACK_PAVING.matches(stack);
	}

	/**
	 * 设置要铺设的轨道，只记录种类、不消耗物品；传入空物品等于清除
	 */
	public void setPlacedTrack(ItemStack stack) {
		ItemStack next = isValidTrackFilter(stack) ? stack.copyWithCount(1) : ItemStack.EMPTY;
		if (ItemStack.isSameItemSameComponents(next, placedTrack)) {
			return;
		}
		placedTrack = next;
		setChanged();
		sendData();
		if (level != null && !level.isClientSide()) {
			refreshBuilder(level);
		}
	}

	/**
	 * 实际用于铺设的轨道物品，未设置时退回 Create 默认轨道
	 */
	public Item getPlacementTrackItem() {
		return placedTrack.isEmpty() ? AllBlocks.TRACK.get().asItem() : placedTrack.getItem();
	}

	/**
	 * 实际用于铺设的轨道材质，未设置时退回安山岩
	 */
	public TrackMaterial getPlacementTrackMaterial() {
		if (placedTrack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof ITrackBlock trackBlock) {
			return trackBlock.getMaterial();
		}
		return TrackMaterial.ANDESITE;
	}

	/**
	 * 本次铺设允许使用的转弯半径（格），取自方块顶部的滑条
	 */
	public int getTurnRadius() {
		return turnRadiusBehaviour == null
				? TrackPlacementUtils.DEFAULT_TURN_SPAN
				: TrackPlacementUtils.clampTurnSpan(turnRadiusBehaviour.getValue());
	}

	private int targetX = getBlockPos().getX();
	private int targetY = getBlockPos().getY();
	private int targetZ = getBlockPos().getZ();
	private String targetDimension = "";
	private boolean wasPowered;
	private TrackPlacingHandler.Builder builder;
	private boolean trackEnough = true;
	private boolean paveEnough = true;

	@Override
	public void tick() {
		super.tick();
		if (level == null) {
			return;
		}
		if (level.getGameTime() % 20 == 0) {
			refreshBuilder(level);
		}
		if (!level.isClientSide()) {
			tickRedstone(level);
		}
	}

	private void refreshBuilder(Level level) {
		Direction direction = getBlockState().getValue(TrackPlacerBlock.FACING);
		Vec3i offset = new Vec3i(
				targetX - worldPosition.getX(),
				targetY - worldPosition.getY(),
				targetZ - worldPosition.getZ()
		);
		builder = TrackPlacingHandler.builder(level, getBlockPos(), offset, direction, getTurnRadius());
		if (!level.isClientSide()) {
			refreshEnoughState(level);
		}
	}

	private void refreshEnoughState(Level level) {
		if (builder == null) {
			return;
		}

		ItemStack paveFilter = paveFilter2 != null
				? paveFilter2.getFilter()
				: ItemStack.EMPTY;

		Item trackItem = getPlacementTrackItem();
		builder.material(getPlacementTrackMaterial());

		boolean shouldPave = isValidPaveFilter(paveFilter);

		int requiredTrack = builder.trackCount();
		List<IItemHandler> handlers = findNeighborItemHandlers(level);
		trackEnough = countItems(handlers, trackItem, requiredTrack) >= requiredTrack;

		if (shouldPave) {
			Item paveItem = paveFilter.getItem();
			int requiredPave = builder.pave(Block.byItem(paveItem));
			paveEnough = countItems(handlers, paveItem, requiredPave) >= requiredPave;
		} else {
			paveEnough = true;
		}

		notifyUpdate();
	}

	private void tickRedstone(Level level) {
		boolean powered = level.hasNeighborSignal(worldPosition) || level.getDirectSignalTo(worldPosition) > 0;
		if (powered && !wasPowered) {
			refreshBuilder(level);
			TrackPlacingHandler.Builder placement = builder;
			if (placement == null) {
				wasPowered = powered;
				return;
			}
			ItemStack paveFilter = paveFilter2 != null ? paveFilter2.getFilter() : ItemStack.EMPTY;

			Item trackItem = getPlacementTrackItem();
			placement.material(getPlacementTrackMaterial());

			boolean shouldPave = isValidPaveFilter(paveFilter);
			List<IItemHandler> handlers = findNeighborItemHandlers(level);

			Item paveItem = null;
			int requiredPave = 0;
			if (shouldPave) {
				paveItem = paveFilter.getItem();
				Block paveBlock = Block.byItem(paveItem);
				requiredPave = placement.pave(paveBlock);
			}

			int requiredTrack = placement.trackCount();
			if (requiredTrack <= 0) {
				setStatus(level, WorkingStatus.ERROR);
				wasPowered = powered;
				return;
			}

			if (countItems(handlers, trackItem, requiredTrack) < requiredTrack
					|| (shouldPave && countItems(handlers, paveItem, requiredPave) < requiredPave)) {
				setStatus(level, WorkingStatus.ERROR);
				wasPowered = powered;
				return;
			}

			consumeItems(handlers, trackItem, requiredTrack);
			if (shouldPave) {
				consumeItems(handlers, paveItem, requiredPave);
			}
			setStatus(level, WorkingStatus.ACTIVATE);
			targetX = getBlockPos().getX();
			targetY = getBlockPos().getY();
			targetZ = getBlockPos().getZ();
			targetDimension = "";
			placement.build();
		} else if (!powered && wasPowered) {
			if (Objects.equals(targetDimension, "")) {
				setStatus(level, WorkingStatus.EMPTY);
			} else {
				setStatus(level, WorkingStatus.IDLE);
			}
		}
		wasPowered = powered;
	}

	private void setStatus(Level level, WorkingStatus status) {
		BlockState state = getBlockState();
		if (state.hasProperty(TrackPlacerBlock.WORKING_STATUS)) {
			level.setBlockAndUpdate(getBlockPos(), state.setValue(TrackPlacerBlock.WORKING_STATUS, status));
		}
	}

	private List<IItemHandler> findNeighborItemHandlers(Level level) {
		List<IItemHandler> handlers = new ArrayList<>();
		for (Direction facing : Direction.values()) {
			BlockPos neighborPos = worldPosition.relative(facing);
			if (!level.isLoaded(neighborPos)) {
				continue;
			}
			IItemHandler handler =
					level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, facing.getOpposite());
			if (handler != null) {
				handlers.add(handler);
			}
		}
		return handlers;
	}

	private int countItems(List<IItemHandler> handlers, Item item, int max) {
		int found = 0;
		for (IItemHandler handler : handlers) {
			if (handler instanceof BottomlessItemHandler) {
				ItemStack supply = handler.getStackInSlot(0);
				if (!supply.isEmpty() && supply.is(item)) {
					return max;
				}
			}
			for (int slot = 0; slot < handler.getSlots(); slot++) {
				int want = max - found;
				if (want <= 0) {
					return found;
				}
				ItemStack stack = handler.extractItem(slot, want, true);
				if (stack.isEmpty() || !stack.is(item)) {
					continue;
				}
				found += stack.getCount();
			}
		}
		return found;
	}

	private void consumeItems(List<IItemHandler> handlers, Item item, int amount) {
		int remaining = amount;
		for (IItemHandler handler : handlers) {
			if (remaining <= 0) {
				return;
			}
			if (handler instanceof BottomlessItemHandler) {
				ItemStack supply = handler.getStackInSlot(0);
				if (!supply.isEmpty() && supply.is(item)) {
					return;
				}
			}
			for (int slot = 0; slot < handler.getSlots(); slot++) {
				if (remaining <= 0) {
					return;
				}
				ItemStack stack = handler.extractItem(slot, remaining, true);
				if (stack.isEmpty() || !stack.is(item)) {
					continue;
				}
				ItemStack extracted = handler.extractItem(slot, stack.getCount(), false);
				remaining -= extracted.getCount();
			}
		}
	}

	public void setTarget(int x, int y, int z, String dimension) {
		targetX = x;
		targetY = y + 1;
		targetZ = z;
		targetDimension = dimension == null ? "" : dimension;

		if (level != null && !level.isClientSide()) {
			BlockState state = getBlockState();
			if (state.hasProperty(TrackPlacerBlock.WORKING_STATUS)) {
				level.setBlockAndUpdate(getBlockPos(), state.setValue(TrackPlacerBlock.WORKING_STATUS, WorkingStatus.IDLE));
				refreshBuilder(level);
			}
		}
		notifyUpdate();
	}

	@Override
	public AABB getRenderBoundingBox() {
		return new AABB(getBlockPos()).inflate(2.5);
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putInt("targetX", targetX);
		tag.putInt("targetY", targetY);
		tag.putInt("targetZ", targetZ);
		tag.putString("targetDim", targetDimension == null ? "" : targetDimension);
		tag.putBoolean("TrackEnough", trackEnough);
		tag.putBoolean("PaveEnough", paveEnough);
		// 1.21.1 的 ItemStack.save 不允许空栈（会抛 Cannot encode empty ItemStack），
		// 模板为空时直接省略该键，read 一侧缺键即按空栈处理
		if (!placedTrack.isEmpty()) {
			tag.put("PlacedTrack", placedTrack.save(registries));
		}
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		targetX = tag.getInt("targetX");
		targetY = tag.getInt("targetY");
		targetZ = tag.getInt("targetZ");
		targetDimension = tag.getString("targetDim");
		trackEnough = !tag.contains("TrackEnough") || tag.getBoolean("TrackEnough");
		paveEnough = !tag.contains("PaveEnough") || tag.getBoolean("PaveEnough");

		ItemStack loaded = tag.contains("PlacedTrack")
				? ItemStack.parseOptional(registries, tag.getCompound("PlacedTrack"))
				: readLegacyTrack(tag, registries);
		placedTrack = isValidTrackFilter(loaded) ? loaded : ItemStack.EMPTY;

		if (turnRadiusBehaviour != null) {
			// 旧存档没有滑条的 NBT 时读到的值是 0，这里把越界值拉回滑条范围
			turnRadiusBehaviour.value = TrackPlacementUtils.clampTurnSpan(turnRadiusBehaviour.getValue());
		}
	}

	private static ItemStack readLegacyTrack(CompoundTag tag, HolderLookup.Provider registries) {
		if (!tag.contains("Track")) {
			return ItemStack.EMPTY;
		}
		Item item = FilterItemStack.of(registries, tag.getCompound("Track")).item().getItem();
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		if (level != null && builder == null) {
			refreshBuilder(level);
		}
		boolean valid = builder != null && builder.trackCount() > 0;

		if (valid) {
			ItemStack paveFilter = paveFilter2 != null ? paveFilter2.getFilter() : ItemStack.EMPTY;
			boolean shouldPave = isValidPaveFilter(paveFilter);

			gogglesLine(tooltip, Component.translatable("tooltip.railway_automation.track_placer.current_track",
							getPlacementTrackItem().getDescription())
					.withStyle(ChatFormatting.GRAY));

			if (getBlockState().getValue(TrackPlacerBlock.WORKING_STATUS) == WorkingStatus.ERROR) {
				gogglesLine(tooltip, Component.translatable("tooltip.railway_automation.track_placer.placement_failed")
						.withStyle(ChatFormatting.RED)
						.withStyle(ChatFormatting.BOLD));
				if (!trackEnough) {
					gogglesLine(tooltip, Component.translatable("tooltip.railway_automation.track_placer.track_lack")
							.withStyle(ChatFormatting.GRAY));
				}
				if (!paveEnough) {
					gogglesLine(tooltip, Component.translatable("tooltip.railway_automation.track_placer.pave_lack")
							.withStyle(ChatFormatting.GRAY));
				}
			} else {
				gogglesLine(tooltip, Component.translatable("tooltip.railway_automation.track_placer.valid")
						.withStyle(ChatFormatting.GREEN)
						.withStyle(ChatFormatting.BOLD)
						.append(Component.translatable("tooltip.railway_automation.track_placer.target_prefix")
								.withStyle(ChatFormatting.GRAY)));

				gogglesLine(tooltip, Component.translatable("tooltip.railway_automation.track_placer.coord", "X", targetX)
						.withStyle(ChatFormatting.GRAY));
				gogglesLine(tooltip, Component.translatable("tooltip.railway_automation.track_placer.coord", "Y", targetY)
						.withStyle(ChatFormatting.GRAY));
				gogglesLine(tooltip, Component.translatable("tooltip.railway_automation.track_placer.coord", "Z", targetZ)
						.withStyle(ChatFormatting.GRAY));

				MutableComponent required = Component.translatable("tooltip.railway_automation.track_placer.track",
								builder.trackCount())
						.withStyle(ChatFormatting.GRAY);
				if (shouldPave) {
					Item paveItem = paveFilter.getItem();
					int requiredPave = builder.pave(Block.byItem(paveItem));
					required.append(Component.translatable("tooltip.railway_automation.track_placer.and_pave",
							requiredPave,
							Component.translatable(paveItem.getDescriptionId())));
				}
				gogglesLine(tooltip, required);
			}
		} else {
			gogglesLine(tooltip, Component.translatable("tooltip.railway_automation.track_placer.invalid")
					.withStyle(ChatFormatting.RED)
					.withStyle(ChatFormatting.BOLD));
		}

		return true;
	}

	private void gogglesLine(List<Component> tooltip, Component line) {
		new LangBuilder(RailwayAutomation.MODID)
				.add(line)
				.forGoggles(tooltip);
	}
}
