package meldexun.betterconfig.gui.configuration;

import meldexun.betterconfig.mixin.configuration.ConfigCategoryAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class ConfigurationGuiFactory implements IModGuiFactory {
    public static final ConfigurationGuiFactory INSTANCE = new ConfigurationGuiFactory();

    @Override public void initialize(Minecraft mc) {}
    @Override public boolean hasConfigGui() { return true; }
    @Override public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() { return null; }

    public static String currentModId = null;
    @Override public GuiScreen createConfigGui(GuiScreen parent) {
        return new ConfigurationGui(parent, currentModId, registeredConfigurations.get(currentModId));
    }

    public static class ConfigurationGui extends GuiConfig {
        public ConfigurationGui(GuiScreen parentScreen, String modid, Map<String, Configuration> cfg) {
            super(parentScreen, getConfigElements(cfg), modid, false, false, Loader.instance().getIndexedModList().get(modid).getName());
        }

        private static List<IConfigElement> getConfigElements(Map<String, Configuration> map) {
            if(map.size() > 1) {
                List<IConfigElement> elements = new ArrayList<>();
                for (Map.Entry<String, Configuration> entry : map.entrySet()) {
                    Configuration cfg = entry.getValue();
                    ConfigCategory cat = new ConfigCategory(entry.getKey()); //the configuration gets its own category, as there are multiple for this modid
                    ((ConfigCategoryAccessor)cat).getChildrenList().addAll(cfg.getCategoryNames().stream().map(cfg::getCategory).collect(Collectors.toList()));
                    elements.add(new ConfigElement(cat));
                }
                return elements;
            } else {
                Configuration cfg = map.get(map.keySet().toArray(new String[0])[0]); //config name doesn't matter if its only one, we skip one category step in the gui
                return cfg.getCategoryNames().stream()
                        .map(cfg::getCategory)
                        .map(cat -> (IConfigElement) new ConfigElement(cat))
                        .collect(Collectors.toList());
            }
        }
    }

    private static final Map<String, Map<String, Configuration>> registeredConfigurations = new HashMap<>();
    public static boolean hasGuiFor(String modid) {
        boolean hasGui = registeredConfigurations.containsKey(modid);
        if(hasGui) currentModId = modid;
        return hasGui;
    }
    public static void registerConfiguration(String modid, String configName, Configuration cfg){
        registeredConfigurations.computeIfAbsent(modid, m -> new HashMap<>()).put(configName == null ? "general" : configName, cfg);
    }
    public static void registerConfiguration(String modid, String configName, String cfgLocation){
        registerConfiguration(modid, configName, new Configuration(new File(Loader.instance().getConfigDir(), cfgLocation)));
    }
    public static void save(String modid){
        if(registeredConfigurations.containsKey(modid)) {
            registeredConfigurations.get(modid).values().forEach(Configuration::save);
        }
    }
}
