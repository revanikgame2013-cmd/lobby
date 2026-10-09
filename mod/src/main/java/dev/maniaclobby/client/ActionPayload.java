package dev.maniaclobby.client;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер: одно число-действие (0 hello, 1 открыть, 2 готов, 3 роль, 4 старт, 5 назад). */
public record ActionPayload(int action) implements CustomPayload {

    public static final Id<ActionPayload> ID = new Id<>(Identifier.of("maniaclobby", "main"));

    public static final PacketCodec<PacketByteBuf, ActionPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeByte(value.action()),
            buf -> new ActionPayload(buf.readByte()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
