package meldexun.betterconfig;

import java.io.IOException;
import java.lang.reflect.Type;

import javax.annotation.Nullable;

import meldexun.betterconfig.api.BetterConfig;

abstract class ConfigElement {

	static ConfigElement create(Type type) {
		if (ConfigUtil.isValue(type)) {
			return new ConfigValue();
		}
		if (ConfigUtil.isList(type)) {
			return new ConfigList();
		}
		return new ConfigCategory();
	}

	boolean isDefault() {
		return false;
	}

	abstract boolean isConfigTypeEqual(Type type);

	abstract void read(ConfigReader reader) throws IOException;

	abstract void write(ConfigWriter writer, BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) throws IOException;

	abstract void saveToConfig(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance);

	abstract Object loadFromConfig(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance);

}
