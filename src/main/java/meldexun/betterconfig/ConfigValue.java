package meldexun.betterconfig;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

import javax.annotation.Nullable;

import meldexun.betterconfig.api.BetterConfig;

class ConfigValue extends ConfigElement {

	private String value = "";

	@Override
	String toString(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) {
		return value;
	}

	@Override
	boolean isConfigTypeEqual(Type type) {
		return ConfigUtil.isValue(type);
	}

	@Override
	void read(ConfigReader reader) throws IOException {
		this.value = reader.readLine();
	}

	@Override
	void write(ConfigWriter writer, BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) throws IOException {
		Objects.requireNonNull(type);
		Objects.requireNonNull(instance);
		if (!ConfigUtil.isValue(type)) {
			throw new IllegalArgumentException();
		}

		writer.write(this.value);
	}

	@Override
	void saveToConfig(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(instance);
		if (!ConfigUtil.isValue(type)) {
			throw new IllegalArgumentException();
		}

		this.value = TypeAdapters.get(type).serialize(instance);
	}

	@Override
	Object loadFromConfig(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(instance);
		if (!ConfigUtil.isValue(type)) {
			throw new IllegalArgumentException();
		}

		return TypeAdapters.get(type).deserialize(this.value);
	}

}
