package top.maple_bamboo.rs_disk_move.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import top.maple_bamboo.rs_disk_move.block.DiskMoverBlockEntity;

import java.util.function.Supplier;

public class MoveActionPacket {
    private final BlockPos pos;

    public MoveActionPacket(BlockPos pos) {
        this.pos = pos;
    }

    public MoveActionPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static MoveActionPacket decode(FriendlyByteBuf buf) {
        return new MoveActionPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            BlockEntity be = player.level().getBlockEntity(this.pos);
            if (be instanceof DiskMoverBlockEntity diskMover) {
                //
                // 核心修复：自动认主
                // 每次点击 Start/Stop，都更新机器的所有者为当前操作的玩家
                // 这样能保证发消息时一定能找到人
                diskMover.setOwner(player.getUUID());

                // 切换运行状态
                diskMover.toggleTransfer();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}