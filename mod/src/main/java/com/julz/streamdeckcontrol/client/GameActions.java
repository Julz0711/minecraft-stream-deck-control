package com.julz.streamdeckcontrol.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.julz.streamdeckcontrol.config.ModConfig;
import com.julz.streamdeckcontrol.core.ActionHandler;
import com.julz.streamdeckcontrol.core.ActionResult;
import com.julz.streamdeckcontrol.core.ChatMessage;
import com.julz.streamdeckcontrol.core.Protocol;
import com.julz.streamdeckcontrol.mixin.MinecraftAccessor;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/** Every action the API offers. Requests arrive on a server thread and run on the game thread. */
public class GameActions implements ActionHandler {
    public static final int MIN_FOV = 30;
    public static final int MAX_FOV = 110;

    private final Minecraft client;
    private final Map<String, BiFunction<Minecraft, JsonObject, ActionResult>> actions = new LinkedHashMap<>();

    public GameActions(Minecraft client) {
        this.client = client;
        actions.put("render_distance", GameActions::renderDistance);
        actions.put("simulation_distance", GameActions::simulationDistance);
        actions.put("perspective", GameActions::perspective);
        actions.put("hide_hud", GameActions::hideHud);
        actions.put("cinematic", GameActions::cinematic);
        actions.put("screenshot", GameActions::screenshot);
        actions.put("debug_overlay", GameActions::debugOverlay);
        actions.put("fov", GameActions::fov);
        actions.put("volume", GameActions::volume);
        actions.put("mute", GameActions::mute);
        actions.put("fullscreen", GameActions::fullscreen);
        actions.put("open_screen", GameActions::openScreen);
        actions.put("command", GameActions::command);
        actions.put("chat", GameActions::chat);
        actions.put("weather", GameActions::weather);
        actions.put("time", GameActions::time);
        actions.put("daylight_cycle", GameActions::daylightCycle);
        actions.put("game_mode", GameActions::gameMode);
        actions.put("debug_key", GameActions::debugKey);
    }

    @Override
    public Set<String> actionNames() {
        return actions.keySet();
    }

    @Override
    public CompletableFuture<ActionResult> run(String name, JsonObject args) {
        BiFunction<Minecraft, JsonObject, ActionResult> action = actions.get(name);
        return CompletableFuture.supplyAsync(() -> action.apply(client, args), client);
    }

    /** Runs an action directly on the game thread, for the game test. */
    public ActionResult runNow(String name, JsonObject args) {
        return actions.get(name).apply(client, args);
    }

    // Options

    private static ActionResult renderDistance(Minecraft client, JsonObject args) {
        return step(client, client.options.renderDistance(), args, 1);
    }

    private static ActionResult simulationDistance(Minecraft client, JsonObject args) {
        if (client.level != null && !client.hasSingleplayerServer()) {
            return ActionResult.fail(Protocol.SINGLEPLAYER_ONLY, "On a server, the server sets the simulation distance");
        }
        return step(client, client.options.simulationDistance(), args, 1);
    }

    private static ActionResult fov(Minecraft client, JsonObject args) {
        return step(client, client.options.fov(), args, 5);
    }

    /** Adds {@code delta} to an integer option, or sets it to {@code value}, within the option's range. */
    private static ActionResult step(Minecraft client, OptionInstance<Integer> option, JsonObject args, int defaultDelta) {
        int target = args.has("value") ? integer(args, "value", option.get()) : option.get() + integer(args, "delta", defaultDelta);
        int clamped = clampToRange(option, target);
        if (clamped != option.get()) {
            option.set(clamped);
            client.options.save();
        }
        return ActionResult.OK;
    }

    static int clampToRange(OptionInstance<Integer> option, int value) {
        if (option.values() instanceof OptionInstance.IntRangeBase range) {
            return Math.clamp(value, range.minInclusive(), range.maxInclusive());
        }
        return value;
    }

