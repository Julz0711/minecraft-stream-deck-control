package com.julz.streamdeckcontrol.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

import java.util.Locale;

/** Builds the state snapshot sent to clients. Runs on the game thread every tick. */
public final class GameState {
    private static final int DAYLIGHT_SAMPLE_TICKS = 20;

    private static Boolean daylightCycle;
    private static long lastClockTime = Long.MIN_VALUE;
    private static int ticksSinceSample;

    private GameState() {
    }

    /** Whether the day/night clock is running, or null when unknown. */
    public static Boolean daylightCycle() {
        return daylightCycle;
    }

    public static JsonObject snapshot(Minecraft client) {
        JsonObject state = new JsonObject();
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        boolean inWorld = level != null && player != null;

        state.addProperty("inWorld", inWorld);
        state.addProperty("singleplayer", client.hasSingleplayerServer());
        state.addProperty("renderDistance", client.options.renderDistance().get());
        state.addProperty("simulationDistance", client.options.simulationDistance().get());
        state.addProperty("fov", client.options.fov().get());
        double volume = client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get();
        state.addProperty("volume", GameActions.percent(volume));
        state.addProperty("muted", volume <= 0);
        state.addProperty("fullscreen", client.options.fullscreen().get());
        state.addProperty("fps", client.getFps());
        state.addProperty("perspective", client.options.getCameraType().name().toLowerCase(Locale.ROOT));
        state.addProperty("hudHidden", client.gui.hud.isHidden());
        state.addProperty("debugOverlay", client.debugEntries.isOverlayVisible());
        state.addProperty("cinematic", Cinematic.isActive());

        if (inWorld) {
            state.addProperty("canCheat", GameActions.canCheat(client));
            BlockPos pos = player.blockPosition();
            JsonObject position = new JsonObject();
            position.addProperty("x", pos.getX());
            position.addProperty("y", pos.getY());
            position.addProperty("z", pos.getZ());
            state.add("position", position);

            long clock = level.getOverworldClockTime();
            JsonObject time = new JsonObject();
            time.addProperty("day", clock / 24000 + 1);
            time.addProperty("timeOfDay", clock % 24000);
            state.add("time", time);
            state.addProperty("weather", level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear");
            if (client.gameMode != null) {
                state.addProperty("gameMode", client.gameMode.getPlayerMode().getName());
            }
            sampleDaylightCycle(client, clock);
            if (daylightCycle != null) {
                state.addProperty("daylightCycle", daylightCycle);
            }
        } else {
            daylightCycle = null;
            lastClockTime = Long.MIN_VALUE;
        }
        return state;
    }

    /**
     * The client is not told the advance_time game rule, so this watches the clock instead:
     * if it moved during the last second, the cycle is on.
     */
    private static void sampleDaylightCycle(Minecraft client, long clock) {
        if (client.isPaused()) {
            return;
        }
        if (lastClockTime == Long.MIN_VALUE) {
            lastClockTime = clock;
            ticksSinceSample = 0;
            return;
        }
        if (++ticksSinceSample >= DAYLIGHT_SAMPLE_TICKS) {
            daylightCycle = clock != lastClockTime;
            lastClockTime = clock;
            ticksSinceSample = 0;
        }
    }
}
