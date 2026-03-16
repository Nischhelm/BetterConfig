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

	abstract String toString(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance);

	boolean isDefault(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) {
		if (metadata == null || !metadata.hasDefaultValue()) {
			return false;
		}
		return this.toString(settings, type, metadata, instance).equals(TypeUtil.toString(type, metadata.defaultValue()));
	}

	abstract boolean isConfigTypeEqual(Type type);

	abstract void read(ConfigReader reader) throws IOException;

	abstract void write(ConfigWriter writer, BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) throws IOException;

	abstract void saveToConfig(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance);

	abstract Object loadFromConfig(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance);

}
