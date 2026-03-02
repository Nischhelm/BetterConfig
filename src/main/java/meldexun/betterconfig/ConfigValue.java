package meldexun.betterconfig;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

import javax.annotation.Nullable;

class ConfigValue extends ConfigElement {

	String value;

	ConfigValue(Config config, DefaultSupplier<Type> type) {
		super(config, type);
		if (!ConfigUtil.isValue(this.type.getOrDefault())) {
			throw new IllegalArgumentException();
		}
		this.value = TypeAdapters.get(this.type.getOrDefault()).defaultSerializedValue();
	}

	@Override
	void read(ConfigReader reader) throws IOException {
		String value = reader.readLine();
		if (!TypeAdapters.get(this.type.getOrDefault()).isSerializedValue(value)) {
			throw new IllegalArgumentException();
		}
		this.value = value;
	}

	@Override
	void write(ConfigWriter writer) throws IOException {
		writer.write(this.value);
	}

	@Override
	void saveToConfig(Type type, @Nullable Object instance) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(instance);
		if (!ConfigUtil.isValue(type)) {
			throw new IllegalArgumentException();
		}
		this.type.set(type);
		this.value = TypeAdapters.get(type).serialize(instance);
	}

	@Override
	Object loadFromConfig(Type type, @Nullable Object instance) {
		Objects.requireNonNull(type);
		if (!ConfigUtil.isValue(type) || this.type.existsAndNotEqual(type)) {
			return instance;
		}
		return TypeAdapters.get(type).deserialize(this.value);
	}

}
