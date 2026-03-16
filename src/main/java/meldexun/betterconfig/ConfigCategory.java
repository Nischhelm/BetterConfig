package meldexun.betterconfig;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

import meldexun.betterconfig.api.BetterConfig;
import net.minecraftforge.common.config.Config;

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
	static final int CATEGORY_COMMENT_LENGTH = 106;
	static final String CATEGORY_COMMENT_BORDER = StringUtils.repeat('#', CATEGORY_COMMENT_LENGTH);
	static final String CATEGORY_COMMENT_SEPARATOR = '#' + StringUtils.repeat('-', CATEGORY_COMMENT_LENGTH - 2) + '#';
	final Map<String, ConfigCategory> subcategories = new LinkedHashMap<>();
	final Map<String, ConfigElement> elements = new LinkedHashMap<>();

	void clear() {
		this.elements.clear();
		this.subcategories.clear();
	}

	static class Entry {
		private final String name;
		private final ConfigElement configElement;
		private final Type type;
		@Nullable
		private final ConfigElementMetadata metadata;
		private final Object instance;

		private Entry(String name, ConfigElement configElement, @Nullable Object owner, Field field) {
			this.name = name;
			this.configElement = configElement;
			this.type = field.getGenericType();
			this.metadata = ConfigElementMetadata.fromField(owner, field);
			try {
				this.instance = field.get(owner);
			} catch (ReflectiveOperationException e) {
				throw new UnsupportedOperationException(e);
			}
		}

		private Entry(String name, ConfigElement configElement, Type type, Object instance) {
			this.name = name;
			this.configElement = configElement;
			this.type = type;
			this.metadata = null; // TODO compute metadata for map values?
			this.instance = instance;
		}

		String name() {
			return this.name;
		}

		ConfigElement configElement() {
			return this.configElement;
		}

		Type type() {
			return this.type;
		}

		@Nullable
		ConfigElementMetadata metadata() {
			return this.metadata;
		}

		@Nullable
		Object instance() {
			return this.instance;
		}
	}

	List<Entry> elements(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) {
		List<Entry> list = new ArrayList<>();

		if (TypeUtil.isMap(type)) {
			Type keyType = TypeUtil.getKeyType(type);
			if (!TypeAdapters.hasAdapter(keyType)) {
				throw new IllegalArgumentException();
			}
			TypeAdapter<Object> keyAdapter = TypeAdapters.get(keyType);
			Type valueType = TypeUtil.getValueType(type);

			if (ConfigUtil.isCategory(valueType)) {
				this.subcategories.forEach((name, subcategory) -> {
					list.add(new Entry(name, subcategory, valueType, ((Map<?, ?>) instance).get(keyAdapter.deserialize(name))));
				});
			} else {
				this.elements.forEach((name, element) -> {
					if (element.isConfigTypeEqual(valueType)) {
						list.add(new Entry(name, element, valueType, ((Map<?, ?>) instance).get(keyAdapter.deserialize(name))));
					}
				});
			}
		} else {
			Map<String, Field> fields = Arrays.stream(ConfigUtil.getConfigFields(type, instance == null)).collect(Collectors.toMap(field -> getName(settings, type, field), Function.identity()));
			Stream.concat(this.subcategories.entrySet().stream(), this.elements.entrySet().stream())
					.map(e -> new Entry(e.getKey(), e.getValue(), instance, fields.get(e.getKey())))
					.forEach(list::add);
			list.sort(OrderUtil.buildComparator(settings.elementOrder(), type, Entry::name, Entry::type, e -> e.metadata() != null ? e.metadata().order() : 0));
		}

		return list;
	}

	@Override
	String toString(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) {
		if (TypeUtil.isMap(type)) {
			if (ConfigUtil.isCategory(TypeUtil.getValueType(type))) {
				return "";
//				String s = this.subcategories.entrySet().stream()
//						.map(e -> e.getKey() + "=" + e.getValue().toString(settings, type, metadata, instance)) //TODO: prob needs to unwrap metadata
//						.collect(Collectors.joining(", ", "{", "}"));
//				return s;
				//{meldexun.betterconfig.example.TestData@72a90036={ name: Test, testLong: 1, testDouble: 0.0, testEnum: NORTH, testArray: [a, b, c], testList: [a, b, c], testMap: {a=a, b=b, c=c} }, meldexun.betterconfig.example.TestData@703e8fe8={ name: Test, testLong: 1, testDouble: 0.0, testEnum: NORTH, testArray: [a, b, c], testList: [a, b, c], testMap: {a=a, b=b, c=c} }}
//				return this.subcategories.toString();
			} else {
				return this.elements.entrySet().stream()
						.map(e -> e.getKey() + "=" + e.getValue().toString(settings, type, metadata, instance)) //TODO: prob needs to unwrap metadata
						.collect(Collectors.joining(", ", "{", "}"));
			}
		} else {
			//[{ name: Test, testLong: 1, testDouble: 0.0, testEnum: NORTH, testArray: [a, b, c], testList: [a, b, c], testMap: {a=a, b=b, c=c} }, { name: Test, testLong: 1, testDouble: 0.0, testEnum: NORTH, testArray: [a, b, c], testList: [a, b, c], testMap: {a=a, b=b, c=c} }]
			return Arrays.stream(ConfigUtil.getConfigFields(type, instance == null))
					.map(field -> {
						String name = getName(settings, type, field);
						ConfigElement configElement;
						if (ConfigUtil.isCategory(field.getGenericType())) {
							configElement = this.subcategories.get(name);
						} else {
							configElement = this.elements.get(name);
						}
						return configElement != null ? name + ":" + configElement : null;
					})
					.filter(Objects::nonNull)
					.collect(Collectors.joining(", ", "{", "}"));
		}
	}

	@Override
	boolean isConfigTypeEqual(Type type) {
		return ConfigUtil.isCategory(type);
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
				element = new ConfigValue();
			} else if ((matcher = reader.readMatching(LIST)) != null) {
				name = ObjectUtils.defaultIfNull(matcher.group(2), matcher.group(3));
				element = new ConfigList();
			} else if ((matcher = reader.readMatching(CATEGORY)) != null) {
				name = ObjectUtils.defaultIfNull(matcher.group(1), matcher.group(2));
				element = new ConfigCategory();
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
	void write(ConfigWriter writer, BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) throws IOException {
		writer.writeLine('{');
		writer.incrementIndentation();
		writer.write(this.elements(settings, type, metadata, instance), (writer1, entry) -> {
			if (writeEntry(writer1, settings, entry.name(), entry.configElement(), entry.type(), entry.metadata(), entry.instance(), !TypeUtil.isMap(type))) {
				writer1.newLine();
				return true;
			}
			return false;
		});
		writer.decrementIndentation();
		writer.write('}');
	}

	static boolean writeEntry(ConfigWriter writer, BetterConfig settings, String name, ConfigElement element, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance, boolean writeComment) throws IOException {
		// write comment
		if (writeComment) {
			if (metadata != null) {
				if (metadata.optional() && element.isDefault(settings, type, metadata, instance)) {
					return false;
				}

				if (element instanceof ConfigCategory) {
					if (metadata.hasComment()) {
						if (settings.bigCategoryComments()) {
							writer.writeLine(CATEGORY_COMMENT_BORDER);
							writer.writeCommentLine(name);
							writer.writeLine(CATEGORY_COMMENT_SEPARATOR);
						}

						for (String commentLine : metadata.comment().split("\r?\n")) {
							writer.writeCommentLine(commentLine);
						}

						if (settings.bigCategoryComments()) {
							writer.writeLine(CATEGORY_COMMENT_BORDER);
							writer.newLine();
						}
					}
				} else {
					if (metadata.hasComment()) {
						for (String commentLine : metadata.comment().split("\r?\n")) {
							writer.writeCommentLine(commentLine);
						}
					}

					boolean writeRange = settings.addRangesToComments() && (metadata.hasLongRange() || metadata.hasDoubleRange());
					boolean writeDefault = settings.addDefaultsToComments() && metadata.hasDefaultValue();
					if (writeRange || writeDefault) {
						writer.startComment();
						if (writeRange) {
							writer.write("Min: ");
							writer.write(metadata.hasLongRange() ? Long.toString(metadata.minLong()) : Double.toString(metadata.minDouble()));
							writer.write(" Max: ");
							writer.write(metadata.hasLongRange() ? Long.toString(metadata.maxLong()) : Double.toString(metadata.maxDouble()));
						}
						if (writeDefault) {
							if (writeRange) {
								writer.write(' ');
							}
							writer.write("Default: ");
							writer.write(TypeUtil.toString(type, metadata.defaultValue()));
						}
						writer.newLine();
					}
				}
			} else {
				writer.writeCommentLine("~DEPRECATED~");
			}
		}

		// write type and name
		if (element instanceof ConfigValue) {
			writeType(writer, type);
			writer.write(':');
			writeName(writer, name);
			writer.write('=');
		} else if (element instanceof ConfigList) {
			writeType(writer, type);
			writer.write(':');
			writeName(writer, name);
			writer.write(' ');
		} else if (element instanceof ConfigCategory) {
			writeName(writer, name);
			writer.write(' ');
		} else {
			throw new IllegalArgumentException();
		}

		// write value
		element.write(writer, settings, type, metadata, instance);

		return true;
	}

	static void writeType(ConfigWriter writer, Type type) throws IOException {
		if (TypeUtil.isArrayOrCollection(type)) {
			type = TypeUtil.getComponentOrElementType(type);
			while (TypeUtil.isArrayOrCollection(type)) {
				writer.write('L');
				type = TypeUtil.getComponentOrElementType(type);
			}
		}
		if (TypeAdapters.hasAdapter(type)) {
			if (TypeUtils.isAssignable(type, boolean.class)) {
				writer.write('B');
			} else if (TypeUtils.isAssignable(type, byte.class) || TypeUtils.isAssignable(type, short.class) || TypeUtils.isAssignable(type, int.class) || TypeUtils.isAssignable(type, long.class) || TypeUtils.isAssignable(type, char.class)) {
				writer.write('I');
			} else if (TypeUtils.isAssignable(type, float.class) || TypeUtils.isAssignable(type, double.class)) {
				writer.write('D');
			} else {
				writer.write('S');
			}
		} else {
			writer.write('C');
		}
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
	void saveToConfig(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) {
		Objects.requireNonNull(type);
		if (!ConfigUtil.isCategory(type)) {
			throw new IllegalArgumentException();
		}

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
				ConfigElement element = ConfigElement.create(valueType);
				if (element instanceof ConfigCategory) {
					this.subcategories.put(name, (ConfigCategory) element);
				} else {
					this.elements.put(name, element);
				}
				element.saveToConfig(settings, valueType, null, v); // TODO compute metadata for map values?
			});
		} else {
			for (Field field : ConfigUtil.getConfigFields(type, instance == null)) {
				String name = getName(settings, type, field);
				ConfigElement element;
				if (ConfigUtil.isCategory(field.getGenericType())) {
					element = this.subcategories.computeIfAbsent(name, k -> new ConfigCategory());
				} else {
					element = this.elements.compute(name, (k, v) -> v != null && v.isConfigTypeEqual(field.getGenericType()) ? v : ConfigElement.create(field.getGenericType()));
				}
				try {
					element.saveToConfig(settings, field.getGenericType(), ConfigElementMetadata.fromField(instance, field), field.get(instance));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new UnsupportedOperationException(e);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	Object loadFromConfig(BetterConfig settings, Type type, @Nullable ConfigElementMetadata metadata, @Nullable Object instance) {
		Objects.requireNonNull(type);
		if (!ConfigUtil.isCategory(type)) {
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
					map.put(keyAdapter.deserialize(name), subcategory.loadFromConfig(settings, valueType, null, TypeUtil.newInstance(valueType))); // TODO compute metadata for map values?
				});
			} else {
				this.elements.forEach((name, element) -> {
					if (element.isConfigTypeEqual(valueType)) {
						map.put(keyAdapter.deserialize(name), element.loadFromConfig(settings, valueType, null, TypeUtil.newInstance(valueType))); // TODO compute metadata for map values?
					}
				});
			}

			return map;
		} else {
			for (Field field : ConfigUtil.getConfigFields(type, instance == null)) {
				String name = getName(settings, type, field);
				ConfigElement element = (ConfigUtil.isCategory(field.getGenericType()) ? this.subcategories : this.elements).get(name);
				if (element != null && element.isConfigTypeEqual(field.getGenericType())) {
					try {
						Object value = element.loadFromConfig(settings, field.getGenericType(), ConfigElementMetadata.fromField(instance, field), field.get(instance));
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

	private static String getName(BetterConfig settings, Type type, Field field) {
		String name = AnnotationUtil.map(field, Config.Name.class, Config.Name::value, field.getName());
		if (ConfigUtil.isNonMapCategory(field.getGenericType()) && settings.lowerCaseCategories()) {
			name = name.toLowerCase();
		}
		return name;
	}

}
