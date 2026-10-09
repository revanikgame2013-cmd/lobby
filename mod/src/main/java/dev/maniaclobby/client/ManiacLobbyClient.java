package dev.maniaclobby.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class ManiacLobbyClient implements ClientModInitializer {

    public static final int A_HELLO = 0, A_OPEN = 1, A_READY = 2, A_ROLE = 3, A_START = 4, A_LEAVE = 5;

    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatePayload.ID, StatePayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(StatePayload.ID,
                (payload, context) -> context.client().execute(() -> handle(context.client(), payload)));

        // Сообщаем серверу, что у нас есть мод.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> send(A_HELLO));

        // Клавиша P — открыть лобби.
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.maniaclobby.play", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "key.categories.misc"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) send(A_OPEN);
            }
        });

        // Кнопка «Играть» справа внизу в меню паузы (Esc).
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof GameMenuScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("ИГРАТЬ"), b -> {
                    client.setScreen(null);
                    send(A_OPEN);
                }).dimensions(w - 110, h - 30, 100, 20).build());
            }
        });
    }

    public static void send(int action) {
        try {
            ClientPlayNetworking.send(new ActionPayload(action));
        } catch (Exception ignored) {
            // не на сервере с плагином — молча игнорируем
        }
    }

    private static void handle(MinecraftClient client, StatePayload p) {
        if (p.close()) {
            if (client.currentScreen instanceof LobbyScreen s) s.closeFromServer();
            return;
        }
        if (client.currentScreen instanceof LobbyScreen s) {
            s.update(p.state());
        } else if (p.open()) {
            client.setScreen(new LobbyScreen(p.state()));
        }
    }
}