    private static ActionResult volume(Minecraft client, JsonObject args) {
        SoundSource source = soundSource(args);
        if (source == null) {
            return ActionResult.fail(Protocol.BAD_REQUEST, "Unknown sound category: " + string(args, "source"));
        }
        OptionInstance<Double> option = client.options.getSoundSourceOptionInstance(source);
        double target = args.has("value")
                ? integer(args, "value", 100) / 100.0
                : percent(option.get()) / 100.0 + integer(args, "delta", 10) / 100.0;
        option.set(Math.clamp(target, 0.0, 1.0));
        client.options.save();
        return ActionResult.OK;
    }

    private static ActionResult mute(Minecraft client, JsonObject args) {
        SoundSource source = soundSource(args);
        if (source == null) {
            return ActionResult.fail(Protocol.BAD_REQUEST, "Unknown sound category: " + string(args, "source"));
        }
        OptionInstance<Double> option = client.options.getSoundSourceOptionInstance(source);
        boolean muted = option.get() <= 0;
        boolean mute = bool(args, "on", !muted);
        ModConfig config = ModConfig.getInstance();
        if (mute && !muted) {
            config.mutedVolumes.put(source.getName(), option.get());
            ModConfig.save();
            option.set(0.0);
        } else if (!mute && muted) {
            option.set(Math.clamp(config.mutedVolumes.getOrDefault(source.getName(), 1.0), 0.05, 1.0));
        }
        client.options.save();
        return ActionResult.OK;
    }

    /** The "source" argument as a sound category, master when left out, or null when unknown. */
    private static SoundSource soundSource(JsonObject args) {
        String name = string(args, "source");
        if (name == null) {
            return SoundSource.MASTER;
        }
        for (SoundSource source : SoundSource.values()) {
            if (source.getName().equals(name)) {
                return source;
            }
        }
        return null;
    }

    private static ActionResult fullscreen(Minecraft client, JsonObject args) {
        OptionInstance<Boolean> fullscreen = client.options.fullscreen();
        boolean on = bool(args, "on", !fullscreen.get());
        if (on != fullscreen.get()) {
            fullscreen.set(on);
            client.options.save();
        }
        return ActionResult.OK;
    }

    // View

