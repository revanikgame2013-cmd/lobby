package dev.maniaclobby;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Состояние лобби. Порядок добавления = порядок входа, первый и есть лидер. */
public final class Lobby {

    public static final class Member {
        public boolean ready;
        public Role role = Role.SURVIVOR;
    }

    private final LinkedHashMap<UUID, Member> members = new LinkedHashMap<>();

    public Member add(UUID id) {
        return members.computeIfAbsent(id, k -> new Member());
    }

    public boolean contains(UUID id) {
        return members.containsKey(id);
    }

    public Member get(UUID id) {
        return members.get(id);
    }

    public void remove(UUID id) {
        members.remove(id);
    }

    public void clear() {
        members.clear();
    }

    public int size() {
        return members.size();
    }

    public Map<UUID, Member> members() {
        return Collections.unmodifiableMap(members);
    }

    public UUID leader() {
        return members.isEmpty() ? null : members.keySet().iterator().next();
    }

    public boolean isLeader(UUID id) {
        return id.equals(leader());
    }

    public int count(Role role) {
        int n = 0;
        for (Member m : members.values()) {
            if (m.role == role) n++;
        }
        return n;
    }
}
