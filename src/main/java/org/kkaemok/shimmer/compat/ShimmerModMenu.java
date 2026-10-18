package org.kkaemok.shimmer.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.kkaemok.shimmer.gui.ShimmerConfigScreen;

/** Loaded only when Mod Menu requests its optional entrypoint. */
public final class ShimmerModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ShimmerConfigScreen::new;
    }
}
