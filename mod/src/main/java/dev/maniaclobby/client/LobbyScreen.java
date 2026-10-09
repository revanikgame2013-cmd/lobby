package dev.maniaclobby.client;

import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;

/** Экран лобби: игроки в ряд, над каждым ✔ или ✘, кнопки роли / готов / начать / назад. */
public class LobbyScreen extends Screen {

    private static final int PANEL_W = 130;

    private LobbyState state;
    private final Map<UUID, LivingEntity> entities = new HashMap<>();
    private boolean closedByServer;

    private ButtonWidget roleButton;
    private ButtonWidget readyButton;
    private ButtonWidget startButton;

    public LobbyScreen(LobbyState state) {
        super(Text.literal("Лобби"));
        this.state = state;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("НАЗАД [ESC]"), b -> close())
                .dimensions(10, height - 30, 110, 20).build());

        roleButton = addDrawableChild(ButtonWidget.builder(Text.empty(),
                b -> ManiacLobbyClient.send(ManiacLobbyClient.A_ROLE))
                .dimensions(10, 20, 110, 20).build());

        readyButton = addDrawableChild(ButtonWidget.builder(Text.empty(),
                b -> ManiacLobbyClient.send(ManiacLobbyClient.A_READY))
                .dimensions(width - 240, height - 30, 110, 20).build());

        startButton = addDrawableChild(ButtonWidget.builder(Text.literal("НАЧАТЬ"),
                b -> ManiacLobbyClient.send(ManiacLobbyClient.A_START))
                .dimensions(width - 120, height - 30, 110, 20).build());

        refreshWidgets();
    }

    public void update(LobbyState s) {
        this.state = s;
        refreshWidgets();
    }

    public void closeFromServer() {
        closedByServer = true;
        if (client != null) client.setScreen(null);
    }

    private UUID selfId() {
        return client != null && client.player != null ? client.player.getUuid() : null;
    }

    private void refreshWidgets() {
        if (roleButton == null) return;
        LobbyState.Member me = state.find(selfId());
        boolean maniac = me != null && me.maniac();
        boolean ready = me != null && me.ready();
        roleButton.setMessage(Text.literal("⟳ " + (maniac ? "Маньяк" : "Выживший")));
        readyButton.setMessage(Text.literal(ready ? "✔ Готов" : "✘ Не готов"));
        startButton.active = state.leader().equals(selfId());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        // Закрыли экран сами (Esc / Назад) — сообщаем серверу, что вышли из лобби.
        if (!closedByServer) ManiacLobbyClient.send(ManiacLobbyClient.A_LEAVE);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xE0050505, 0xF0140808);
        ctx.fillGradient(0, 0, PANEL_W, height, 0x80500000, 0x40100000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        int n = state.members().size();
        if (n > 0) {
            int areaX = PANEL_W;
            int areaW = width - PANEL_W;
            int slotW = Math.min(90, areaW / n);
            int startX = areaX + (areaW - slotW * n) / 2;
            int y2 = height - 90;
            int y1 = Math.max(60, y2 - 170);

            for (int i = 0; i < n; i++) {
                LobbyState.Member m = state.members().get(i);
                int cx = startX + slotW * i + slotW / 2;
                drawMember(ctx, m, cx, slotW, y1, y2, mouseX, mouseY);
            }
        }

        String stats = "Игроков: " + n + "   Маньяков: " + state.maniacCount();
        ctx.drawTextWithShadow(textRenderer, stats, width - textRenderer.getWidth(stats) - 10, height - 46, 0xFFFFFFFF);
    }

    private void drawMember(DrawContext ctx, LobbyState.Member m, int cx, int slotW, int y1, int y2, int mouseX, int mouseY) {
        LivingEntity entity = entityFor(m);
        if (entity != null) {
            InventoryScreen.drawEntity(ctx, cx - slotW / 2, y1, cx + slotW / 2, y2, 70, 0.0625F,
                    mouseX, mouseY, entity);
        }

        // ✔ / ✘ над головой
        String mark = m.ready() ? "✔" : "✘";
        int markColor = m.ready() ? 0xFF55FF55 : 0xFFFF5555;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, y1 - 6, 0);
        ctx.getMatrices().scale(2.5F, 2.5F, 1F);
        ctx.drawCenteredTextWithShadow(textRenderer, mark, 0, 0, markColor);
        ctx.getMatrices().pop();

        // ник, роль, лидер
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(m.name()), cx, y2 + 8, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(m.maniac() ? "Маньяк" : "Выживший"),
                cx, y2 + 20, m.maniac() ? 0xFFFF5555 : 0xFFAAAAAA);
        if (m.id().equals(state.leader())) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("★ лидер"), cx, y2 + 32, 0xFFFFAA00);
        }
    }

    private LivingEntity entityFor(LobbyState.Member m) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.getUuid().equals(m.id())) return mc.player;
        if (mc.world == null) return null;
        return entities.computeIfAbsent(m.id(),
                id -> new OtherClientPlayerEntity(mc.world, new GameProfile(id, m.name())));
    }
}
