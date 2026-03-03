package meldexun.betterconfig;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

import meldexun.betterconfig.gui.EntryInfo;

class ConfigCategory extends ConfigElement {

	static final String UNQUOTED_NAME = "[\\w\\.-]*";
	static final String QUOTED_NAME = "[^\"]*";
	static final String NAME = String.format("(?:(%s)|\"(%s)\")", UNQUOTED_NAME, QUOTED_NAME);
	static final String VALUE_TYPE = "([BIDS])";
	static final String LIST_TYPE = "(L?[BIDSC])";
	static final Pattern NO_QUOTING_NEEDED = Pattern.compile(UNQUOTED_NAME);
	static final Pattern CATEGORY = Pattern.compile(String.format("%s\\s*(?=\\{)", NAME));
	static final Pattern LIST = Pattern.compile(String.format("%s:%s\\s*(?=<)", LIST_TYPE, NAME));
	static final Pattern VALUE = Pattern.compile(String.format("%s:%s=", VALUE_TYPE, NAME));
	static final Comparator<Map.Entry<String, ? extends ConfigElement>> CATEGORY_ORDER = Comparator.comparingInt(e -> e.getValue().info() != null ? e.getValue().info().order() : 0);
	static final Comparator<Map.Entry<String, ? extends ConfigElement>> ELEMENT_ORDER = CATEGORY_ORDER.thenComparing(Map.Entry::getKey);
	static final int CATEGORY_COMMENT_LENGTH = 106;
	final Map<String, ConfigCategory> subcategories = new LinkedHashMap<>();
	final Map<String, ConfigElement> elements = new LinkedHashMap<>();

	ConfigCategory(Config config, DefaultSupplier<Type> type) {
		super(config, type);
		if (!ConfigUtil.isCategory(this.type.getOrDefault())) {
			throw new IllegalArgumentException();
		}
	}

	@SuppressWarnings("unchecked")
	Map.Entry<String, ConfigCategory>[] subcategoriesSorted() {
		Map.Entry<String, ConfigCategory>[] array = this.subcategories.entrySet().toArray(new Map.Entry[this.subcategories.size()]);
		if (!TypeUtil.isMap(this.type.getOrDefault())) {
			Arrays.parallelSort(array, CATEGORY_ORDER);
		}
		return array;
	}

	@SuppressWarnings("unchecked")
	Map.Entry<String, ConfigElement>[] elementsSorted() {
		Map.Entry<String, ConfigElement>[] array = this.elements.entrySet().toArray(new Map.Entry[this.elements.size()]);
		if (!TypeUtil.isMap(this.type.getOrDefault())) {
			Arrays.parallelSort(array, ELEMENT_ORDER);
		}
		return array;
	}

	@Override
	void read(ConfigReader reader) throws IOException {
		if (!reader.readLine().equals("{")) {
			throw new IllegalArgumentException();
		}
		while (!reader.readLineIfEqual("}")) {
			String name;
			ConfigElement element;

			Matcher matcher;
			if ((matcher = reader.readMatching(VALUE)) != null) {
				name = ObjectUtils.defaultIfNull(matcher.group(2), matcher.group(3));
				element = new ConfigValue(this.config, DefaultSupplier.fallback(parseValueType(matcher.group(1))));
			} else if ((matcher = reader.readMatching(LIST)) != null) {
				name = ObjectUtils.defaultIfNull(matcher.group(2), matcher.group(3));
				element = new ConfigList(this.config, DefaultSupplier.fallback(parseListType(matcher.group(1))));
			} else if ((matcher = reader.readMatching(CATEGORY)) != null) {
				name = ObjectUtils.defaultIfNull(matcher.group(1), matcher.group(2));
				element = new ConfigCategory(this.config, DefaultSupplier.fallback(Map.class));
			} else {
				throw new IllegalArgumentException(reader.peekLine());
			}
			element.read(reader);

			if (element instanceof ConfigCategory) {
				this.subcategories.put(name, (ConfigCategory) element);
			} else {
				this.elements.put(name, element);
			}
		}
	}

	static Type parseValueType(String id) {
		if (id.length() != 1) {
			throw new IllegalArgumentException();
		}
		switch (id.charAt(0)) {
		case 'B':
			return Boolean.class;
		case 'I':
			return Long.class;
		case 'D':
			return Double.class;
		case 'S':
			return String.class;
		default:
			throw new IllegalArgumentException();
		}
	}

	static Type parseListType(String s) {
		if (s.isEmpty()) {
			throw new IllegalArgumentException();
		}
		Type type = TypeUtils.parameterize(Collection.class, parseElementType(s.charAt(s.length() - 1)));
		for (int i = s.length() - 2; i >= 0; i--) {
			if (s.charAt(i) != 'L') {
				throw new IllegalArgumentException();
			}
			type = TypeUtils.parameterize(Collection.class, type);
		}
		return type;
	}

