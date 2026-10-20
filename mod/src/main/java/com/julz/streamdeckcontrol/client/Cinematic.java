package com.julz.streamdeckcontrol.client;

import com.julz.streamdeckcontrol.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Cinematic mode: vanilla smooth camera, black letterbox bars and (optionally) no HUD. */
public final class Cinematic {
    private static boolean active;
    private static boolean smoothCameraBefore;
    private static boolean hidHud;

    private Cinematic() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void set(Minecraft client, boolean on) {
        if (on == active) {
            return;
        }
        active = on;
        if (on) {
            smoothCameraBefore = client.options.smoothCamera;
            client.options.smoothCamera = true;
            hidHud = ModConfig.getInstance().cinematicHidesHud && !client.gui.hud.isHidden();
            if (hidHud) {
                client.gui.hud.toggle();
            }
        } else {
            client.options.smoothCamera = smoothCameraBefore;
            // Only bring the HUD back if it is still hidden; the player may have pressed F1 meanwhile.
            if (hidHud && client.gui.hud.isHidden()) {
                client.gui.hud.toggle();
            }
            hidHud = false;
        }
    }

    /** Ends cinematic mode when leaving a world, so the next world starts normal. */
    public static void reset(Minecraft client) {
        if (active) {
            set(client, false);
        }
    }

    /** Height of each bar in GUI pixels for a screen of the given size. */
    public static int barHeight(int width, int height, double ratio) {
        if (ratio <= 0 || width <= 0) {
            return 0;
        }
        int picture = (int) Math.round(width / ratio);
        return Math.max(0, (height - picture) / 2);
    }

    /** Called after the HUD is drawn; screens and toasts are drawn on top of the bars. */
    public static void drawLetterbox(Minecraft client, GuiGraphicsExtractor graphics) {
        if (!active || client.level == null) {
            return;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        int bar = barHeight(width, height, ModConfig.getInstance().letterboxRatio);
        if (bar > 0) {
            graphics.fill(0, 0, width, bar, 0xFF000000);
            graphics.fill(0, height - bar, width, height, 0xFF000000);
        }
    }
}
