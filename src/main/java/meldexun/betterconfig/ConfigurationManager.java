package meldexun.betterconfig;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import meldexun.betterconfig.api.BetterConfig;
import meldexun.betterconfig.gui.EntryInfo;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.Config.Type;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderException;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.discovery.ASMDataTable.ASMData;

@SuppressWarnings("unchecked")
public class ConfigurationManager {

	private static final Map<File, ConfigCategory> BETTER_CONFIGS = new HashMap<>();
	private static final Multimap<File, String> LOADED_CATEGORIES = HashMultimap.create();

	private static final Map<String, Multimap<Config.Type, ASMData>> asm_data;
	private static final Map<String, Set<Class<?>>> MOD_CONFIG_CLASSES;
	private static final Map<String, Configuration> CONFIGS;
	private static final Method sync;
	static {
		try {
			Field f;

			f = ConfigManager.class.getDeclaredField("asm_data");
			f.setAccessible(true);
			asm_data = (Map<String, Multimap<Type, ASMData>>) f.get(null);

			f = ConfigManager.class.getDeclaredField("MOD_CONFIG_CLASSES");
			f.setAccessible(true);
			MOD_CONFIG_CLASSES = (Map<String, Set<Class<?>>>) f.get(null);

			f = ConfigManager.class.getDeclaredField("CONFIGS");
			f.setAccessible(true);
			CONFIGS = (Map<String, Configuration>) f.get(null);

			sync = ConfigManager.class.getDeclaredMethod("sync", Configuration.class, Class.class, String.class, String.class, boolean.class, Object.class);
			sync.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new UnsupportedOperationException(e);
		}
	}

	public static void sync(String modid, Config.Type type) {
		FMLLog.log.debug("Attempting to inject @Config classes into {} for type {}", modid, type);
		ClassLoader mcl = Loader.instance().getModClassLoader();
		File configDir = Loader.instance().getConfigDir();
		Multimap<Config.Type, ASMData> map = asm_data.get(modid);

		if (map == null)
			return;

		for (ASMData targ : map.get(type)) {
			try {
				Class<?> cls = Class.forName(targ.getClassName(), true, mcl);

				MOD_CONFIG_CLASSES.computeIfAbsent(modid, k -> new HashSet<>()).add(cls);

				String name = (String) targ.getAnnotationInfo().get("name");
				if (name == null)
					name = modid;
				String category = (String) targ.getAnnotationInfo().get("category");
				if (category == null)
					category = "general";

				File file = new File(configDir, name + ".cfg");

				if (cls.isAnnotationPresent(BetterConfig.class)) {
					ConfigCategory cfg = BETTER_CONFIGS.get(file);
					if (cfg == null) {
						BETTER_CONFIGS.put(file, cfg = ConfigurationLoader.load(file));
					}

					ConfigCategory categoryElement = !category.isEmpty() ? getOrCreateCategory(cfg, category) : cfg;
					if (LOADED_CATEGORIES.put(file, category)) {
						EntryInfo.load(cls, null);
						categoryElement.loadFromConfig(cls, null);
					}

					categoryElement.saveToConfig(cls, null);
					ConfigurationLoader.save(cfg, file);
				} else {
					Configuration cfg = CONFIGS.get(file.getAbsolutePath());
					if (cfg == null) {
						cfg = new Configuration(file);
						cfg.load();
						CONFIGS.put(file.getAbsolutePath(), cfg);
					}

					sync.invoke(null, cfg, cls, modid, category, !Loader.instance().hasReachedState(LoaderState.AVAILABLE), null);

					cfg.save();
				}
			} catch (Exception e) {
				FMLLog.log.error("An error occurred trying to load a config for {} into {}", modid, targ.getClassName(), e);
				throw new LoaderException(e);
			}
		}
	}

	private static ConfigCategory getOrCreateCategory(ConfigCategory cfg, String name) {
		return cfg.subcategories.computeIfAbsent(name, k -> new ConfigCategory(DefaultSupplier.fallback(Map.class)));
	}

}
