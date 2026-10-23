package com.julz.streamdeckcontrol.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reaches what F3+A uses to reload chunks. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("levelExtractor")
    LevelExtractor streamDeckControl$levelExtractor();
}
