package com.julz.streamdeckcontrol.client;

import com.julz.streamdeckcontrol.config.ModConfig;
import com.julz.streamdeckcontrol.core.ApiServer;
import com.julz.streamdeckcontrol.core.PairingToken;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class StreamDeckControlClient implements ClientModInitializer {
    public static final String MOD_ID = "stream-deck-control";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** Tries this many ports from the configured one, one per game instance. */
    public static final int PORT_ATTEMPTS = 10;

    private static final long PAIRING_TOAST_INTERVAL_MS = 15_000;
    private static final SystemToast.SystemToastId PAIRING_TOAST = new SystemToast.SystemToastId(10_000L);
    private static final SystemToast.SystemToastId COPIED_TOAST = new SystemToast.SystemToastId(3_000L);

    private static ApiServer server;
    private static GameActions actions;
    private static volatile long lastPairingToast;

    @Override
    public void onInitializeClient() {
        ModConfig.getInstance();

        ClientLifecycleEvents.CLIENT_STARTED.register(StreamDeckControlClient::startServer);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (server != null) {
                server.stop();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (server != null) {
                server.publishState(GameState.snapshot(client));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> Cinematic.reset(client)));
    }

    private static void startServer(Minecraft client) {
        actions = new GameActions(client);
        ModConfig config = ModConfig.getInstance();
        server = new ApiServer(
                new ApiServer.Settings(
                        () -> ModConfig.getInstance().pairingCode,
                        () -> List.copyOf(ModConfig.getInstance().allowedOrigins),
                        version(MOD_ID),
                        version("minecraft")),
                actions,
                clientName -> client.execute(() -> showPairingToast(client, clientName)));
        server.start(config.port, PORT_ATTEMPTS);
    }

    public static ApiServer server() {
        return server;
    }

    public static GameActions actions() {
        return actions;
    }

    /** Replaces the pairing code; every connected Stream Deck has to pair again. */
    public static void newPairingCode() {
        ModConfig.getInstance().pairingCode = PairingToken.generate();
        ModConfig.save();
        if (server != null) {
            server.disconnectAll();
        }
    }

    /** Puts the pairing code on the clipboard and confirms with a toast. */
    public static void copyPairingCode(Minecraft client) {
        client.keyboardHandler.setClipboard(ModConfig.getInstance().pairingCode);
        SystemToast.addOrUpdate(client.gui.toastManager(), COPIED_TOAST,
                Component.translatable("toast.stream-deck-control.copied.title"),
                Component.translatable("toast.stream-deck-control.copied.code"));
    }

    private static void showPairingToast(Minecraft client, String clientName) {
        if (!ModConfig.getInstance().showPairingToasts) {
            return;
        }
        // The plugin retries every few seconds; one toast at a time is enough.
        long now = System.currentTimeMillis();
        if (now - lastPairingToast < PAIRING_TOAST_INTERVAL_MS) {
            return;
        }
        lastPairingToast = now;
        SystemToast.addOrUpdate(client.gui.toastManager(), PAIRING_TOAST,
                Component.translatable("toast.stream-deck-control.pairing.title", clientName),
                Component.translatable("toast.stream-deck-control.pairing.code", ModConfig.getInstance().pairingCode));
        // In a world the code also goes to chat, where clicking it copies it.
        if (client.player != null) {
            String code = ModConfig.getInstance().pairingCode;
            Component clickable = ComponentUtils.wrapInSquareBrackets(Component.literal(code)).withStyle(style -> style
                    .withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.CopyToClipboard(code))
                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.stream-deck-control.pairing.hover"))));
            client.gui.hud.getChat().addClientSystemMessage(Component.translatable("chat.stream-deck-control.pairing", clientName, clickable));
        }
    }

    private static String version(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
