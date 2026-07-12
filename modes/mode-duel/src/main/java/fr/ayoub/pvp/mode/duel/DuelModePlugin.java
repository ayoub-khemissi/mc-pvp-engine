package fr.ayoub.pvp.mode.duel;

import fr.ayoub.pvp.api.PvPEngineApi;
import org.bukkit.plugin.java.JavaPlugin;

/** Registers the duel mode with the engine. That is the entire plugin. */
public final class DuelModePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        PvPEngineApi.modes().register(new DuelMode());
        getLogger().info("Duel mode registered.");
    }
}
