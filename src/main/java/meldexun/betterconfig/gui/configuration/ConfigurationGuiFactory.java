package meldexun.betterconfig.gui.configuration;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

public class ConfigurationGuiFactory implements IModGuiFactory {

    private final String modId;
    private final String modName;

    public ConfigurationGuiFactory(String modId, String modName) {
        this.modId = modId;
        this.modName = modName;
    }

    @Override
    public void initialize(Minecraft mc) {

    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parent) {
        return new GuiConfig(parent, getConfigElements(ConfigurationGuiRegistry.get(this.modId)), this.modId, false, false, this.modName);
    }

    private static final Field configCategory_children;
    static {
        try {
            configCategory_children = ConfigCategory.class.getDeclaredField("children");
            configCategory_children.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ConfigCategory> getChildCategoryList(ConfigCategory configCategory) {
        try {
            return (List<ConfigCategory>) configCategory_children.get(configCategory);
        } catch (IllegalAccessException e){
            throw new UnsupportedOperationException(e);
        }
    }

    private static List<IConfigElement> getConfigElements(Map<String, Configuration> map) {
        List<ConfigCategory> fileCategories = map.entrySet()
                .stream()
                .map(e -> {
                    String cfgFileName = e.getKey();
                    Configuration cfg = e.getValue();
                    List<ConfigCategory> categoriesInFile = cfg.getCategoryNames()
                            .stream()
                            .map(cfg::getCategory)
                            .filter(cat -> !cat.isChild()) // cfg stores subcategory names too, so gotta filter those
                            .filter(cat -> !cat.isEmpty() || !cat.getChildren().isEmpty()) // ignore empty categories
                            .collect(Collectors.toList());

                    while(categoriesInFile.size() == 1 && categoriesInFile.get(0).isEmpty()) {
                        categoriesInFile = getChildCategoryList(categoriesInFile.get(0)); // skip steps with only a single subcategory option in the GUI
                    }

                    ConfigCategory newCategory = new ConfigCategory(cfgFileName); // each file gets its own category, might be multiple per modid
                    getChildCategoryList(newCategory).addAll(categoriesInFile); // move the categories of the cfg object to the new category
                    return newCategory;
                })
                .collect(Collectors.toList());

        if (fileCategories.size() == 1) {
            fileCategories = getChildCategoryList(fileCategories.get(0)); // skip the file selection if there's only one file
        }

        return fileCategories
                .stream()
                .map(ConfigElement::new)
                .collect(Collectors.toList());
    }

    public static String getCurrentModId(){
        ModContainer modContainer = Loader.instance().activeModContainer();
        if(modContainer == null) {
            return "";
        }
        return modContainer.getModId();
    }

}
