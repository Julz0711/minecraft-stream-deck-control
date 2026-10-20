package com.julz.streamdeckcontrol.compat;

import com.julz.streamdeckcontrol.client.StreamDeckControlConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Adds the config button in Mod Menu. Only loaded by Mod Menu itself. */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return StreamDeckControlConfigScreen::new;
    }
}
