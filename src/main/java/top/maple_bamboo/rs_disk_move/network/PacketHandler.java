package top.maple_bamboo.rs_disk_move.network;

import top.maple_bamboo.rs_disk_move.RSDiskMove;

public class PacketHandler {
    public static void register() {
        int id = 0;
        RSDiskMove.NETWORK.registerMessage(id++, MoveActionPacket.class, MoveActionPacket::encode, MoveActionPacket::decode, MoveActionPacket::handle);
        RSDiskMove.NETWORK.registerMessage(id++, SideTogglePacket.class, SideTogglePacket::encode, SideTogglePacket::decode, SideTogglePacket::handle);
        RSDiskMove.NETWORK.registerMessage(id, OpenGuiPacket.class, OpenGuiPacket::encode, OpenGuiPacket::decode, OpenGuiPacket::handle);
    }

    public static void sendToServer(Object msg) {
        RSDiskMove.NETWORK.sendToServer(msg);
    }
}