package meldexun.betterconfig.gui.configuration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import meldexun.betterconfig.asm.BetterConfigPlugin;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

public class ConfigurationGuiRegistry {

	private static final Map<String, Map<File, Configuration>> registeredConfigurations = new HashMap<>();

	public static void registerConfiguration(Configuration cfg) {
		if (!BetterConfigPlugin.coreModInitiationComplete) {
			return;
		}
		ModContainer modContainer = Loader.instance().activeModContainer();
		if (modContainer == null) {
			return;
		}
		String modid = modContainer.getModId();
		if (ConfigManager.hasConfigForMod(modid)) {
			return;
		}

		registeredConfigurations.computeIfAbsent(modid, k -> new HashMap<>()).put(cfg.getConfigFile().getAbsoluteFile(), cfg);
	}

	public static void save(String modid) {
		if (registeredConfigurations.containsKey(modid)) {
			registeredConfigurations.get(modid).values().forEach(Configuration::save);
		}
	}

	public static Map<File, Configuration> get(String modid) {
		return registeredConfigurations.get(modid);
	}

	public static boolean hasGuiFor(String modid) {
		return registeredConfigurations.containsKey(modid);
	}

	public static void unregister(String modId) {
		registeredConfigurations.remove(modId);
	}

}
