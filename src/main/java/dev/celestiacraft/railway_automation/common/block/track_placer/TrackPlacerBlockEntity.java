package dev.celestiacraft.railway_automation.common.block.track_placer;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllTags;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.crate.BottomlessItemHandler;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.celestiacraft.railway_automation.RailwayAutomation;
import dev.celestiacraft.railway_automation.common.block.state.properties.track_placer.WorkingStatus;
import dev.celestiacraft.railway_automation.common.event.TrackPlacingHandler;
import lombok.Getter;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
public class TrackPlacerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
	public TrackPlacerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	private TrackPlacerPaveFilterBehaviour filtering;
	private TrackPlacerPaveFilterBehaviour2 paveFilter2;

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		filtering = new TrackPlacerPaveFilterBehaviour(this, new TrackPlacerPaveFilterSlot());
		filtering.withPredicate(TrackPlacerBlockEntity::isValidTrackFilter);
		behaviours.add(filtering);

		paveFilter2 = new TrackPlacerPaveFilterBehaviour2(this, new TrackPlacerPaveFilterSlot(11));
		paveFilter2.withPredicate(TrackPlacerBlockEntity::isValidPaveFilter);
		behaviours.add(paveFilter2);

		behaviours.add(new DirectBeltInputBehaviour(this));
	}

	/**
	 * 轨道槽只接受轨道方块，轨道物品同时决定铺设的材质
	 */
	public static boolean isValidTrackFilter(ItemStack stack) {
		return !stack.isEmpty()
				&& stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof ITrackBlock;
	}

	/**
	 * 地基槽只接受普通方块：轨道由轨道槽决定，不能拿来当铺路材料
	 * <p>
	 * Create 的 {@code invalid_for_track_paving} 标签只排除了支架，所以这里额外排除所有实现
	 * {@link ITrackBlock} 的方块——Create 本体的轨道与汽鸣铁道的宽轨、窄轨、单轨、幻渺轨道都在此列。
	 */
	public static boolean isValidPaveFilter(ItemStack stack) {
		return !stack.isEmpty()
				&& stack.getItem() instanceof BlockItem blockItem
				&& !(blockItem.getBlock() instanceof ITrackBlock)
				&& !AllTags.AllItemTags.INVALID_FOR_TRACK_PAVING.matches(stack);
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
		builder = TrackPlacingHandler.builder(level, getBlockPos(), offset, direction);
		if (!level.isClientSide()) {
			refreshEnoughState(level);
		}
	}

	private void refreshEnoughState(Level level) {
		if (builder == null) {
			return;
		}

		ItemStack trackFilter = filtering != null
				? filtering.getFilter()
				: ItemStack.EMPTY;

		ItemStack paveFilter = paveFilter2 != null
				? paveFilter2.getFilter()
				: ItemStack.EMPTY;

		Item trackItem = AllBlocks.TRACK.get().asItem();
		TrackMaterial trackMaterial = TrackMaterial.ANDESITE;
		if (!trackFilter.isEmpty()
				&& trackFilter.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof ITrackBlock trackBlock) {
			trackItem = trackFilter.getItem();
			trackMaterial = trackBlock.getMaterial();
		}
		builder.material(trackMaterial);

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
			ItemStack trackFilter = filtering != null ? filtering.getFilter() : ItemStack.EMPTY;
			ItemStack paveFilter = paveFilter2 != null ? paveFilter2.getFilter() : ItemStack.EMPTY;

			Item trackItem = AllBlocks.TRACK.get().asItem();
			TrackMaterial trackMaterial = TrackMaterial.ANDESITE;
			if (!trackFilter.isEmpty() && trackFilter.getItem() instanceof BlockItem blockItem
					&& blockItem.getBlock() instanceof ITrackBlock trackBlock) {
				trackItem = trackFilter.getItem();
				trackMaterial = trackBlock.getMaterial();
			}
			placement.material(trackMaterial);

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
			BlockEntity blockEntity = level.getBlockEntity(neighborPos);
			if (blockEntity == null) {
				continue;
			}
			LazyOptional<IItemHandler> capability =
					blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, facing.getOpposite());
			if (capability.isPresent()) {
				handlers.add(capability.orElse(EmptyHandler.INSTANCE));
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
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.putInt("targetX", targetX);
		tag.putInt("targetY", targetY);
		tag.putInt("targetZ", targetZ);
		tag.putString("targetDim", targetDimension == null ? "" : targetDimension);
		tag.putBoolean("TrackEnough", trackEnough);
		tag.putBoolean("PaveEnough", paveEnough);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		targetX = tag.getInt("targetX");
		targetY = tag.getInt("targetY");
		targetZ = tag.getInt("targetZ");
		targetDimension = tag.getString("targetDim");
		trackEnough = !tag.contains("TrackEnough") || tag.getBoolean("TrackEnough");
		paveEnough = !tag.contains("PaveEnough") || tag.getBoolean("PaveEnough");
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