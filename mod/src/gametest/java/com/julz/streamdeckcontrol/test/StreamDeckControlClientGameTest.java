package com.julz.streamdeckcontrol.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.julz.streamdeckcontrol.client.StreamDeckControlClient;
import com.julz.streamdeckcontrol.client.StreamDeckControlConfigScreen;
import com.julz.streamdeckcontrol.config.ModConfig;
import com.julz.streamdeckcontrol.core.ApiServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Talks to the mod through its real WebSocket API, like the Stream Deck plugin does: pairs, runs actions
 * and checks that the state events follow.
 */
public class StreamDeckControlClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> StreamDeckControlClient.server() != null && StreamDeckControlClient.server().port() > 0);
        int port = context.computeOnClient(client -> StreamDeckControlClient.server().port());
        String code = context.computeOnClient(client -> ModConfig.getInstance().pairingCode);

        testConfigScreen(context);

        // A wrong code is turned away and asks for pairing
        try (Deck wrong = Deck.connect(port)) {
            wrong.send("{\"type\":\"hello\",\"protocol\":1,\"token\":\"AAAA-AAAA\",\"client\":\"Test\"}");
            context.waitFor(client -> wrong.closeCode != 0);
            assertEquals(ApiServer.CLOSE_PAIRING_REQUIRED, wrong.closeCode, "close code for a wrong pairing code");
        }

        try (Deck deck = Deck.connect(port)) {
            deck.send("{\"type\":\"hello\",\"protocol\":1,\"token\":\"" + code + "\",\"client\":\"Test\"}");
            context.waitFor(client -> deck.welcome != null);
            assertEquals(19, deck.welcome.getAsJsonArray("actions").size(), "number of actions");
            assertEquals(false, deck.state("inWorld").getAsBoolean(), "inWorld on the title screen");

            // Cheats on, so the admin keys can be tested too
            try (TestSingleplayerContext singleplayer = context.worldBuilder()
                    .adjustSettings(creator -> creator.setAllowCommands(true))
                    .create()) {
                context.waitFor(client -> client.player != null);
                context.waitFor(client -> deck.has("inWorld") && deck.state("inWorld").getAsBoolean());
                testOptions(context, deck);
                testView(context, deck);
                testScreens(context, deck);
                testCommands(context, deck);
            }
            context.waitFor(client -> !deck.state("inWorld").getAsBoolean());
            assertEquals(false, deck.state("cinematic").getAsBoolean(), "cinematic after leaving the world");
        }
    }

    private static void testConfigScreen(ClientGameTestContext context) {
        context.setScreen(() -> new StreamDeckControlConfigScreen(null));
        context.waitForScreen(StreamDeckControlConfigScreen.class);
        context.takeScreenshot("stream-deck-control-config-screen");
        context.setScreen(() -> null);
    }

    private static void testOptions(ClientGameTestContext context, Deck deck) {
        expectOk(context, deck, "render_distance", "{\"value\":8}");
        context.waitFor(client -> deck.state("renderDistance").getAsInt() == 8);
        expectOk(context, deck, "render_distance", "{\"delta\":-1}");
        context.waitFor(client -> deck.state("renderDistance").getAsInt() == 7);

        expectOk(context, deck, "render_distance", "{\"value\":999}");
        int max = context.computeOnClient(client -> client.options.renderDistance().get());
        assertEquals(true, max <= 32, "render distance is clamped");

        expectOk(context, deck, "simulation_distance", "{\"value\":6}");
        context.waitFor(client -> deck.state("simulationDistance").getAsInt() == 6);

        expectOk(context, deck, "fov", "{\"value\":90}");
        context.waitFor(client -> deck.state("fov").getAsInt() == 90);

        expectOk(context, deck, "volume", "{\"value\":50}");
        context.waitFor(client -> deck.state("volume").getAsInt() == 50);
        expectOk(context, deck, "mute", "{}");
        context.waitFor(client -> deck.state("muted").getAsBoolean());
        expectOk(context, deck, "mute", "{}");
        context.waitFor(client -> !deck.state("muted").getAsBoolean() && deck.state("volume").getAsInt() == 50);

        // One sound category (jukebox and note blocks) on its own; master stays where it was
        expectOk(context, deck, "volume", "{\"source\":\"record\",\"value\":30}");
        context.waitFor(client -> deck.state("volumes").getAsJsonObject().get("record").getAsInt() == 30);
        expectOk(context, deck, "mute", "{\"source\":\"record\"}");
        context.waitFor(client -> deck.state("volumes").getAsJsonObject().get("record").getAsInt() == 0);
        expectOk(context, deck, "mute", "{\"source\":\"record\"}");
        context.waitFor(client -> deck.state("volumes").getAsJsonObject().get("record").getAsInt() == 30);
        assertEquals(50, deck.state("volume").getAsInt(), "master volume after changing one category");
        JsonObject unknown = deck.request(context, "volume", "{\"source\":\"kazoo\"}");
        assertEquals("bad_request", unknown.get("error").getAsString(), "unknown sound category");
    }

    private static void testView(ClientGameTestContext context, Deck deck) {
        expectOk(context, deck, "perspective", "{}");
        context.waitFor(client -> deck.state("perspective").getAsString().equals("third_person_back"));
        expectOk(context, deck, "perspective", "{\"to\":\"first_person\"}");
        context.waitFor(client -> deck.state("perspective").getAsString().equals("first_person"));

        expectOk(context, deck, "hide_hud", "{}");
        context.waitFor(client -> deck.state("hudHidden").getAsBoolean());
        expectOk(context, deck, "hide_hud", "{}");
        context.waitFor(client -> !deck.state("hudHidden").getAsBoolean());

        expectOk(context, deck, "debug_overlay", "{}");
        context.waitFor(client -> deck.state("debugOverlay").getAsBoolean());
        expectOk(context, deck, "debug_overlay", "{}");
        context.waitFor(client -> !deck.state("debugOverlay").getAsBoolean());

        // Cinematic: smooth camera, letterbox bars, HUD hidden; all undone when it ends
        expectOk(context, deck, "cinematic", "{}");
        context.waitFor(client -> deck.state("cinematic").getAsBoolean());
        context.runOnClient(client -> {
            assertEquals(true, client.options.smoothCamera, "smooth camera in cinematic mode");
            assertEquals(true, client.gui.hud.isHidden(), "HUD hidden in cinematic mode");
        });
        // Let the letterbox bars finish fading in
        context.waitTicks(20);
        context.takeScreenshot("stream-deck-control-cinematic");
        expectOk(context, deck, "cinematic", "{}");
        context.waitFor(client -> !deck.state("cinematic").getAsBoolean());
        context.runOnClient(client -> {
            assertEquals(false, client.options.smoothCamera, "smooth camera after cinematic mode");
            assertEquals(false, client.gui.hud.isHidden(), "HUD after cinematic mode");
        });

        expectOk(context, deck, "screenshot", "{}");

        context.waitFor(client -> deck.has("position") && deck.has("time") && deck.has("weather") && deck.has("gameMode"));

        // Compass and server keys
        context.waitFor(client -> deck.has("yaw") && deck.has("biome") && deck.has("server"));
        assertEquals(true, deck.state("biome").getAsString().startsWith("minecraft:"), "biome id");
        assertEquals("Singleplayer", deck.state("server").getAsJsonObject().get("name").getAsString(), "server name");

        // F3 shortcuts
        expectOk(context, deck, "debug_key", "{\"key\":\"chunk_borders\"}");
        context.waitFor(client -> deck.state("chunkBorders").getAsBoolean());
        expectOk(context, deck, "debug_key", "{\"key\":\"chunk_borders\"}");
        context.waitFor(client -> !deck.state("chunkBorders").getAsBoolean());
        expectOk(context, deck, "debug_key", "{\"key\":\"hitboxes\"}");
        context.waitFor(client -> deck.state("hitboxes").getAsBoolean());
        context.waitTicks(2);
        context.takeScreenshot("stream-deck-control-hitboxes");
        expectOk(context, deck, "debug_key", "{\"key\":\"hitboxes\"}");
        context.waitFor(client -> !deck.state("hitboxes").getAsBoolean());
        boolean tooltips = deck.state("advancedTooltips").getAsBoolean();
        expectOk(context, deck, "debug_key", "{\"key\":\"advanced_tooltips\"}");
        context.waitFor(client -> deck.state("advancedTooltips").getAsBoolean() != tooltips);
        expectOk(context, deck, "debug_key", "{\"key\":\"advanced_tooltips\"}");
        expectOk(context, deck, "debug_key", "{\"key\":\"reload_chunks\"}");
        expectOk(context, deck, "debug_key", "{\"key\":\"copy_location\"}");
        String location = context.computeOnClient(client -> com.julz.streamdeckcontrol.client.GameActions.locationCommand(client.player));
        assertEquals(true, location.startsWith("/execute in minecraft:overworld run tp @s "), "copied location: " + location);
        expectOk(context, deck, "debug_key", "{\"key\":\"clear_chat\"}");
        String modeBefore = deck.state("gameMode").getAsString();
        expectOk(context, deck, "debug_key", "{\"key\":\"creative_spectator\"}");
        context.waitFor(client -> deck.state("gameMode").getAsString().equals("spectator"));
        expectOk(context, deck, "debug_key", "{\"key\":\"creative_spectator\"}");
        context.waitFor(client -> deck.state("gameMode").getAsString().equals(modeBefore.equals("spectator") ? "creative" : modeBefore));
        JsonObject unknownKey = deck.request(context, "debug_key", "{\"key\":\"nope\"}");
        assertEquals("bad_request", unknownKey.get("error").getAsString(), "unknown debug key");
        context.waitFor(client -> deck.state("fps").getAsInt() > 0);
    }

    private static void testScreens(ClientGameTestContext context, Deck deck) {
        expectOk(context, deck, "open_screen", "{\"screen\":\"inventory\"}");
        context.waitForScreen(InventoryScreen.class);
        JsonObject blocked = deck.request(context, "open_screen", "{\"screen\":\"chat\"}");
        assertEquals("screen_open", blocked.get("error").getAsString(), "opening chat over the inventory");
        context.setScreen(() -> null);
    }

    private static void testCommands(ClientGameTestContext context, Deck deck) {
        expectOk(context, deck, "chat", "{\"message\":\"hello from the deck at {coords}\"}");
        JsonObject slash = deck.request(context, "chat", "{\"message\":\"/say hi\"}");
        assertEquals("bad_request", slash.get("error").getAsString(), "chat message that is a command");

        JsonObject disabled = deck.request(context, "command", "{\"command\":\"/say hi\"}");
        assertEquals("disabled", disabled.get("error").getAsString(), "chat command while turned off");

        boolean canCheat = deck.state("canCheat").getAsBoolean();
        boolean actualPermission = context.computeOnClient(client ->
                client.player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
        assertEquals(actualPermission, canCheat, "canCheat matches the player's permissions");
        assertEquals(true, canCheat, "canCheat in a world with cheats on");
        String mode = deck.state("gameMode").getAsString();
        expectOk(context, deck, "game_mode", "{\"mode\":\"spectator\"}");
        context.waitFor(client -> deck.state("gameMode").getAsString().equals("spectator"));
        expectOk(context, deck, "game_mode", "{\"mode\":\"" + mode + "\"}");
        context.waitFor(client -> deck.state("gameMode").getAsString().equals(mode));

        expectOk(context, deck, "weather", "{\"weather\":\"rain\"}");
        context.waitFor(client -> deck.state("weather").getAsString().equals("rain"), 400);
        expectOk(context, deck, "weather", "{\"weather\":\"clear\"}");

        expectOk(context, deck, "time", "{\"time\":\"night\"}");
        context.waitFor(client -> {
            long timeOfDay = deck.state("time").getAsJsonObject().get("timeOfDay").getAsLong();
            return timeOfDay >= 13000 && timeOfDay < 14000;
        });

        expectOk(context, deck, "daylight_cycle", "{\"on\":false}");
        context.waitFor(client -> deck.has("daylightCycle") && !deck.state("daylightCycle").getAsBoolean(), 200);
        expectOk(context, deck, "daylight_cycle", "{\"on\":true}");
        context.waitFor(client -> deck.state("daylightCycle").getAsBoolean(), 200);
    }

    private static void expectOk(ClientGameTestContext context, Deck deck, String action, String args) {
        JsonObject result = deck.request(context, action, args);
        if (!result.get("ok").getAsBoolean()) {
            throw new AssertionError(action + " failed: " + result);
        }
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    /** A stand-in for the Stream Deck plugin. */
    private static class Deck extends WebSocketClient implements AutoCloseable {
        private final Map<String, JsonElement> state = new ConcurrentHashMap<>();
        private final Map<String, JsonObject> results = new ConcurrentHashMap<>();
        private volatile JsonObject welcome;
        private volatile int closeCode;
        private int nextId = 1;

        private Deck(int port) {
            super(URI.create("ws://127.0.0.1:" + port));
        }

        static Deck connect(int port) {
            Deck deck = new Deck(port);
            try {
                if (!deck.connectBlocking(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Could not connect to port " + port);
                }
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            return deck;
        }

        boolean has(String key) {
            return state.containsKey(key);
        }

        JsonElement state(String key) {
            JsonElement value = state.get(key);
            if (value == null) {
                throw new AssertionError("No state for " + key + " in " + state);
            }
            return value;
        }

        /** Sends an action and ticks the game until its result arrives. */
        JsonObject request(ClientGameTestContext context, String action, String args) {
            String id = String.valueOf(nextId++);
            send("{\"type\":\"action\",\"id\":\"" + id + "\",\"name\":\"" + action + "\",\"args\":" + args + "}");
            context.waitFor((Minecraft client) -> results.containsKey(id));
            // One more tick so the state event for the change has been sent too
            context.waitTick();
            return results.remove(id);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
        }

        @Override
        public void onMessage(String message) {
            JsonObject msg = JsonParser.parseString(message).getAsJsonObject();
            switch (msg.get("type").getAsString()) {
                case "welcome" -> {
                    msg.getAsJsonObject("state").entrySet().forEach(e -> state.put(e.getKey(), e.getValue()));
                    welcome = msg;
                }
                case "state" -> msg.getAsJsonObject("state").entrySet().forEach(e -> {
                    if (e.getValue().isJsonNull()) {
                        state.remove(e.getKey());
                    } else {
                        state.put(e.getKey(), e.getValue());
                    }
                });
                case "result" -> results.put(msg.get("id").getAsString(), msg);
                default -> {
                }
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            closeCode = code;
        }

        @Override
        public void onError(Exception ex) {
        }
    }
}