    private static ActionResult perspective(Minecraft client, JsonObject args) {
        Options options = client.options;
        CameraType before = options.getCameraType();
        CameraType after = before.cycle();
        if (args.has("to")) {
            try {
                after = CameraType.valueOf(string(args, "to").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ActionResult.fail(Protocol.BAD_REQUEST, "Unknown perspective: " + string(args, "to"));
            }
        }
        options.setCameraType(after);
        // Same as the vanilla F5 key: spectating mobs with a shader effect switches it with the view
        if (before.isFirstPerson() != after.isFirstPerson() && client.getCameraEntity() != null) {
            client.gameRenderer.checkEntityPostEffect(after.isFirstPerson() ? client.getCameraEntity() : null);
        }
        return ActionResult.OK;
    }

    private static ActionResult hideHud(Minecraft client, JsonObject args) {
        boolean hidden = client.gui.hud.isHidden();
        if (bool(args, "on", !hidden) != hidden) {
            client.gui.hud.toggle();
        }
        return ActionResult.OK;
    }

    private static ActionResult cinematic(Minecraft client, JsonObject args) {
        if (client.level == null) {
            return ActionResult.fail(Protocol.NOT_IN_WORLD, "Join a world first");
        }
        Cinematic.set(client, bool(args, "on", !Cinematic.isActive()));
        return ActionResult.OK;
    }

    private static ActionResult debugOverlay(Minecraft client, JsonObject args) {
        boolean visible = client.debugEntries.isOverlayVisible();
        boolean on = bool(args, "on", !visible);
        if (on != visible) {
            client.debugEntries.setOverlayVisible(on);
        }
        return ActionResult.OK;
    }

    private static ActionResult screenshot(Minecraft client, JsonObject args) {
        Screenshot.grab(client, true);
        return ActionResult.OK;
    }

    private static ActionResult openScreen(Minecraft client, JsonObject args) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return ActionResult.fail(Protocol.NOT_IN_WORLD, "Join a world first");
        }
        String screen = string(args, "screen");
        if (screen == null) {
            screen = "pause";
        }
        if (!screen.equals("pause") && client.gui.screen() != null) {
            return ActionResult.fail(Protocol.SCREEN_OPEN, "Close the open menu first");
        }
        switch (screen) {
            case "pause" -> client.pauseGame(false);
            case "chat" -> client.gui.openChatScreen(ChatComponent.ChatMethod.MESSAGE);
            case "inventory" -> {
                // Same as the vanilla inventory key
                if (client.gameMode != null && client.gameMode.isServerControlledInventory()) {
                    player.sendOpenInventory();
                } else {
                    client.gui.setScreen(new InventoryScreen(player));
                }
            }
            default -> {
                return ActionResult.fail(Protocol.BAD_REQUEST, "Unknown screen: " + screen);
            }
        }
        return ActionResult.OK;
    }

    // F3 shortcuts

    /** The F3 + key combinations, by name. Debug views are refused where the server hides debug info, like vanilla. */
    private static ActionResult debugKey(Minecraft client, JsonObject args) {
        String key = string(args, "key");
        if (key == null) {
            return ActionResult.fail(Protocol.BAD_REQUEST, "No debug key given");
        }
        LocalPlayer player = client.player;
        boolean needsWorld = !key.equals("advanced_tooltips") && !key.equals("reload_resources") && !key.equals("pause_on_lost_focus");
        if (needsWorld && (player == null || client.level == null)) {
            return ActionResult.fail(Protocol.NOT_IN_WORLD, "Join a world first");
        }
        boolean reduced = player != null && player.isReducedDebugInfo();
        switch (key) {
            case "chunk_borders", "hitboxes", "copy_location" -> {
                if (reduced) {
                    return ActionResult.fail(Protocol.DISABLED, "This server hides debug info");
                }
            }
            default -> {
            }
        }
        switch (key) {
            case "chunk_borders" -> client.debugEntries.toggleStatus(DebugScreenEntries.CHUNK_BORDERS);
            case "hitboxes" -> client.debugEntries.toggleStatus(DebugScreenEntries.ENTITY_HITBOXES);
            case "advanced_tooltips" -> {
                client.options.advancedItemTooltips = !client.options.advancedItemTooltips;
                client.options.save();
            }
            case "pause_on_lost_focus" -> {
                client.options.pauseOnLostFocus = !client.options.pauseOnLostFocus;
                client.options.save();
            }
            case "reload_chunks" -> ((MinecraftAccessor) client).streamDeckControl$levelExtractor().allChanged();
            case "reload_resources" -> client.reloadResourcePacks();
            case "copy_location" -> client.keyboardHandler.setClipboard(locationCommand(player));
            default -> {
                return ActionResult.fail(Protocol.BAD_REQUEST, "Unknown debug key: " + key);
            }
        }
        return ActionResult.OK;
    }

    /** The same teleport command F3+C copies. */
    public static String locationCommand(LocalPlayer player) {
        return String.format(Locale.ROOT, "/execute in %s run tp @s %.2f %.2f %.2f %.2f %.2f",
                player.level().dimension().identifier(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    // Commands

    private static ActionResult command(Minecraft client, JsonObject args) {
        if (!ModConfig.getInstance().allowCommands) {
            return ActionResult.fail(Protocol.DISABLED, "Turn on chat commands in the Stream Deck Control settings");
        }
        String command = string(args, "command");
        if (command == null || command.isBlank()) {
            return ActionResult.fail(Protocol.BAD_REQUEST, "No command given");
        }
        return sendCommand(client, command.strip().replaceFirst("^/", ""), false);
    }

    /** Sends a saved chat message, like typing it into chat. Placeholders fill in the player's position. */
    private static ActionResult chat(Minecraft client, JsonObject args) {
        LocalPlayer player = client.player;
        if (player == null || client.getConnection() == null) {
            return ActionResult.fail(Protocol.NOT_IN_WORLD, "Join a world first");
        }
        String template = string(args, "message");
        BlockPos pos = player.blockPosition();
        String message = ChatMessage.fill(template != null ? template : "", Map.of(
                "x", String.valueOf(pos.getX()),
                "y", String.valueOf(pos.getY()),
                "z", String.valueOf(pos.getZ()),
                "coords", pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        String problem = ChatMessage.problem(message);
        if (problem != null) {
            return ActionResult.fail(Protocol.BAD_REQUEST, problem);
        }
        client.getConnection().sendChat(message);
        return ActionResult.OK;
    }

    private static ActionResult weather(Minecraft client, JsonObject args) {
        String weather = string(args, "weather");
        if (!"clear".equals(weather) && !"rain".equals(weather) && !"thunder".equals(weather)) {
            return ActionResult.fail(Protocol.BAD_REQUEST, "Weather must be clear, rain or thunder");
        }
        return sendCommand(client, "weather " + weather, true);
    }

    private static ActionResult time(Minecraft client, JsonObject args) {
        String time = string(args, "time");
        if (!Set.of("day", "noon", "night", "midnight").contains(time)) {
            return ActionResult.fail(Protocol.BAD_REQUEST, "Time must be day, noon, night or midnight");
        }
        return sendCommand(client, "time set " + time, true);
    }

    private static ActionResult daylightCycle(Minecraft client, JsonObject args) {
        Boolean current = GameState.daylightCycle();
        boolean on = bool(args, "on", current == null || !current);
        return sendCommand(client, "gamerule advance_time " + on, true);
    }

    private static final GameType[] GAME_MODE_CYCLE = {GameType.SURVIVAL, GameType.CREATIVE, GameType.SPECTATOR};

    private static ActionResult gameMode(Minecraft client, JsonObject args) {
        if (client.gameMode == null) {
            return ActionResult.fail(Protocol.NOT_IN_WORLD, "Join a world first");
        }
        GameType target;
        if (args.has("mode")) {
            target = GameType.byName(string(args, "mode"), null);
            if (target == null) {
                return ActionResult.fail(Protocol.BAD_REQUEST, "Unknown game mode: " + string(args, "mode"));
            }
        } else {
            target = GAME_MODE_CYCLE[0];
            GameType current = client.gameMode.getPlayerMode();
            for (int i = 0; i < GAME_MODE_CYCLE.length; i++) {
                if (GAME_MODE_CYCLE[i] == current) {
                    target = GAME_MODE_CYCLE[(i + 1) % GAME_MODE_CYCLE.length];
                }
            }
        }
        return sendCommand(client, "gamemode " + target.getName(), true);
    }

    /** Admin commands need operator rights (or cheats in singleplayer); without them nothing is sent. */
    public static boolean canCheat(Minecraft client) {
        return client.player != null && client.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static ActionResult sendCommand(Minecraft client, String command, boolean admin) {
        if (client.player == null || client.getConnection() == null) {
            return ActionResult.fail(Protocol.NOT_IN_WORLD, "Join a world first");
        }
        if (admin && !canCheat(client)) {
            return ActionResult.fail(Protocol.NO_PERMISSION, "Needs operator rights or cheats");
        }
        client.getConnection().sendCommand(command);
        return ActionResult.OK;
    }

    // Arguments

    static int percent(double volume) {
        return (int) Math.round(volume * 100);
    }

    private static String string(JsonObject args, String key) {
        JsonElement value = args.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static int integer(JsonObject args, String key, int fallback) {
        JsonElement value = args.get(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsInt();
        }
        return fallback;
    }

    private static boolean bool(JsonObject args, String key, boolean fallback) {
        JsonElement value = args.get(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        return fallback;
    }
}
