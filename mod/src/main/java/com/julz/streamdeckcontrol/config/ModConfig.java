package com.julz.streamdeckcontrol.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.julz.streamdeckcontrol.client.StreamDeckControlClient;
import com.julz.streamdeckcontrol.core.PairingToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    public static final int DEFAULT_PORT = 25570;

    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("stream-deck-control.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance;

    /** First port to try; a second game instance takes the next free one. */
    public int port = DEFAULT_PORT;
    /** The code the Stream Deck plugin must send. Generated on first launch. */
    public String pairingCode = "";
    /** The "Chat command" key runs any command, so it is off until the player turns it on. */
    public boolean allowCommands = false;
    /** Shows a toast with the pairing code when an unpaired program tries to connect. */
    public boolean showPairingToasts = true;
    /** Width / height of the picture between the cinematic letterbox bars, e.g. 2.39 for 2.39:1. 0 turns the bars off. */
    public double letterboxRatio = 2.39;
    /** Cinematic mode also hides the HUD (like F1). */
    public boolean cinematicHidesHud = true;
    /** Web pages allowed to use the API, e.g. "http://localhost:3000". Programs that send no Origin header are always allowed. */
    public List<String> allowedOrigins = new ArrayList<>();
    /** Master volume to restore when unmuting. */
    public double volumeBeforeMute = 1.0;

    public static ModConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, ModConfig.class);
            } catch (IOException | JsonParseException e) {
                StreamDeckControlClient.LOGGER.error("Failed to read {}, using defaults", CONFIG_FILE, e);
            }
        }
        if (instance == null) {
            instance = new ModConfig();
        }
        instance.sanitize();
        // Writes the file on first launch and adds options introduced by newer versions.
        save();
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            StreamDeckControlClient.LOGGER.error("Failed to save {}", CONFIG_FILE, e);
        }
    }

    /** Pulls hand-edited values back into range. */
    public void sanitize() {
        if (port < 1024 || port > 65535) {
            port = DEFAULT_PORT;
        }
        if (PairingToken.normalize(pairingCode).length() < 8) {
            pairingCode = PairingToken.generate();
        }
        if (Double.isNaN(letterboxRatio) || letterboxRatio < 0) {
            letterboxRatio = 0;
        }
        letterboxRatio = Math.min(letterboxRatio, 4.0);
        if (allowedOrigins == null) {
            allowedOrigins = new ArrayList<>();
        }
        volumeBeforeMute = Math.clamp(volumeBeforeMute, 0.05, 1.0);
    }
}
