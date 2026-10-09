package dev.maniaclobby;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Вызывается, когда лидер нажал "Начать". Сюда можно подписаться из своего
 * плагина игры: participants — те, кто был готов (с ролями), skipped — те, кто нет.
 */
public final class LobbyGameStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Map<Player, Role> participants;
    private final List<Player> skipped;

    public LobbyGameStartEvent(Map<Player, Role> participants, List<Player> skipped) {
        this.participants = Collections.unmodifiableMap(participants);
        this.skipped = Collections.unmodifiableList(skipped);
    }

    public Map<Player, Role> getParticipants() {
        return participants;
    }

    public List<Player> getSkipped() {
        return skipped;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
