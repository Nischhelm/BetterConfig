package meldexun.betterconfig.gui.configuration;

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
import java.lang.reflect.Field;
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

    private static Field configCategory$children = null;

    public static class ConfigurationGui extends GuiConfig {
        public ConfigurationGui(GuiScreen parentScreen, String modid, Map<String, Configuration> cfg) {
            super(parentScreen, getConfigElements(cfg), modid, false, false, Loader.instance().getIndexedModList().get(modid).getName());
        }

        private static List<IConfigElement> getConfigElements(Map<String, Configuration> map) {
            if(configCategory$children == null) {
                try {
                    configCategory$children = ConfigCategory.class.getDeclaredField("children");
                    configCategory$children.setAccessible(true);
                } catch (NoSuchFieldException | SecurityException e){
                    System.err.println("Failed to access field ConfigCategory.children");
                    return new ArrayList<>();
                }
            }

            if(map.size() > 1) {
                List<IConfigElement> elements = new ArrayList<>();
                for (Map.Entry<String, Configuration> entry : map.entrySet()) {
                    Configuration cfg = entry.getValue();
                    ConfigCategory cat = new ConfigCategory(entry.getKey()); //the configuration gets its own category, as there are multiple for this modid
                    try {
                        ((ArrayList<ConfigCategory>) configCategory$children.get(cat)).addAll(cfg.getCategoryNames().stream().map(cfg::getCategory).collect(Collectors.toList()));
                        elements.add(new ConfigElement(cat));
                    } catch (IllegalAccessException | ClassCastException e) {
                        System.err.println("Failed to access field ConfigCategory.children");
                    }
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

    private static final Set<String> excludedModids = new HashSet<>();
    public static void excludeModId(String modid) {
        excludedModids.add(modid);
    }
    private static final Map<String, Map<String, Configuration>> registeredConfigurations = new HashMap<>();
    public static boolean hasGuiFor(String modid) {
        boolean hasGui = registeredConfigurations.containsKey(modid);
        if(hasGui) currentModId = modid;
        return hasGui;
    }
    public static void registerConfiguration(String modid, String configName, Configuration cfg){
        if(excludedModids.contains(modid)) return;
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
