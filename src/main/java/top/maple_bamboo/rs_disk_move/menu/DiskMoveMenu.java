package top.maple_bamboo.rs_disk_move.menu;

import com.refinedmods.refinedstorage.api.storage.disk.IStorageDiskProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import top.maple_bamboo.rs_disk_move.RSDiskMove;
import top.maple_bamboo.rs_disk_move.block.DiskMoverBlockEntity;

public class DiskMoveMenu extends AbstractContainerMenu {
    private final DiskMoverBlockEntity blockEntity;
    private final ContainerLevelAccess levelAccess;
    private final ContainerData data;

    public DiskMoveMenu(int pContainerId, Inventory inv, BlockPos pos) {
        this(pContainerId, inv, pos, new SimpleContainerData(6));
    }

    public DiskMoveMenu(int pContainerId, Inventory inv, BlockPos pos, ContainerData data) {
        super(RSDiskMove.DISK_MOVER_MENU.get(), pContainerId);
        this.levelAccess = ContainerLevelAccess.create(inv.player.level(), pos);
        this.data = data;

        BlockEntity be = inv.player.level().getBlockEntity(pos);
        if (be instanceof DiskMoverBlockEntity diskMover) {
            this.blockEntity = diskMover;
        } else {
            throw new IllegalStateException("Menu bound to wrong block entity at " + pos);
        }

        // --- 机器槽位 (0 - 5) ---
        for (int i = 0; i < 6; i++) {
            this.addSlot(new SlotItemHandler(this.blockEntity.getItemHandler(), i, 28, 25 + i * 18) {
                // 1. 禁止拿取逻辑：机器运行时锁死
                @Override
                public boolean mayPickup(Player playerIn) {
                    return data.get(1) == 0 && super.mayPickup(playerIn);
                }

                // 2. 限制输入逻辑：机器停止 + 必须是 RS 磁盘
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    boolean isMachineStopped = data.get(1) == 0;
                    // 使用 RS API 判断是否为磁盘 (兼容物品盘、流体盘、创造盘等)
                    boolean isRsDisk = stack.getItem() instanceof IStorageDiskProvider;

                    return isMachineStopped && isRsDisk && super.mayPlace(stack);
                }
            });
        }

        // --- 玩家背包 (6 - 32) ---
        int startX = 12;
        int invY = 174;
        int hotbarY = 232;

        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(inv, l + i * 9 + 9, startX + l * 18, invY + i * 18));
            }
        }

        // --- 玩家快捷栏 (33 - 41) ---
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(inv, i, startX + i * 18, hotbarY));
        }

        addDataSlots(data);
    }

    public BlockPos getBlockPos() { return this.blockEntity.getBlockPos(); }
    public ContainerData getData() { return this.data; }

    public long getTotalItems() {
        return ((long) data.get(3) << 32) | ((long) data.get(2) & 0xFFFFFFFFL);
    }

    public long getMovedItems() {
        return ((long) data.get(5) << 32) | ((long) data.get(4) & 0xFFFFFFFFL);
    }

    // --- 3. 快速移动逻辑 (Shift + 左键) ---
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player playerIn, int index) {
        ItemStack sourceStack = ItemStack.EMPTY;
        Slot sourceSlot = this.slots.get(index);

        if (sourceSlot.hasItem()) {
            ItemStack stackInSlot = sourceSlot.getItem();
            sourceStack = stackInSlot.copy();

            // 如果点击的是机器槽位 (0-5) -> 移入玩家背包
            if (index < 6) {
                // 参数: stack, startId, endId, reverse(true=优先放快捷栏)
                if (!this.moveItemStackTo(stackInSlot, 6, 42, true)) {
                    return ItemStack.EMPTY;
                }
            }
            // 如果点击的是玩家背包 (6-41) -> 移入机器槽位
            else {
                // moveItemStackTo 会自动调用我们上面重写的 mayPlace
                // 所以如果不是磁盘，或者机器正在运行，这里会自动失败，不用额外写判断
                if (!this.moveItemStackTo(stackInSlot, 0, 6, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stackInSlot.isEmpty()) {
                sourceSlot.set(ItemStack.EMPTY);
            } else {
                sourceSlot.setChanged();
            }

            if (stackInSlot.getCount() == sourceStack.getCount()) {
                return ItemStack.EMPTY;
            }

            sourceSlot.onTake(playerIn, stackInSlot);
        }
        return sourceStack;
    }

    @Override
    public boolean stillValid(@NotNull Player pPlayer) {
        return stillValid(levelAccess, pPlayer, RSDiskMove.DISK_MOVER_BLOCK.get());
    }
}