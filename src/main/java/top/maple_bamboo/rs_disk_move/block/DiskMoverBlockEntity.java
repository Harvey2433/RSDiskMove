package top.maple_bamboo.rs_disk_move.block;

import com.refinedmods.refinedstorage.api.IRSAPI;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDisk;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDiskProvider;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.IComparer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.maple_bamboo.rs_disk_move.RSDiskMove;
import top.maple_bamboo.rs_disk_move.menu.DiskMoveMenu;

import java.util.*;

public class DiskMoverBlockEntity extends BlockEntity implements MenuProvider {
    private final ItemStackHandler itemHandler = new ItemStackHandler(6) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            inventoryChanged = true;
        }
    };

    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.empty();
    private int sideConfig = 0b111111;
    private boolean isTransferring = false;

    // 显示数据
    private long displayTotal = 0;
    private long displayMoved = 0;

    // 内部阶段
    private int transferPhase = 0; // 0=Item, 1=Wait, 2=Fluid
    private long currentPhaseTotal = 0;
    private long currentPhaseMoved = 0;

    private long totalItemCount = 0;
    private long totalFluidCount = 0;

    private boolean inventoryChanged = false;
    private UUID ownerUUID;

    // 计时器
    private int itemStallTimer = 0;
    private int itemNoContainerTimer = 0;
    private int fluidStallTimer = 0;
    private int fluidNoContainerTimer = 0;
    private int transitionTimer = 0;
    private int autoResetTimer = 0;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> sideConfig;
                case 1 -> isTransferring ? 1 : 0;
                case 2 -> (int) (displayTotal & 0xFFFFFFFFL);
                case 3 -> (int) (displayTotal >>> 32);
                case 4 -> (int) (displayMoved & 0xFFFFFFFFL);
                case 5 -> (int) (displayMoved >>> 32);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0: sideConfig = value; break;
                case 1: isTransferring = (value == 1); break;
                case 2: displayTotal = (displayTotal & 0xFFFFFFFF00000000L) | (value & 0xFFFFFFFFL); break;
                case 3: displayTotal = (displayTotal & 0x00000000FFFFFFFFL) | ((long) value << 32); break;
                case 4: displayMoved = (displayMoved & 0xFFFFFFFF00000000L) | (value & 0xFFFFFFFFL); break;
                case 5: displayMoved = (displayMoved & 0x00000000FFFFFFFFL) | ((long) value << 32); break;
            }
        }

        @Override
        public int getCount() { return 6; }
    };

    public DiskMoverBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(RSDiskMove.DISK_MOVER_BE.get(), pPos, pBlockState);
    }

    public void setOwner(UUID uuid) {
        this.ownerUUID = uuid;
        setChanged();
    }

    // ================== Tick 逻辑 ==================
    public static void tick(Level level, BlockPos ignoredPos, BlockState ignoredState, DiskMoverBlockEntity be) {
        if (level.isClientSide) return;

        if (be.isTransferring) {
            be.displayTotal = be.currentPhaseTotal;
            be.displayMoved = be.currentPhaseMoved;
            if (be.displayMoved > be.displayTotal) be.displayTotal = be.displayMoved;

            switch (be.transferPhase) {
                case 0: be.tickItemPhase((ServerLevel) level); break;
                case 1: be.tickTransitionPhase((ServerLevel) level); break;
                case 2: be.tickFluidPhase((ServerLevel) level); break;
            }
        } else {
            if (be.autoResetTimer > 0) {
                be.autoResetTimer--;
                if (be.autoResetTimer == 0) be.resetAll();
            }
        }
    }

    // --- Phase 0: 物品阶段 ---
    private void tickItemPhase(ServerLevel level) {
        if (hasValidItemTargets(level)) {
            itemNoContainerTimer++;
            if (itemNoContainerTimer > 40) { // 2s
                pauseTransfer(Component.translatable("message.rs_disk_move.paused_no_item_container"));
            }
            return;
        } else {
            itemNoContainerTimer = 0;
        }

        long moved = transferItems(level);
        this.currentPhaseMoved += moved;

        if (moved == 0) {
            if (!hasItemsLeft(level)) {
                // 物品完成
                if (this.totalFluidCount > 0) {
                    notifyOwner(Component.translatable("message.rs_disk_move.complete_item_start_fluid"));
                    this.transferPhase = 1;
                    this.transitionTimer = 20;
                    setChanged();
                } else {
                    finishTransfer(Component.translatable("message.rs_disk_move.complete_all"));
                }
            } else {
                itemStallTimer++;
                if (itemStallTimer >= 200) { // 10s
                    pauseTransfer(Component.translatable("message.rs_disk_move.paused_item_target_full"));
                }
            }
        } else {
            itemStallTimer = 0;
            setChanged();
        }
    }

    // --- Phase 1: 过渡阶段 ---
    private void tickTransitionPhase(ServerLevel ignoredLevel) {
        if (transitionTimer > 0) {
            transitionTimer--;
            return;
        }
        long fluidTotal = calculateFluidTotal();
        if (fluidTotal > 0) {
            this.transferPhase = 2;
            this.currentPhaseTotal = fluidTotal;
            this.currentPhaseMoved = 0;
            this.displayTotal = fluidTotal;
            this.displayMoved = 0;
            this.fluidStallTimer = 0;
            this.fluidNoContainerTimer = 0;
            setChanged();
        } else {
            finishTransfer(Component.translatable("message.rs_disk_move.complete_item"));
        }
    }

    // --- Phase 2: 流体阶段 ---
    private void tickFluidPhase(ServerLevel level) {
        FluidStack testFluid = getFirstFluidStack(level);
        if (testFluid == null || testFluid.isEmpty()) {
            finishTransfer(Component.translatable("message.rs_disk_move.complete_all"));
            return;
        }

        int status = checkFluidTargets(level, testFluid);

        if (status == 2) {
            long moved = transferFluids(level);
            this.currentPhaseMoved += moved;

            fluidNoContainerTimer = 0;
            fluidStallTimer = 0;

            if (moved == 0) fluidStallTimer++;
            setChanged();
        }
        else if (status == 1) {
            fluidNoContainerTimer = 0;
            fluidStallTimer++;
            if (fluidStallTimer >= 100) {
                pauseTransfer(Component.translatable("message.rs_disk_move.paused_fluid_target_full"));
            }
        }
        else {
            fluidStallTimer = 0;
            fluidNoContainerTimer++;
            if (fluidNoContainerTimer > 40) {
                pauseTransfer(Component.translatable("message.rs_disk_move.paused_no_fluid_container"));
            }
        }
    }

    private int checkFluidTargets(ServerLevel level, FluidStack stack) {
        boolean foundFullHandler = false;

        for (Direction dir : Direction.values()) {
            if (!isSideEnabled(dir)) continue;

            BlockEntity be = level.getBlockEntity(this.worldPosition.relative(dir));
            if (be == null) continue;

            LazyOptional<IFluidHandler> cap = be.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite());
            if (!cap.isPresent()) continue;

            IFluidHandler handler = cap.orElse(null);

            int simulated = handler.fill(stack, IFluidHandler.FluidAction.SIMULATE);
            if (simulated > 0) return 2;

            if (handler.getTanks() > 0) {
                for (int i = 0; i < handler.getTanks(); i++) {
                    if (handler.getFluidInTank(i).getAmount() >= handler.getTankCapacity(i)) {
                        foundFullHandler = true;
                    }
                }
            }
        }

        if (foundFullHandler) return 1;
        return 0;
    }

    // ================== 控制逻辑 ==================

    public void toggleTransfer() {
        if (this.level == null || this.level.isClientSide) return;

        if (this.autoResetTimer > 0) {
            this.autoResetTimer = 0;
            resetAll();
            this.isTransferring = false;
            return;
        }

        if (this.isTransferring) {
            return;
        }

        if (this.sideConfig == 0) {
            notifyOwner(Component.translatable("message.rs_disk_move.error_no_output"));
            return;
        }

        boolean isNewTask = this.inventoryChanged || (this.displayTotal == 0);

        if (isNewTask) {
            calculateSeparatedTotals(); // 计算时已去重

            if (this.totalItemCount == 0 && this.totalFluidCount == 0) {
                notifyOwner(Component.translatable("message.rs_disk_move.error_empty_disk"));
                return;
            }

            this.inventoryChanged = false;
            this.currentPhaseMoved = 0;

            if (this.totalItemCount > 0) {
                this.transferPhase = 0;
                this.currentPhaseTotal = this.totalItemCount;
                if (hasValidItemTargets((ServerLevel) level)) {
                    notifyOwner(Component.translatable("message.rs_disk_move.error_no_item_container"));
                    return;
                }
                notifyOwner(Component.translatable("message.rs_disk_move.start_item"));
            } else {
                notifyOwner(Component.translatable("message.rs_disk_move.start_fluid"));
                this.transferPhase = 2;
                this.currentPhaseTotal = this.totalFluidCount;

                FluidStack testFluid = getFirstFluidStack((ServerLevel) level);
                if (testFluid != null) {
                    int status = checkFluidTargets((ServerLevel) level, testFluid);
                    if (status == 0) {
                        notifyOwner(Component.translatable("message.rs_disk_move.error_no_fluid_container"));
                        return;
                    }
                }
            }
        }

        this.isTransferring = true;
        this.itemStallTimer = 0;
        this.itemNoContainerTimer = 0;
        this.fluidStallTimer = 0;
        this.fluidNoContainerTimer = 0;
        setChanged();
    }

    private void pauseTransfer(Component msg) {
        this.isTransferring = false;
        notifyOwner(msg);
        setChanged();
    }

    private void finishTransfer(Component msg) {
        this.isTransferring = false;
        this.currentPhaseMoved = this.currentPhaseTotal;
        this.displayMoved = this.displayTotal;
        notifyOwner(msg);
        this.autoResetTimer = 60;
        setChanged();
    }

    private void resetAll() {
        this.isTransferring = false;
        this.displayTotal = 0;
        this.displayMoved = 0;
        this.currentPhaseTotal = 0;
        this.currentPhaseMoved = 0;
        this.transferPhase = 0;
        this.autoResetTimer = 0;
        setChanged();
    }

    private void notifyOwner(Component msg) {
        if (ownerUUID != null && level instanceof ServerLevel serverLevel) {
            net.minecraft.server.MinecraftServer server = serverLevel.getServer();
            Player player = server.getPlayerList().getPlayer(ownerUUID);
            if (player != null) {
                player.sendSystemMessage(msg);
            }
        }
    }

    // ================== 计算辅助方法 (核心去重) ==================

    // 核心修改：使用 Set<UUID> 记录已处理的磁盘 ID，防止重复统计
    private void calculateSeparatedTotals() {
        this.totalItemCount = 0;
        this.totalFluidCount = 0;
        IRSAPI api = RSDiskMove.RS_API;
        if (api == null || level == null) return;

        Set<UUID> processedIds = new HashSet<>(); // 去重集合

        for (int i = 0; i < 6; i++) {
            ItemStack diskStack = itemHandler.getStackInSlot(i);
            if (!diskStack.isEmpty() && diskStack.getItem() instanceof IStorageDiskProvider provider && provider.isValid(diskStack)) {
                UUID diskId = provider.getId(diskStack);
                // 核心去重逻辑：如果这个 ID 已经算过了，直接跳过
                if (processedIds.contains(diskId)) continue;
                processedIds.add(diskId);

                try {
                    IStorageDisk disk = api.getStorageDiskManager((ServerLevel) level).get(diskId);
                    if (disk != null) {
                        for (Object obj : disk.getStacks()) {
                            if (obj instanceof ItemStack stack) this.totalItemCount += stack.getCount();
                            else if (obj instanceof FluidStack stack) this.totalFluidCount += stack.getAmount();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // 计算流体总量 (也需要去重，防止流体阶段计算错误)
    private long calculateFluidTotal() {
        long amount = 0;
        IRSAPI api = RSDiskMove.RS_API;
        if (api == null || level == null) return 0;

        Set<UUID> processedIds = new HashSet<>();

        for (int i = 0; i < 6; i++) {
            ItemStack diskStack = itemHandler.getStackInSlot(i);
            if (!diskStack.isEmpty() && diskStack.getItem() instanceof IStorageDiskProvider provider && provider.isValid(diskStack)) {
                UUID diskId = provider.getId(diskStack);
                if (processedIds.contains(diskId)) continue;
                processedIds.add(diskId);

                try {
                    IStorageDisk disk = api.getStorageDiskManager((ServerLevel) level).get(diskId);
                    if (disk != null) {
                        for (Object obj : disk.getStacks()) {
                            if (obj instanceof FluidStack stack) amount += stack.getAmount();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return amount;
    }

    private boolean hasValidItemTargets(ServerLevel level) {
        for (Direction dir : Direction.values()) {
            if (isSideEnabled(dir)) {
                BlockEntity targetBe = level.getBlockEntity(this.worldPosition.relative(dir));
                if (targetBe != null && targetBe.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite()).isPresent()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasItemsLeft(ServerLevel level) { return checkDiskContent(level, ItemStack.class); }
    private boolean hasFluidsLeft(ServerLevel level) { return checkDiskContent(level, FluidStack.class); }

    private boolean checkDiskContent(ServerLevel level, Class<?> type) {
        IRSAPI api = RSDiskMove.RS_API;
        if (api == null) return false;
        // 这里的检查不需要去重，只要有一个盘里有东西就返回 true
        for (int i = 0; i < 6; i++) {
            ItemStack diskStack = itemHandler.getStackInSlot(i);
            if (!diskStack.isEmpty() && diskStack.getItem() instanceof IStorageDiskProvider provider && provider.isValid(diskStack)) {
                try {
                    IStorageDisk disk = api.getStorageDiskManager(level).get(provider.getId(diskStack));
                    if (disk != null) {
                        for (Object obj : disk.getStacks()) {
                            if (type.isInstance(obj)) return true;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    private FluidStack getFirstFluidStack(ServerLevel level) {
        IRSAPI api = RSDiskMove.RS_API;
        if (api == null) return null;
        for (int i = 0; i < 6; i++) {
            ItemStack diskStack = itemHandler.getStackInSlot(i);
            if (!diskStack.isEmpty() && diskStack.getItem() instanceof IStorageDiskProvider provider && provider.isValid(diskStack)) {
                try {
                    IStorageDisk disk = api.getStorageDiskManager(level).get(provider.getId(diskStack));
                    if (disk != null) {
                        for (Object obj : disk.getStacks()) {
                            if (obj instanceof FluidStack stack) return stack;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    // 转移逻辑不需要去重，因为 extract 操作是原子性的，从A盘取出后B盘自然就没了
    private long transferItems(ServerLevel level) {
        IRSAPI api = RSDiskMove.RS_API;
        if (api == null) return 0;
        List<IItemHandler> targets = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            if (isSideEnabled(dir)) {
                BlockEntity targetBe = level.getBlockEntity(this.worldPosition.relative(dir));
                if (targetBe != null) targetBe.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite()).ifPresent(targets::add);
            }
        }
        if (targets.isEmpty()) return 0;

        long movedCount = 0;
        for (int i = 0; i < 6; i++) {
            ItemStack diskStack = itemHandler.getStackInSlot(i);
            if (!diskStack.isEmpty() && diskStack.getItem() instanceof IStorageDiskProvider provider && provider.isValid(diskStack)) {
                try {
                    IStorageDisk disk = api.getStorageDiskManager(level).get(provider.getId(diskStack));
                    if (disk == null) continue;
                    Collection stacks = disk.getStacks();
                    for (Object obj : new ArrayList<>(stacks)) {
                        if (!(obj instanceof ItemStack stackInDisk) || stackInDisk.isEmpty()) continue;
                        ItemStack toInsert = ItemHandlerHelper.copyStackWithSize(stackInDisk, stackInDisk.getCount());
                        for (IItemHandler target : targets) {
                            if (toInsert.isEmpty()) break;
                            ItemStack remaining = ItemHandlerHelper.insertItem(target, toInsert, false);
                            int inserted = toInsert.getCount() - remaining.getCount();
                            if (inserted > 0) {
                                ItemStack toExtract = stackInDisk.copy();
                                toExtract.setCount(inserted);
                                disk.extract(toExtract, inserted, IComparer.COMPARE_NBT, Action.PERFORM);
                                movedCount += inserted;
                            }
                            toInsert = remaining;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return movedCount;
    }

    private long transferFluids(ServerLevel level) {
        IRSAPI api = RSDiskMove.RS_API;
        if (api == null) return 0;
        List<IFluidHandler> targets = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            if (isSideEnabled(dir)) {
                BlockEntity targetBe = level.getBlockEntity(this.worldPosition.relative(dir));
                if (targetBe != null) targetBe.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite()).ifPresent(targets::add);
            }
        }
        if (targets.isEmpty()) return 0;

        long movedCount = 0;
        for (int i = 0; i < 6; i++) {
            ItemStack diskStack = itemHandler.getStackInSlot(i);
            if (!diskStack.isEmpty() && diskStack.getItem() instanceof IStorageDiskProvider provider && provider.isValid(diskStack)) {
                try {
                    IStorageDisk disk = api.getStorageDiskManager(level).get(provider.getId(diskStack));
                    if (disk == null) continue;
                    Collection stacks = disk.getStacks();
                    for (Object obj : new ArrayList<>(stacks)) {
                        if (!(obj instanceof FluidStack stackInDisk) || stackInDisk.isEmpty()) continue;
                        FluidStack toInsert = stackInDisk.copy();
                        for (IFluidHandler target : targets) {
                            if (toInsert.isEmpty()) break;
                            int filled = target.fill(toInsert, IFluidHandler.FluidAction.EXECUTE);
                            if (filled > 0) {
                                FluidStack toExtract = stackInDisk.copy();
                                toExtract.setAmount(filled);
                                disk.extract(toExtract, filled, IComparer.COMPARE_NBT, Action.PERFORM);
                                movedCount += filled;
                                toInsert.shrink(filled);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return movedCount;
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return lazyItemHandler.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        lazyItemHandler = LazyOptional.of(() -> itemHandler);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.put("inventory", itemHandler.serializeNBT());
        pTag.putInt("SideConfig", sideConfig);
        pTag.putBoolean("IsTransferring", isTransferring);
        pTag.putLong("DisplayTotal", displayTotal);
        pTag.putLong("DisplayMoved", displayMoved);
        pTag.putInt("TransferPhase", transferPhase);
        pTag.putLong("CurrentPhaseTotal", currentPhaseTotal);
        pTag.putLong("CurrentPhaseMoved", currentPhaseMoved);
        pTag.putLong("TotalItemCount", totalItemCount);
        pTag.putLong("TotalFluidCount", totalFluidCount);
        if (ownerUUID != null) pTag.putUUID("Owner", ownerUUID);
        pTag.putInt("ItemStallTimer", itemStallTimer);
        pTag.putInt("ItemNoContainer", itemNoContainerTimer);
        pTag.putInt("FluidStallTimer", fluidStallTimer);
        pTag.putInt("FluidNoContainer", fluidNoContainerTimer);
        pTag.putInt("TransitionTimer", transitionTimer);
        pTag.putInt("AutoResetTimer", autoResetTimer);
        pTag.putBoolean("InvChanged", inventoryChanged);
        super.saveAdditional(pTag);
    }

    @Override
    public void load(@NotNull CompoundTag pTag) {
        super.load(pTag);
        itemHandler.deserializeNBT(pTag.getCompound("inventory"));
        if (pTag.contains("SideConfig")) sideConfig = pTag.getInt("SideConfig");
        if (pTag.contains("IsTransferring")) isTransferring = pTag.getBoolean("IsTransferring");
        if (pTag.contains("DisplayTotal")) displayTotal = pTag.getLong("DisplayTotal");
        if (pTag.contains("DisplayMoved")) displayMoved = pTag.getLong("DisplayMoved");
        if (pTag.contains("TransferPhase")) transferPhase = pTag.getInt("TransferPhase");
        if (pTag.contains("CurrentPhaseTotal")) currentPhaseTotal = pTag.getLong("CurrentPhaseTotal");
        if (pTag.contains("CurrentPhaseMoved")) currentPhaseMoved = pTag.getLong("CurrentPhaseMoved");
        if (pTag.contains("TotalItemCount")) totalItemCount = pTag.getLong("TotalItemCount");
        if (pTag.contains("TotalFluidCount")) totalFluidCount = pTag.getLong("TotalFluidCount");
        if (pTag.contains("Owner")) ownerUUID = pTag.getUUID("Owner");
        if (pTag.contains("ItemStallTimer")) itemStallTimer = pTag.getInt("ItemStallTimer");
        if (pTag.contains("FluidStallTimer")) fluidStallTimer = pTag.getInt("FluidStallTimer");
        if (pTag.contains("ItemNoContainer")) itemNoContainerTimer = pTag.getInt("ItemNoContainer");
        if (pTag.contains("FluidNoContainer")) fluidNoContainerTimer = pTag.getInt("FluidNoContainer");
        if (pTag.contains("TransitionTimer")) transitionTimer = pTag.getInt("TransitionTimer");
        if (pTag.contains("AutoResetTimer")) autoResetTimer = pTag.getInt("AutoResetTimer");
        if (pTag.contains("InvChanged")) inventoryChanged = pTag.getBoolean("InvChanged");
    }

    public void drops() {
        SimpleContainer inventory = new SimpleContainer(itemHandler.getSlots());
        for (int i = 0; i < itemHandler.getSlots(); i++) inventory.setItem(i, itemHandler.getStackInSlot(i));
        Containers.dropContents(Objects.requireNonNull(this.level), this.worldPosition, inventory);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.rs_disk_move.disk_mover");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, @NotNull Inventory pPlayerInventory, @NotNull Player pPlayer) {
        return new DiskMoveMenu(pContainerId, pPlayerInventory, this.worldPosition, this.data);
    }

    public ItemStackHandler getItemHandler() { return itemHandler; }
    public boolean isSideEnabled(Direction dir) { return (sideConfig & (1 << dir.ordinal())) != 0; }

    public void toggleSide(Direction dir) {
        if (isTransferring) {
            return;
        }
        sideConfig ^= (1 << dir.ordinal());
        setChanged();
    }

    public void disableAllSides() {
        if (isTransferring) {
            return;
        }
        sideConfig = 0;
        setChanged();
    }
}