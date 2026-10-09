package dev.maniaclobby;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class LobbyPlugin extends JavaPlugin implements Listener, PluginMessageListener, TabExecutor {

    public static final String CHANNEL = "maniaclobby:main";

    // Действия мод -> плагин
    private static final int A_HELLO = 0, A_OPEN = 1, A_READY = 2, A_ROLE = 3, A_START = 4, A_LEAVE = 5;

    private static final int SLOT_READY = 28, SLOT_ROLE = 30, SLOT_START = 32, SLOT_BACK = 34;

    private final Lobby lobby = new Lobby();
    private final Set<UUID> modded = new HashSet<>();
    private final Set<UUID> internalClose = new HashSet<>();
    private final List<UUID> lastParticipants = new ArrayList<>();
    private NamespacedKey playItemKey;
    private boolean gameRunning;

    /** Держатель инвентаря-меню для игроков без мода. */
    private static final class LobbyHolder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onEnable() {
        saveDefaultConfig();
        playItemKey = new NamespacedKey(this, "play_item");
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getCommand("play").setExecutor(this);
        getCommand("lobby").setExecutor(this);
        getCommand("lobby").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    // ------------------------------------------------------------------ networking

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || message.length < 1) return;
        if (modded.add(player.getUniqueId())) {
            registerChannelForPlayer(player);
        }
        switch (message[0]) {
            case A_HELLO -> { }
            case A_OPEN -> join(player);
            case A_READY -> toggleReady(player);
            case A_ROLE -> toggleRole(player);
            case A_START -> start(player);
            case A_LEAVE -> leave(player);
            default -> { }
        }
    }

    /**
     * Клиенты Fabric не всегда сообщают серверу, что слушают канал, а Paper шлёт
     * plugin message только в зарегистрированные каналы. Добавляем канал вручную (reflection).
     */
    private void registerChannelForPlayer(Player p) {
        try {
            Method m = p.getClass().getMethod("addChannel", String.class);
            m.invoke(p, CHANNEL);
        } catch (Throwable t) {
            getLogger().fine("Не удалось зарегистрировать канал для " + p.getName() + ": " + t);
        }
    }

    private void send(Player p, byte[] data) {
        p.sendPluginMessage(this, CHANNEL, data);
    }

    // ------------------------------------------------------------------ lobby logic

    private void join(Player p) {
        if (gameRunning) {
            p.sendMessage(Component.text("Игра уже идёт, дождитесь окончания.", NamedTextColor.RED));
            return;
        }
        UUID id = p.getUniqueId();
        if (!lobby.contains(id)) {
            if (lobby.size() >= getConfig().getInt("max-players", 16)) {
                p.sendMessage(Component.text("Лобби заполнено.", NamedTextColor.RED));
                return;
            }
            lobby.add(id);
        }
        broadcast(p);
    }

    private void leave(Player p) {
        UUID id = p.getUniqueId();
        if (!lobby.contains(id)) return;
        lobby.remove(id);
        closeUi(p);
        broadcast(null);
    }

    private void toggleReady(Player p) {
        Lobby.Member m = lobby.get(p.getUniqueId());
        if (m == null) return;
        m.ready = !m.ready;
        broadcast(null);
    }

    private void toggleRole(Player p) {
        Lobby.Member m = lobby.get(p.getUniqueId());
        if (m == null) return;
        Role next = m.role.other();
        if (next == Role.MANIAC && lobby.count(Role.MANIAC) >= getConfig().getInt("max-maniacs", 1)) {
            p.sendMessage(Component.text("Маньяк уже выбран другим игроком.", NamedTextColor.RED));
            return;
        }
        m.role = next;
        broadcast(null);
    }

    private void start(Player p) {
        if (!lobby.contains(p.getUniqueId())) return;
        if (!lobby.isLeader(p.getUniqueId())) {
            p.sendMessage(Component.text("Начать игру может только лидер.", NamedTextColor.RED));
            return;
        }

        Map<Player, Role> participants = new LinkedHashMap<>();
        List<Player> skipped = new ArrayList<>();
        for (var e : lobby.members().entrySet()) {
            Player pl = Bukkit.getPlayer(e.getKey());
            if (pl == null) continue;
            if (e.getValue().ready) participants.put(pl, e.getValue().role);
            else skipped.add(pl);
        }

        int minReady = getConfig().getInt("min-ready", 2);
        if (participants.size() < minReady) {
            p.sendMessage(Component.text("Нужно минимум " + minReady + " готовых игроков.", NamedTextColor.RED));
            return;
        }

        // Если никто не выбрал маньяка — выбираем случайно среди готовых.
        if (!participants.containsValue(Role.MANIAC)) {
            List<Player> list = new ArrayList<>(participants.keySet());
            participants.put(list.get(ThreadLocalRandom.current().nextInt(list.size())), Role.MANIAC);
        }

        // Закрываем меню у всех и очищаем лобби.
        for (UUID id : new ArrayList<>(lobby.members().keySet())) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) closeUi(pl);
        }
        lobby.clear();

        gameRunning = true;
        lastParticipants.clear();
        participants.keySet().forEach(pl -> lastParticipants.add(pl.getUniqueId()));

        for (Player pl : skipped) {
            pl.sendMessage(Component.text("Вы не нажали «Готов» — игра началась без вас.", NamedTextColor.YELLOW));
        }

        Bukkit.getPluginManager().callEvent(new LobbyGameStartEvent(participants, skipped));

        if (getConfig().getBoolean("default-start-behaviour", true)) {
            Location spawn = getConfig().getLocation("game-spawn");
            for (var e : participants.entrySet()) {
                Player pl = e.getKey();
                if (spawn != null) pl.teleport(spawn);
                pl.setGameMode(GameMode.SURVIVAL);
                TextColor color = e.getValue() == Role.MANIAC ? NamedTextColor.RED : NamedTextColor.GREEN;
                pl.showTitle(Title.title(
                        Component.text(e.getValue().title, color),
                        Component.text("Игра началась!", NamedTextColor.GRAY)));
            }
        }
    }

    private void endGame() {
        gameRunning = false;
        Location spawn = getConfig().getLocation("lobby-spawn");
        if (spawn != null) {
            for (UUID id : lastParticipants) {
                Player pl = Bukkit.getPlayer(id);
                if (pl != null) pl.teleport(spawn);
            }
        }
        lastParticipants.clear();
    }

    // ------------------------------------------------------------------ UI sync

    /** Отправляет актуальное состояние всем в лобби. opener — кому нужно открыть меню. */
    private void broadcast(Player opener) {
        for (UUID id : new ArrayList<>(lobby.members().keySet())) {
            Player pl = Bukkit.getPlayer(id);
            if (pl == null) {
                lobby.remove(id);
                continue;
            }
            boolean open = pl.equals(opener);
            if (modded.contains(id)) {
                send(pl, Net.state(lobby, open));
            } else {
                refreshGui(pl, open);
            }
        }
    }

    private void closeUi(Player p) {
        if (modded.contains(p.getUniqueId())) {
            send(p, Net.close());
        } else if (p.getOpenInventory().getTopInventory().getHolder() instanceof LobbyHolder) {
            internalClose.add(p.getUniqueId());
            try {
                p.closeInventory();
            } finally {
                internalClose.remove(p.getUniqueId());
            }
        }
    }

    // ------------------------------------------------------------------ fallback GUI (без мода)

    private void refreshGui(Player p, boolean open) {
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof LobbyHolder h) {
            fillGui(p, h.inv);
        } else if (open) {
            LobbyHolder h = new LobbyHolder();
            Inventory inv = Bukkit.createInventory(h, 36, Component.text("Лобби"));
            h.inv = inv;
            fillGui(p, inv);
            p.openInventory(inv);
        }
    }

    private void fillGui(Player viewer, Inventory inv) {
        inv.clear();
        Lobby.Member me = lobby.get(viewer.getUniqueId());
        if (me == null) return;

        int slot = 0;
        UUID leader = lobby.leader();
        for (var e : lobby.members().entrySet()) {
            if (slot >= 27) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(e.getKey()));
            String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            boolean ready = e.getValue().ready;
            sm.displayName(noItalic(Component.text((ready ? "✔ " : "✘ ") + name,
                    ready ? NamedTextColor.GREEN : NamedTextColor.RED)));
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.text(e.getValue().role.title,
                    e.getValue().role == Role.MANIAC ? NamedTextColor.DARK_RED : NamedTextColor.GRAY)));
            if (e.getKey().equals(leader)) lore.add(noItalic(Component.text("★ Лидер", NamedTextColor.GOLD)));
            sm.lore(lore);
            head.setItemMeta(sm);
            inv.setItem(slot++, head);
        }

        inv.setItem(SLOT_READY, button(
                me.ready ? Material.LIME_DYE : Material.GRAY_DYE,
                me.ready ? "✔ Готов (нажмите, чтобы отменить)" : "✘ Не готов (нажмите, чтобы подтвердить)",
                me.ready ? NamedTextColor.GREEN : NamedTextColor.RED));
        inv.setItem(SLOT_ROLE, button(
                me.role == Role.MANIAC ? Material.WITHER_SKELETON_SKULL : Material.SHIELD,
                "Роль: " + me.role.title + " (нажмите, чтобы сменить)",
                me.role == Role.MANIAC ? NamedTextColor.DARK_RED : NamedTextColor.AQUA));
        boolean isLeader = lobby.isLeader(viewer.getUniqueId());
        inv.setItem(SLOT_START, isLeader
                ? button(Material.NETHER_STAR, "НАЧАТЬ", NamedTextColor.GOLD)
                : button(Material.GRAY_STAINED_GLASS_PANE, "Начать (только лидер)", NamedTextColor.DARK_GRAY));
        inv.setItem(SLOT_BACK, button(Material.BARRIER, "Назад", NamedTextColor.RED));
    }

    private static ItemStack button(Material mat, String name, TextColor color) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(noItalic(Component.text(name, color)));
        it.setItemMeta(meta);
        return it;
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof LobbyHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        switch (e.getRawSlot()) {
            case SLOT_READY -> toggleReady(p);
            case SLOT_ROLE -> toggleRole(p);
            case SLOT_START -> start(p);
            case SLOT_BACK -> leave(p);
            default -> { }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof LobbyHolder)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        if (internalClose.contains(p.getUniqueId())) return;
        // Закрыли меню (Esc) — выходим из лобби, как в моде.
        if (lobby.contains(p.getUniqueId())) {
            lobby.remove(p.getUniqueId());
            broadcast(null);
        }
    }

    // ------------------------------------------------------------------ play item

    private ItemStack playItem() {
        ItemStack it = button(Material.COMPASS, "Играть", NamedTextColor.GREEN);
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(playItemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isPlayItem(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(playItemKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (getConfig().getBoolean("give-play-item", true)) {
            e.getPlayer().getInventory().setItem(8, playItem());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        modded.remove(id);
        internalClose.remove(id);
        if (lobby.contains(id)) {
            lobby.remove(id);
            broadcast(null);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isPlayItem(e.getItem())) return;
        e.setCancelled(true);
        join(e.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isPlayItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("play")) {
            if (sender instanceof Player p) join(p);
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        if (sub.equals("end")) {
            if (!sender.hasPermission("maniaclobby.admin")) return deny(sender);
            endGame();
            sender.sendMessage(Component.text("Игра завершена, лобби снова открыто.", NamedTextColor.GREEN));
            return true;
        }
        if (!(sender instanceof Player p)) return true;

        switch (sub) {
            case "ready" -> toggleReady(p);
            case "role" -> toggleRole(p);
            case "start" -> start(p);
            case "leave" -> leave(p);
            case "setlobby", "setgame" -> {
                if (!p.hasPermission("maniaclobby.admin")) return deny(p);
                getConfig().set(sub.equals("setlobby") ? "lobby-spawn" : "game-spawn", p.getLocation());
                saveConfig();
                p.sendMessage(Component.text("Точка сохранена.", NamedTextColor.GREEN));
            }
            default -> join(p);
        }
        return true;
    }

    private boolean deny(CommandSender s) {
        s.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : List.of("ready", "role", "start", "leave", "setlobby", "setgame", "end")) {
            if (s.startsWith(args[0].toLowerCase())) out.add(s);
        }
        return out;
    }
}
