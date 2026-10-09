package dev.maniaclobby.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Сервер -> клиент: закрыть экран (close) или состояние лобби (open = открыть экран). */
public record StatePayload(boolean close, boolean open, LobbyState state) implements CustomPayload {

    public static final Id<StatePayload> ID = new Id<>(Identifier.of("maniaclobby", "main"));

    public static final PacketCodec<PacketByteBuf, StatePayload> CODEC =
            PacketCodec.of(StatePayload::encode, StatePayload::decode);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void encode(StatePayload p, PacketByteBuf buf) {
        if (p.close()) {
            buf.writeByte(0);
            return;
        }
        buf.writeByte(1);
        buf.writeBoolean(p.open());
        buf.writeLong(p.state().leader().getMostSignificantBits());
        buf.writeLong(p.state().leader().getLeastSignificantBits());
        buf.writeInt(p.state().members().size());
        for (LobbyState.Member m : p.state().members()) {
            buf.writeLong(m.id().getMostSignificantBits());
            buf.writeLong(m.id().getLeastSignificantBits());
            byte[] name = m.name().getBytes(StandardCharsets.UTF_8);
            buf.writeInt(name.length);
            buf.writeBytes(name);
            buf.writeBoolean(m.ready());
            buf.writeBoolean(m.maniac());
        }
    }

    private static StatePayload decode(PacketByteBuf buf) {
        byte kind = buf.readByte();
        if (kind == 0) {
            return new StatePayload(true, false, null);
        }
        boolean open = buf.readBoolean();
        long lm = buf.readLong();
        long ll = buf.readLong();
        UUID leader = new UUID(lm, ll);
        int n = buf.readInt();
        List<LobbyState.Member> members = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long mm = buf.readLong();
            long ml = buf.readLong();
            int len = buf.readInt();
            byte[] raw = new byte[len];
            buf.readBytes(raw);
            boolean ready = buf.readBoolean();
            boolean maniac = buf.readBoolean();
            members.add(new LobbyState.Member(new UUID(mm, ml), new String(raw, StandardCharsets.UTF_8), ready, maniac));
        }
        return new StatePayload(false, open, new LobbyState(leader, members));
    }
}