	static Type parseElementType(char c) {
		switch (c) {
		case 'B':
			return Boolean.class;
		case 'I':
			return Long.class;
		case 'D':
			return Double.class;
		case 'S':
			return String.class;
		case 'C':
			return Map.class;
		default:
			throw new IllegalArgumentException();
		}
	}

	@Override
	void write(ConfigWriter writer) throws IOException {
		writer.writeLine('{');
		writer.incrementIndentation();
		for (Map.Entry<String, ConfigElement> entry : this.elementsSorted()) {
			writeEntry(writer, entry.getKey(), entry.getValue());
			writer.newLine();
		}
		for (Map.Entry<String, ConfigCategory> entry : this.subcategoriesSorted()) {
			writeEntry(writer, entry.getKey(), entry.getValue());
			writer.newLine();
		}
		writer.decrementIndentation();
		writer.write('}');
	}

	static void writeEntry(ConfigWriter writer, String name, ConfigElement element) throws IOException {
		EntryInfo info = element.info();
		if (info != null) {
			if (element instanceof ConfigCategory) {
				if (info.hasComment()) {
					if (element.config.settings.bigCategoryComments()) {
						writer.writeLine('#', CATEGORY_COMMENT_LENGTH);
						writer.writeCommentLine(name);
						writer.write('#').write('-', CATEGORY_COMMENT_LENGTH - 2).write('#').newLine();
					}

					for (String commentLine : info.comment().split("\r?\n")) {
						writer.writeCommentLine(commentLine);
					}

					if (element.config.settings.bigCategoryComments()) {
						writer.writeLine('#', CATEGORY_COMMENT_LENGTH);
					}
				}
			} else {
				if (info.hasComment()) {
					for (String commentLine : info.comment().split("\r?\n")) {
						writer.writeCommentLine(commentLine);
					}
				}
			}
		}

		if (element instanceof ConfigValue) {
			writer.write(serializeType((ConfigValue) element));
			writer.write(':');
			writeName(writer, name);
			writer.write('=');
		} else if (element instanceof ConfigList) {
			writer.write(serializeType((ConfigList) element));
			writer.write(':');
			writeName(writer, name);
			writer.write(' ');
		} else if (element instanceof ConfigCategory) {
			writeName(writer, name);
			writer.write(' ');
		} else {
			throw new IllegalArgumentException();
		}
		element.write(writer);
	}

	static String serializeType(ConfigValue value) {
		return Character.toString(serializeType(value.type.getOrDefault()));
	}

	static char serializeType(Type type) {
		if (TypeUtils.isAssignable(type, boolean.class)) {
			return 'B';
		}
		if (TypeUtils.isAssignable(type, byte.class) || TypeUtils.isAssignable(type, short.class) || TypeUtils.isAssignable(type, int.class) || TypeUtils.isAssignable(type, long.class) || TypeUtils.isAssignable(type, char.class)) {
			return 'I';
		}
		if (TypeUtils.isAssignable(type, float.class) || TypeUtils.isAssignable(type, double.class)) {
			return 'D';
		}
		return 'S';
	}

	static String serializeType(ConfigList list) {
		StringBuilder sb = new StringBuilder();
		Type type = TypeUtil.getComponentOrElementType(list.type.getOrDefault());
		while (TypeUtil.isArrayOrCollection(type)) {
			sb.append('L');
			type = TypeUtil.getComponentOrElementType(type);
		}
		sb.append(serializeElementType(type));
		return sb.toString();
	}

	static char serializeElementType(Type elementType) {
		if (TypeAdapters.hasAdapter(elementType)) {
			return serializeType(elementType);
		}
		return 'C';
	}

	static void writeName(ConfigWriter writer, String name) throws IOException {
		if (NO_QUOTING_NEEDED.matcher(name).matches()) {
			writer.write(name);
		} else {
			writer.write('"');
			writer.write(name);
			writer.write('"');
		}
	}

	@Override
	void loadInfo(Type type, EntryInfo info, Object instance) {
		super.loadInfo(type, info, instance);

		if (!TypeUtil.isMap(type)) {
			for (Field field : ConfigUtil.getConfigFields(type, instance == null)) {
				String name = field.isAnnotationPresent(net.minecraftforge.common.config.Config.Name.class) ? field.getAnnotation(net.minecraftforge.common.config.Config.Name.class).value() : field.getName();
				if (this.config.settings.lowerCaseCategories()) {
					name = name.toLowerCase();
				}
				ConfigElement element;
				if (ConfigUtil.isCategory(field.getGenericType())) {
					element = this.subcategories.computeIfAbsent(name, k -> new ConfigCategory(this.config, DefaultSupplier.of(field.getGenericType())));
				} else {
					element = this.elements.compute(name, (k, v) -> v != null && v.isConfigTypeEqual(field.getGenericType()) ? v : ConfigElement.create(this.config, field.getGenericType()));
				}
				try {
					element.loadInfo(field.getGenericType(), EntryInfo.fromField(instance, field), field.get(instance));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new UnsupportedOperationException(e);
				}
			}
		}
	}

