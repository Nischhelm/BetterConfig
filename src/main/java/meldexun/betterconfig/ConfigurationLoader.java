package meldexun.betterconfig;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.regex.Matcher;

import org.apache.commons.lang3.ObjectUtils;

public class ConfigurationLoader {

	public static ConfigCategory load(File file) throws IOException {
		ConfigCategory cfg = new ConfigCategory(DefaultSupplier.fallback(Map.class));
		if (file.exists()) {
			try (ConfigReader reader = new ConfigReader(Files.newBufferedReader(file.toPath()))) {
				while (reader.hasNext()) {
					Matcher matcher;
					if ((matcher = reader.readMatching(ConfigCategory.CATEGORY)) != null) {
						String name = ObjectUtils.defaultIfNull(matcher.group(1), matcher.group(2));
						ConfigCategory subcategory = new ConfigCategory(DefaultSupplier.fallback(Map.class));
						subcategory.read(reader);
						cfg.subcategories.put(name, subcategory);
					} else {
						throw new IllegalArgumentException();
					}
				}
			}
		}
		return cfg;
	}

	public static void save(ConfigCategory cfg, File file) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(file.toPath())) {

			writer.write("# Better Configuration file");
			writer.newLine();
			writer.newLine();
			//TODO: Configurations would also write config version here, maybe a cool feature to add that as an annotation or smth

			for (Map.Entry<String, ConfigCategory> entry : cfg.subcategoriesSorted()) {
				ConfigCategory.writeEntry(writer, 0, entry.getKey(), entry.getValue(), true);
				writer.newLine();
			}
		}
	}

	static void indent(BufferedWriter writer, int indent) throws IOException {
		for (int i = 0; i < indent; i++) {
			writer.write("    ");
		}
	}

}
