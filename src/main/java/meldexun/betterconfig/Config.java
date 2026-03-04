package meldexun.betterconfig;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ObjectUtils;

import meldexun.betterconfig.api.BetterConfig;
import meldexun.betterconfig.gui.EntryInfo;

class Config {

	final Type type;
	final EntryInfo info;
	final BetterConfig settings;
	final ConfigCategory root = new ConfigCategory(this, DefaultSupplier.fallback(Map.class));

	Config(Path file, Type type) throws IOException {
		this.type = type;
		this.info = EntryInfo.create(TypeUtil.getRawType(type));
		this.settings = TypeUtil.getRawType(type).getAnnotation(BetterConfig.class);
		if (Files.exists(file)) {
			try (ConfigReader reader = new ConfigReader(Files.newBufferedReader(file))) {
				while (reader.hasNext()) {
					Matcher matcher;
					if ((matcher = reader.readMatching(ConfigCategory.CATEGORY)) != null) {
						String name = ObjectUtils.defaultIfNull(matcher.group(1), matcher.group(2));
						ConfigCategory subcategory = new ConfigCategory(this, DefaultSupplier.fallback(Map.class));
						subcategory.read(reader);
						this.root.subcategories.put(name, subcategory);
					} else {
						throw new IllegalArgumentException();
					}
				}
			}
		}
	}

	void save(Path file) throws IOException {
		try (ConfigWriter writer = new ConfigWriter(Files.newBufferedWriter(file))) {
			writer.writeCommentLine("Configuration file");
			writer.newLine();
			for (Map.Entry<String, ConfigCategory> entry : this.root.subcategoriesSorted()) {
				ConfigCategory.writeEntry(writer, entry.getKey(), entry.getValue());
				writer.newLine();
			}
		}
	}

	String name(@Nullable Object instance, Field field) {
		String name = EntryInfo.fromField(instance, field).name();
		if (this.settings.lowerCaseCategories() && ConfigUtil.isNonMapCategory(field.getGenericType())) {
			name = name.toLowerCase();
		}
		return name;
	}

}
