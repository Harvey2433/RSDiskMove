package top.maple_bamboo.rs_disk_move.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import top.maple_bamboo.rs_disk_move.block.DiskMoverBlockEntity;

import java.util.function.Supplier;

public class SideTogglePacket {
    private final BlockPos pos;
    private final int directionOrdinal; // -1 表示 Disable All

    // 原构造函数 (用于单个方向)
    public SideTogglePacket(BlockPos pos, Direction dir) {
        this.pos = pos;
        this.directionOrdinal = dir.ordinal();
    }

    // 新增构造函数 (用于特殊指令)
    public SideTogglePacket(BlockPos pos, int specialId) {
        this.pos = pos;
        this.directionOrdinal = specialId;
    }

    public SideTogglePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.directionOrdinal = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(directionOrdinal);
    }

    public static SideTogglePacket decode(FriendlyByteBuf buf) {
        return new SideTogglePacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof DiskMoverBlockEntity diskMover) {
                // 如果是 -1，执行全部禁用
                if (directionOrdinal == -1) {
                    diskMover.disableAllSides();
                } else if (directionOrdinal >= 0 && directionOrdinal < Direction.values().length) {
                    diskMover.toggleSide(Direction.values()[directionOrdinal]);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}