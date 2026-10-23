package com.julz.streamdeckcontrol.client;

import com.julz.streamdeckcontrol.config.ModConfig;
import com.julz.streamdeckcontrol.core.ApiServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class StreamDeckControlConfigScreen extends OptionsSubScreen {
    private static final String PREFIX = "stream-deck-control.config.";
    /** Letterbox ratios offered by the slider, in hundredths. */
    private static final int MIN_RATIO = 0;
    private static final int MAX_RATIO = 300;

    private final Screen parent;
    private final OptionInstance<Boolean> pairingCode;
    private final OptionInstance<Boolean> newPairingCode;
    private final OptionInstance<Boolean> allowCommands;
    private final OptionInstance<Boolean> showPairingToasts;
    private final OptionInstance<Boolean> cinematicHidesHud;
    private final OptionInstance<Integer> letterboxRatio;

    public StreamDeckControlConfigScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(PREFIX + "title"));
        this.parent = parent;
        ModConfig config = ModConfig.getInstance();

        // Shows the code; clicking it copies it, so it can be pasted into the Stream Deck app.
        this.pairingCode = OptionInstance.createBoolean(
                PREFIX + "pairingCode",
                OptionInstance.cachedConstantTooltip(Component.translatable(PREFIX + "pairingCode.tooltip")),
                (caption, value) -> Component.translatable(PREFIX + "pairingCode.copy", ModConfig.getInstance().pairingCode),
                false,
                value -> StreamDeckControlClient.copyPairingCode(Minecraft.getInstance()));
        // Makes a new code, which unpairs every Stream Deck.
        this.newPairingCode = OptionInstance.createBoolean(
                PREFIX + "newPairingCode",
                OptionInstance.cachedConstantTooltip(Component.translatable(PREFIX + "newPairingCode.tooltip")),
                (caption, value) -> caption,
                false,
                value -> {
                    StreamDeckControlClient.newPairingCode();
                    // Reopen so the button shows the new code
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> client.gui.setScreen(new StreamDeckControlConfigScreen(this.parent)));
                });
        this.allowCommands = booleanOption("allowCommands", config.allowCommands);
        this.showPairingToasts = booleanOption("showPairingToasts", config.showPairingToasts);
        this.cinematicHidesHud = booleanOption("cinematicHidesHud", config.cinematicHidesHud);
        this.letterboxRatio = new OptionInstance<>(
                PREFIX + "letterboxRatio",
                OptionInstance.cachedConstantTooltip(Component.translatable(PREFIX + "letterboxRatio.tooltip")),
                (caption, v) -> v < 100
                        ? Options.genericValueLabel(caption, Component.translatable(PREFIX + "letterboxRatio.off"))
                        : Options.genericValueLabel(caption, Component.literal(String.format(Locale.ROOT, "%.2f:1", v / 100.0))),
                new OptionInstance.IntRange(MIN_RATIO, MAX_RATIO),
                Math.clamp((int) Math.round(config.letterboxRatio * 100), MIN_RATIO, MAX_RATIO),
                v -> {
                });
    }

    @Override
    protected void addOptions() {
        this.list.addSmall(this.pairingCode, this.newPairingCode);
        this.list.addSmall(this.allowCommands, this.showPairingToasts);
        this.list.addHeader(Component.translatable(PREFIX + "cinematic"));
        this.list.addBig(this.letterboxRatio);
        this.list.addBig(this.cinematicHidesHud);
        ApiServer server = StreamDeckControlClient.server();
        int port = server == null ? -1 : server.port();
        this.list.addHeader(port > 0
                ? Component.translatable(PREFIX + "status", port, server.clientCount())
                : Component.translatable(PREFIX + "status.off"));
    }

    @Override
    public void removed() {
        super.removed();
        ModConfig config = ModConfig.getInstance();
        config.allowCommands = this.allowCommands.get();
        config.showPairingToasts = this.showPairingToasts.get();
        config.cinematicHidesHud = this.cinematicHidesHud.get();
        int ratio = this.letterboxRatio.get();
        config.letterboxRatio = ratio < 100 ? 0 : ratio / 100.0;
        config.sanitize();
        ModConfig.save();
    }

    private static OptionInstance<Boolean> booleanOption(String name, boolean value) {
        return OptionInstance.createBoolean(PREFIX + name,
                OptionInstance.cachedConstantTooltip(Component.translatable(PREFIX + name + ".tooltip")),
                value);
    }
}
