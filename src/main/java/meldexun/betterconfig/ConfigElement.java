package meldexun.betterconfig;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

import javax.annotation.Nullable;

abstract class ConfigElement {

	final DefaultSupplier<Type> type;
	int order;
	String[] comment;

	ConfigElement(DefaultSupplier<Type> type) {
		this.type = Objects.requireNonNull(type).copy();
	}

	static ConfigElement create(Type type) {
		return create(DefaultSupplier.of(type));
	}

	static ConfigElement create(DefaultSupplier<Type> type) {
		if (ConfigUtil.isValue(type.getOrDefault())) {
			return new ConfigValue(type);
		}
		if (ConfigUtil.isList(type.getOrDefault())) {
			return new ConfigList(type);
		}
		return new ConfigCategory(type);
	}

	boolean isConfigTypeEqual(Type type) {
		return ConfigUtil.isConfigTypeEqual(this.type.getOrDefault(), type);
	}

	abstract void read(ConfigReader reader) throws IOException;

	abstract void write(BufferedWriter writer, int indent) throws IOException;

	abstract void saveToConfig(Type type, @Nullable Object instance);

	abstract Object loadFromConfig(Type type, @Nullable Object instance);

	void order(int order) {
		this.order = order;
	}

	int order() {
		return this.order;
	}

	protected void setComment(String[] comment) {
		this.comment = comment;
	}
}
