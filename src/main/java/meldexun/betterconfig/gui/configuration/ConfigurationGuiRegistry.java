package meldexun.betterconfig.gui.configuration;

import meldexun.betterconfig.asm.BetterConfigPlugin;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Configuration;

import java.util.HashMap;
import java.util.Map;

public class ConfigurationGuiRegistry {

    private static final String FILE_ENDING = "\\.\\w+$";

    private static final Map<String, Map<String, Configuration>> registeredConfigurations = new HashMap<>();

    public static void registerConfiguration(Configuration cfg) {
        if(!BetterConfigPlugin.coreModInitiationComplete) return;
        String modid = ConfigurationGuiFactory.getCurrentModId();
        if(modid.isEmpty()) return; //forge etc
        if(ConfigManager.hasConfigForMod(modid)) return;

        // remove .cfg, .json etc
        String configName = cfg.getConfigFile().getName().replaceFirst(FILE_ENDING, "");

        registeredConfigurations.computeIfAbsent(modid, m -> new HashMap<>()).put(configName, cfg);
    }

    public static void save(String modid){
        if(registeredConfigurations.containsKey(modid)) {
            registeredConfigurations.get(modid).values().forEach(Configuration::save);
        }
    }

    public static Map<String, Configuration> get(String modid){
        return registeredConfigurations.get(modid);
    }

    public static boolean hasGuiFor(String modid) {
        return registeredConfigurations.containsKey(modid);
    }

    public static void unregister(String modId) {
        registeredConfigurations.remove(modId);
    }
}