	@Override
	void saveToConfig(Type type, @Nullable Object instance) {
		Objects.requireNonNull(type);
		if (!ConfigUtil.isCategory(type)) {
			throw new IllegalArgumentException();
		}

		this.type.set(type);
		if (TypeUtil.isMap(type)) {
			Objects.requireNonNull(instance);

			this.subcategories.clear();
			this.elements.clear();

			Type keyType = TypeUtil.getKeyType(type);
			if (!TypeAdapters.hasAdapter(keyType)) {
				throw new IllegalArgumentException();
			}
			TypeAdapter<Object> keyAdapter = TypeAdapters.get(keyType);
			Type valueType = TypeUtil.getValueType(type);

			((Map<?, ?>) instance).forEach((k, v) -> {
				String name = keyAdapter.serialize(k);
				ConfigElement element = ConfigElement.create(this.config, valueType);
				if (element instanceof ConfigCategory) {
					this.subcategories.put(name, (ConfigCategory) element);
				} else {
					this.elements.put(name, element);
				}
				element.saveToConfig(valueType, v);
			});
		} else {
			for (Field field : ConfigUtil.getConfigFields(type, instance == null)) {
				String name = field.isAnnotationPresent(net.minecraftforge.common.config.Config.Name.class) ? field.getAnnotation(net.minecraftforge.common.config.Config.Name.class).value() : field.getName();
				if (this.config.settings.lowerCaseCategories()) {
					name = name.toLowerCase();
				}
				ConfigElement element;
				if (ConfigUtil.isCategory(field.getGenericType())) {
					element = this.subcategories.computeIfAbsent(name, k -> new ConfigCategory(this.config, DefaultSupplier.of(field.getGenericType())));
				} else {
					element = this.elements.compute(name, (k, v) -> v != null && v.isConfigTypeEqual(field.getGenericType()) ? v : ConfigElement.create(this.config, field.getGenericType()));
				}
				try {
					element.saveToConfig(field.getGenericType(), field.get(instance));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new UnsupportedOperationException(e);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	Object loadFromConfig(Type type, @Nullable Object instance) {
		Objects.requireNonNull(type);
		if (!ConfigUtil.isCategory(type) || this.type.existsAndNotEqual(type)) {
			return instance;
		}

		if (TypeUtil.isMap(type)) {
			Type keyType = TypeUtil.getKeyType(type);
			if (!TypeAdapters.hasAdapter(keyType)) {
				throw new IllegalArgumentException();
			}
			TypeAdapter<Object> keyAdapter = TypeAdapters.get(keyType);
			Type valueType = TypeUtil.getValueType(type);

			Map<Object, Object> map = (Map<Object, Object>) TypeUtil.newInstance(type, instance);
			if (ConfigUtil.isCategory(valueType)) {
				this.subcategories.forEach((name, subcategory) -> {
					map.put(keyAdapter.deserialize(name), subcategory.loadFromConfig(valueType, TypeUtil.newInstance(valueType)));
				});
			} else {
				this.elements.forEach((name, element) -> {
					if (element.isConfigTypeEqual(valueType)) {
						map.put(keyAdapter.deserialize(name), element.loadFromConfig(valueType, TypeUtil.newInstance(valueType)));
					}
				});
			}

			return map;
		} else {
			for (Field field : ConfigUtil.getConfigFields(type, instance == null)) {
				String name = field.isAnnotationPresent(net.minecraftforge.common.config.Config.Name.class) ? field.getAnnotation(net.minecraftforge.common.config.Config.Name.class).value() : field.getName();
				if (this.config.settings.lowerCaseCategories()) {
					name = name.toLowerCase();
				}
				ConfigElement element = (ConfigUtil.isCategory(field.getGenericType()) ? this.subcategories : this.elements).get(name);
				if (element != null && element.isConfigTypeEqual(field.getGenericType())) {
					try {
						Object value = element.loadFromConfig(field.getGenericType(), field.get(instance));
						if (!ConfigUtil.isNonMapCategory(field.getGenericType())) {
							field.set(instance, value);
						}
					} catch (IllegalArgumentException | IllegalAccessException e) {
						throw new UnsupportedOperationException(e);
					}
				}
			}

			return instance;
		}
	}

}
