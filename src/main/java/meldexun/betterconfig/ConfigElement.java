package meldexun.betterconfig;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

import javax.annotation.Nullable;

abstract class ConfigElement {

	final Config config;
	final DefaultSupplier<Type> type;
	int order;

	ConfigElement(Config config, DefaultSupplier<Type> type) {
		this.config = config;
		this.type = Objects.requireNonNull(type).copy();
	}

	static ConfigElement create(Config config, Type type) {
		return create(config, DefaultSupplier.of(type));
	}

	static ConfigElement create(Config config, DefaultSupplier<Type> type) {
		if (ConfigUtil.isValue(type.getOrDefault())) {
			return new ConfigValue(config, type);
		}
		if (ConfigUtil.isList(type.getOrDefault())) {
			return new ConfigList(config, type);
		}
		return new ConfigCategory(config, type);
	}

	boolean isConfigTypeEqual(Type type) {
		return ConfigUtil.isConfigTypeEqual(this.type.getOrDefault(), type);
	}

	abstract void read(ConfigReader reader) throws IOException;

	abstract void write(ConfigWriter writer) throws IOException;

	abstract void saveToConfig(Type type, @Nullable Object instance);

	abstract Object loadFromConfig(Type type, @Nullable Object instance);

	void order(int order) {
		this.order = order;
	}

	int order() {
		return this.order;
	}

}
