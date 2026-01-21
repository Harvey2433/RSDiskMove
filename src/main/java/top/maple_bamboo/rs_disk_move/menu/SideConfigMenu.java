package top.maple_bamboo.rs_disk_move.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import top.maple_bamboo.rs_disk_move.RSDiskMove;
import top.maple_bamboo.rs_disk_move.block.DiskMoverBlockEntity;

public class SideConfigMenu extends AbstractContainerMenu {
    private final DiskMoverBlockEntity blockEntity;
    private final ContainerLevelAccess levelAccess;
    private final ContainerData data;

    public SideConfigMenu(int pContainerId, Inventory inv, BlockPos pos) {
        this(pContainerId, inv, pos, new SimpleContainerData(1));
    }

    public SideConfigMenu(int pContainerId, Inventory inv, BlockPos pos, ContainerData data) {
        super(RSDiskMove.SIDE_CONFIG_MENU.get(), pContainerId);
        this.levelAccess = ContainerLevelAccess.create(inv.player.level(), pos);
        this.data = data;

        BlockEntity be = inv.player.level().getBlockEntity(pos);
        if (be instanceof DiskMoverBlockEntity diskMover) {
            this.blockEntity = diskMover;
        } else {
            throw new IllegalStateException("Menu bound to wrong block entity");
        }
        addDataSlots(data);
    }

    public BlockPos getBlockPos() { return this.blockEntity.getBlockPos(); }
    public int getSideConfig() { return this.data.get(0); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(levelAccess, player, RSDiskMove.DISK_MOVER_BLOCK.get());
    }
}