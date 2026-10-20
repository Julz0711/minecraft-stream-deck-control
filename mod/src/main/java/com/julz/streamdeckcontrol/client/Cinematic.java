package com.julz.streamdeckcontrol.client;

import com.julz.streamdeckcontrol.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Cinematic mode: vanilla smooth camera, black letterbox bars and (optionally) no HUD. */
public final class Cinematic {
    private static boolean active;
    private static boolean smoothCameraBefore;
    private static boolean hidHud;

    /** How long the letterbox bars take to fade in or out. */
    private static final long FADE_NANOS = 600_000_000L;
    /** Bar progress (0 = gone, 1 = fully in) when cinematic mode was last switched, and when that was. */
    private static double fadeFrom;
    private static long fadeStart;

    private Cinematic() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void set(Minecraft client, boolean on) {
        if (on == active) {
            return;
        }
        fadeFrom = barProgress(System.nanoTime());
        fadeStart = System.nanoTime();
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
        fadeFrom = 0;
    }

    /** How far the bars have faded in, eased so they start and finish gently. */
    static double barProgress(long now) {
        double t = Math.clamp((now - fadeStart) / (double) FADE_NANOS, 0.0, 1.0);
        double eased = t * t * (3 - 2 * t);
        double target = active ? 1.0 : 0.0;
        return fadeFrom + (target - fadeFrom) * eased;
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
        if (client.level == null) {
            return;
        }
        double progress = barProgress(System.nanoTime());
        if (progress <= 0) {
            return;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        // The bars slide in from the edges and darken as they come, like a film letterbox
        int bar = (int) Math.round(barHeight(width, height, ModConfig.getInstance().letterboxRatio) * progress);
        if (bar > 0) {
            int color = (int) Math.round(progress * 255) << 24;
            graphics.fill(0, 0, width, bar, color);
            graphics.fill(0, height - bar, width, height, color);
        }
    }
}
