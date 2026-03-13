package meldexun.betterconfig.gui.configuration;

import com.google.common.collect.Iterables;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConfigurationGuiFactory implements IModGuiFactory {
    public static final ConfigurationGuiFactory INSTANCE = new ConfigurationGuiFactory();

    @Override
    public void initialize(Minecraft mc) {}

    @Override
    public boolean hasConfigGui() { return true; }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    private static String currentModId = null;

    public static boolean hasGuiFor(String modid) {
        boolean hasGui = ConfigurationGuiRegistry.hasGuiFor(modid);
        if(hasGui) currentModId = modid;
        return hasGui;
    }

    @Override public GuiScreen createConfigGui(GuiScreen parent) {
        return new ConfigurationGui(parent, currentModId, ConfigurationGuiRegistry.get(currentModId));
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

            if(map.size() > 1) { //multiple cfg files for this modid
                List<IConfigElement> elements = new ArrayList<>();
                for (Map.Entry<String, Configuration> entry : map.entrySet()) {
                    String cfgFileName = entry.getKey();
                    Configuration cfg = entry.getValue();
                    try {
                        ConfigCategory newCategory = new ConfigCategory(cfgFileName); //each configuration gets its own category, as there are multiple for this modid
                        @SuppressWarnings("unchecked")
                        ArrayList<ConfigCategory> newCatChildren = (ArrayList<ConfigCategory>) configCategory$children.get(newCategory);

                        List<ConfigCategory> cfgChildCategories = getCategoriesStream(cfg).collect(Collectors.toList());
                        if(cfgChildCategories.size() > 1) { //multiple main categories in the file, display a screen to select from them
                            newCatChildren.addAll(cfgChildCategories); //attach all ConfigCategories of the Configuration to the new parent ConfigCategory
                        } else if(cfgChildCategories.size() == 1) { //file has only one category, skip right into its contents
                            //Same effect as just renaming the existing Category from ex "general" to cfgFileName but prob slightly safer
                            ConfigCategory onlyCategory = cfgChildCategories.get(0);
                            newCategory.putAll(onlyCategory);
                            newCatChildren.addAll(onlyCategory.getChildren());
                        } //don't add if file empty
                        elements.add(new ConfigElement(newCategory));
                    } catch (IllegalAccessException | ClassCastException e) {
                        System.err.println("Failed to access field ConfigCategory.children");
                    }
                }
                return elements;
            } else { // only one file, skip right into it, no need to display cfgFileName selection
                Configuration cfg = Iterables.getOnlyElement(map.values()); //config filename doesn't matter if its only one, we skip one category step in the gui
                if(getRelevantCategoryNames(cfg).count() > 1) { // multiple main categories in the file, display a screen to select from them
                    return getCategoriesStream(cfg)
                            .map(cat -> (IConfigElement) new ConfigElement(cat))
                            .collect(Collectors.toList());
                } else { // only one category in the file, skip right into it without displaying the category name
                    return new ConfigElement(cfg.getCategory(getRelevantCategoryNames(cfg).collect(Collectors.toList()).get(0))).getChildElements();
                }
            }
        }

        private static Stream<String> getRelevantCategoryNames(Configuration cfg){
            return cfg.getCategoryNames().stream().filter(name -> !name.contains(".")); //getCategoryNames also returns subcategories, we only want the outermost ones
        }

        private static Stream<ConfigCategory> getCategoriesStream(Configuration cfg) {
            return getRelevantCategoryNames(cfg)
                    .map(cfg::getCategory)
                    .filter(cat -> !cat.isEmpty() || !cat.getChildren().isEmpty()); // keep only if the category isn't empty
        }
    }

    public static String getCurrentModId(){
        ModContainer modContainer = Loader.instance().activeModContainer();
        if(modContainer == null) return "";
        return modContainer.getModId();
    }
}
