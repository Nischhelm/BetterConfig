package meldexun.betterconfig;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import meldexun.betterconfig.api.BetterConfig;

class ConfigList extends ConfigElement {

	private final List<ConfigElement> list = new ArrayList<>();

	void clear() {
		this.list.clear();
	}

	@Override
	String toString(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) {
		String s = TypeUtil.toString(type, this);
		String s1 = this.list.stream()
				.map(el -> el.toString(settings, type, metadata, instance)) //TODO: prob needs to unwrap metadata
				.collect(Collectors.joining(", ", "[", "]"));;
		return this.list.stream()
				.map(el -> el.toString(settings, type, metadata, instance)) //TODO: prob needs to unwrap metadata
				.collect(Collectors.joining(", ", "[", "]"));
	}

	@Override
	boolean isConfigTypeEqual(Type type) {
		return ConfigUtil.isList(type);
	}

	@Override
	void read(ConfigReader reader) throws IOException {
		if (!reader.readLine().equals("<")) {
			throw new IllegalArgumentException();
		}
		this.list.clear();
		while (!reader.readLineIfEqual(">")) {
			ConfigElement element;
			if (reader.peekLine().equals("{")) {
				element = new ConfigCategory();
			} else if (reader.peekLine().equals("<")) {
				element = new ConfigList();
			} else {
				element = new ConfigValue();
			}
			element.read(reader);
			this.list.add(element);
		}
	}

	@Override
	void write(ConfigWriter writer, BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) throws IOException {
		Objects.requireNonNull(type);
		Objects.requireNonNull(instance);
		if (!ConfigUtil.isList(type)) {
			throw new IllegalArgumentException();
		}

		writer.writeLine('<');
		writer.incrementIndentation();
		Type elementType = TypeUtil.getComponentOrElementType(type);
		for (ConfigElement child : this.list) {
			child.write(writer, settings, elementType, null, TypeUtil.newInstance(elementType)); // TODO compute metadata for list elements?
			writer.newLine();
		}
		writer.decrementIndentation();
		writer.write('>');
	}

	@Override
	void saveToConfig(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(instance);
		if (!ConfigUtil.isList(type)) {
			throw new IllegalArgumentException();
		}

		if (TypeUtil.isArray(type)) {
			this.list.clear();
			Type componentType = TypeUtil.getComponentType(type);

			for (int i = 0; i < Array.getLength(instance); i++) {
				ConfigElement element = ConfigElement.create(componentType);
				element.saveToConfig(settings, componentType, null, Array.get(instance, i)); // TODO compute metadata for list elements?
				this.list.add(element);
			}
		} else if (TypeUtil.isCollection(type)) {
			this.list.clear();
			Type elementType = TypeUtil.getElementType(type);

			for (Object value : (Collection<?>) instance) {
				ConfigElement element = ConfigElement.create(elementType);
				element.saveToConfig(settings, elementType, null, value); // TODO compute metadata for list elements?
				this.list.add(element);
			}
		} else {
			throw new IllegalArgumentException();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	Object loadFromConfig(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(instance);
		if (!ConfigUtil.isList(type)) {
			throw new IllegalArgumentException();
		}

		if (TypeUtil.isArray(type)) {
			Type componentType = TypeUtil.getComponentType(type);

			Object array = Array.newInstance(TypeUtil.getRawType(componentType), this.list.size());
			for (int i = 0; i < this.list.size(); i++) {
				if (!this.list.get(i).isConfigTypeEqual(componentType)) {
					continue;
				}
				Array.set(array, i, this.list.get(i).loadFromConfig(settings, componentType, null, TypeUtil.newInstance(componentType))); // TODO compute metadata for list elements?
			}

			return array;
		} else if (TypeUtil.isCollection(type)) {
			Type elementType = TypeUtil.getElementType(type);

			Collection<Object> collection = (Collection<Object>) TypeUtil.newInstance(type, instance);
			for (ConfigElement value : this.list) {
				if (!value.isConfigTypeEqual(elementType)) {
					continue;
				}
				collection.add(value.loadFromConfig(settings, elementType, null, TypeUtil.newInstance(elementType))); // TODO compute metadata for list elements?
			}

			return collection;
		} else {
			throw new IllegalArgumentException();
		}
	}

}
