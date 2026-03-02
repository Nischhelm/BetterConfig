package meldexun.betterconfig;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

class ConfigList extends ConfigElement {

	final List<ConfigElement> list = new ArrayList<>();

	ConfigList(Config config, DefaultSupplier<Type> type) {
		super(config, type);
		if (!ConfigUtil.isList(this.type.getOrDefault())) {
			throw new IllegalArgumentException();
		}
	}

	@Override
	void read(ConfigReader reader) throws IOException {
		if (!reader.readLine().equals("<")) {
			throw new IllegalArgumentException();
		}
		while (!reader.readLineIfEqual(">")) {
			ConfigElement element = ConfigElement.create(this.config, this.type.map(TypeUtil::getComponentOrElementType));
			element.read(reader);
			this.list.add(element);
		}
	}

	@Override
	void write(ConfigWriter writer) throws IOException {
		writer.writeLine('<');
		writer.incrementIndentation();
		for (ConfigElement child : this.list) {
			child.write(writer);
			writer.newLine();
		}
		writer.decrementIndentation();
		writer.write('>');
	}

	@Override
	void saveToConfig(Type type, @Nullable Object instance) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(instance);
		if (!ConfigUtil.isList(type)) {
			throw new IllegalArgumentException();
		}

		if (TypeUtil.isArray(type)) {
			this.type.set(type);
			this.list.clear();
			Type componentType = TypeUtil.getComponentType(type);

			for (int i = 0; i < Array.getLength(instance); i++) {
				ConfigElement element = ConfigElement.create(this.config, componentType);
				element.saveToConfig(componentType, Array.get(instance, i));
				this.list.add(element);
			}
		} else if (TypeUtil.isCollection(type)) {
			this.type.set(type);
			this.list.clear();
			Type elementType = TypeUtil.getElementType(type);

			for (Object value : (Collection<?>) instance) {
				ConfigElement element = ConfigElement.create(this.config, elementType);
				element.saveToConfig(elementType, value);
				this.list.add(element);
			}
		} else {
			throw new IllegalArgumentException();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	Object loadFromConfig(Type type, @Nullable Object instance) {
		Objects.requireNonNull(type);
		if (!ConfigUtil.isList(type) || this.type.existsAndNotEqual(type)) {
			return instance;
		}

		if (TypeUtil.isArray(type)) {
			Type componentType = TypeUtil.getComponentType(type);
			if (!ConfigUtil.isConfigTypeEqual(componentType, TypeUtil.getComponentOrElementType(this.type.getOrDefault()))) {
				return instance;
			}

			Object array = Array.newInstance(TypeUtil.getRawType(componentType), this.list.size());
			for (int i = 0; i < this.list.size(); i++) {
				Array.set(array, i, this.list.get(i).loadFromConfig(componentType, TypeUtil.newInstance(componentType)));
			}

			return array;
		} else if (TypeUtil.isCollection(type)) {
			Type elementType = TypeUtil.getElementType(type);
			if (!ConfigUtil.isConfigTypeEqual(elementType, TypeUtil.getComponentOrElementType(this.type.getOrDefault()))) {
				return instance;
			}

			Collection<Object> collection = (Collection<Object>) TypeUtil.newInstance(type, instance);
			for (ConfigElement value : this.list) {
				collection.add(value.loadFromConfig(elementType, TypeUtil.newInstance(elementType)));
			}

			return collection;
		} else {
			throw new IllegalArgumentException();
		}
	}

}
