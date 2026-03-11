package meldexun.betterconfig;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;

import org.apache.commons.lang3.ObjectUtils;

import meldexun.betterconfig.api.BetterConfig;

class Config {

	private static final BetterConfig DEFAULT_SETTINGS = new BetterConfig() {
		@Override
		public Class<? extends Annotation> annotationType() {
			return BetterConfig.class;
		}

		@Override
		public String modid() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String name() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String category() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean lowerCaseCategories() {
			return true;
		}

		@Override
		public boolean bigCategoryComments() {
			return true;
		}

		@Override
		public boolean addRangesToComments() {
			return true;
		}

		@Override
		public boolean addDefaultsToComments() {
			return true;
		}

		@Override
		public boolean removeDeprecatedEntries() {
			return false;
		}

		@Override
		public ConfigComparator[] elementOrder() {
			return null;
		}
	};
	private final ConfigCategory root = new ConfigCategory(DefaultSupplier.fallback(Map.class));

	void load(Path file) throws IOException {
		this.root.type.reset();
		this.root.info = null;
		this.root.elements.clear();
		this.root.subcategories.clear();
		if (Files.exists(file)) {
			try (ConfigReader reader = new ConfigReader(Files.newBufferedReader(file))) {
				while (reader.hasNext()) {
					Matcher matcher;
					if ((matcher = reader.readMatching(ConfigCategory.CATEGORY)) != null) {
						String name = ObjectUtils.defaultIfNull(matcher.group(1), matcher.group(2));
						this.getOrCreateCategory(name).read(reader);
					} else {
						throw new IllegalArgumentException();
					}
				}
			}
		}
	}

	void save(Path file, Function<String, BetterConfig> getCategorySettings) throws IOException {
		try (ConfigWriter writer = new ConfigWriter(Files.newBufferedWriter(file))) {
			writer.writeCommentLine("Configuration file");
			writer.newLine();
			BetterConfig rootSettings = ObjectUtils.defaultIfNull(getCategorySettings.apply(""), DEFAULT_SETTINGS);
			for (Map.Entry<String, ? extends ConfigElement> entry : this.root.elements(rootSettings.elementOrder())) {
				BetterConfig settings = ObjectUtils.defaultIfNull(getCategorySettings.apply(entry.getKey()), DEFAULT_SETTINGS);
				ConfigCategory.writeEntry(writer, settings, entry.getKey(), entry.getValue());
				writer.newLine();
				writer.newLine();
				writer.newLine();
			}
		}
	}

	ConfigCategory getOrCreateCategory(String categoryName) {
		if (categoryName.isEmpty()) {
			return this.root;
		}
		return this.root.subcategories.computeIfAbsent(categoryName, k -> new ConfigCategory(DefaultSupplier.fallback(Map.class)));
	}

}
