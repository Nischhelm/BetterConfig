package meldexun.betterconfig.api;

import meldexun.betterconfig.gui.configuration.ConfigurationGuiFactory;
import net.minecraftforge.common.config.Configuration;

public class GuiConfigurationRegistryAPI {
    /**
     * ModIds and their configurations registered here will be accessible in the forge mod config.
     * Changes will be saved to file, however there will be no changes to the current game mechanics
     * @param modid the modid of the targeted mod
     * @param configName a name to give the configuration if there are multiple configurations/files for this mod
     * @param cfg the Configuration object
     */
    public static void registerConfiguration(String modid, String configName, Configuration cfg){
        ConfigurationGuiFactory.registerConfiguration(modid, configName, cfg);
    }

    /**
     * If configurations are registered via their file location, comments, default values and ranges won't be accessible
     * @param cfgLocation from /config so an example would be MyMod/mymod.cfg or mymod.cfg
     */
    public static void registerConfiguration(String modid, String configName, String cfgLocation){
        ConfigurationGuiFactory.registerConfiguration(modid, configName, cfgLocation);
    }
}
