package dev.maniaclobby;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Бинарный формат пакетов плагин -> мод (канал maniaclobby:main).
 * 0                      — закрыть экран
 * 1, open, leader, n, [uuid, name, ready, maniac]*n — состояние лобби
 */
final class Net {

    private Net() {}

    static byte[] close() {
        return new byte[] {0};
    }

    static byte[] state(Lobby lobby, boolean open) {
        UUID leader = lobby.leader();
        if (leader == null) return close();
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(1);
            out.writeBoolean(open);
            out.writeLong(leader.getMostSignificantBits());
            out.writeLong(leader.getLeastSignificantBits());

            int count = 0;
            for (UUID id : lobby.members().keySet()) {
                if (Bukkit.getPlayer(id) != null) count++;
            }
            out.writeInt(count);

            for (var e : lobby.members().entrySet()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p == null) continue;
                out.writeLong(e.getKey().getMostSignificantBits());
                out.writeLong(e.getKey().getLeastSignificantBits());
                byte[] name = p.getName().getBytes(StandardCharsets.UTF_8);
                out.writeInt(name.length);
                out.write(name);
                out.writeBoolean(e.getValue().ready);
                out.writeBoolean(e.getValue().role == Role.MANIAC);
            }
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
