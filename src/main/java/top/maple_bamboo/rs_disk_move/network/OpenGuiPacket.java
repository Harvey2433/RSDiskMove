package top.maple_bamboo.rs_disk_move.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkEvent;
import top.maple_bamboo.rs_disk_move.block.DiskMoverBlockEntity;
import top.maple_bamboo.rs_disk_move.menu.SideConfigMenu;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class OpenGuiPacket {
    private final BlockPos pos;
    private final int guiId; // 0=Main, 1=Config

    public OpenGuiPacket(BlockPos pos, int guiId) {
        this.pos = pos;
        this.guiId = guiId;
    }

    public OpenGuiPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.guiId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(guiId);
    }

    public static OpenGuiPacket decode(FriendlyByteBuf buf) {
        return new OpenGuiPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof DiskMoverBlockEntity diskMover) {
                if (guiId == 0) {
                    // 打开主界面 (DiskMoveMenu)
                    NetworkHooks.openScreen(player, diskMover, pos);
                } else if (guiId == 1) {
                    // 打开配置界面 (SideConfigMenu)
                    NetworkHooks.openScreen(player, new MenuProvider() {
                        @Override
                        public net.minecraft.network.chat.Component getDisplayName() {
                            return net.minecraft.network.chat.Component.literal("Configure Sides");
                        }
                        @Nullable
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new SideConfigMenu(id, inv, pos, diskMover.data);
                        }
                    }, pos);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}