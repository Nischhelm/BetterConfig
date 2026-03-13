package meldexun.betterconfig.gui.configuration;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.DummyConfigElement.DummyCategoryElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

public class ConfigurationGuiFactory implements IModGuiFactory {

	private static final String FILE_ENDING = "\\.\\w+$";

	private final String modId;
	private final String modName;

	public ConfigurationGuiFactory(String modId, String modName) {
		this.modId = modId;
		this.modName = modName;
	}

	private static List<IConfigElement> getConfigElements(Map<File, Configuration> map) {
		List<IConfigElement> fileCategories = map.entrySet()
				.stream()
				.map(e -> {
					String cfgFileName = e.getKey().getName().replaceFirst(FILE_ENDING, ""); // TODO: could respect the folder structure here in the rare case of duplicated file names for the same mod in different folders
					Configuration cfg = e.getValue();
					List<IConfigElement> categoriesInFile = cfg.getCategoryNames()
							.stream()
							.map(cfg::getCategory)
							.filter(cat -> !cat.isChild()) // cfg stores subcategory names too, so gotta filter those
							.map(ConfigElement::new)
							.collect(Collectors.toList());

					categoriesInFile = unwrapSingleCategory(categoriesInFile); // skip step when having only a single category option in the GUI

					return new DummyCategoryElement(cfgFileName, cfgFileName, categoriesInFile); // each file gets its own category, might be multiple per modid
				})
				.collect(Collectors.toList());

		fileCategories = unwrapSingleCategory(fileCategories); //skip file select step if only one file

		return fileCategories;
	}

	private static List<IConfigElement> unwrapSingleCategory(List<IConfigElement> elements) {
		if(elements.size() == 1) {
			return elements.get(0).getChildElements();
		}
		return elements;
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

}
