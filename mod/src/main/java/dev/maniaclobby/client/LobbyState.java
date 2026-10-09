package dev.maniaclobby.client;

import java.util.List;
import java.util.UUID;

/** Снимок состояния лобби, присланный сервером. */
public record LobbyState(UUID leader, List<Member> members) {

    public record Member(UUID id, String name, boolean ready, boolean maniac) {}

    public Member find(UUID id) {
        for (Member m : members) {
            if (m.id().equals(id)) return m;
        }
        return null;
    }

    public int maniacCount() {
        int n = 0;
        for (Member m : members) {
            if (m.maniac()) n++;
        }
        return n;
    }
}
