package com.julz.streamdeckcontrol.mixin;

import com.julz.streamdeckcontrol.client.Cinematic;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    /** Draws the cinematic letterbox right after the HUD, which also works while the HUD is hidden. */
    @WrapOperation(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void streamDeckControl$drawLetterbox(Hud hud, GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
        original.call(hud, graphics, delta);
        Cinematic.drawLetterbox(this.minecraft, graphics);
    }
}
