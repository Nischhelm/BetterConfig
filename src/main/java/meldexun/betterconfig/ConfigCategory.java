package meldexun.betterconfig;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

import meldexun.betterconfig.api.BetterConfig;
import meldexun.betterconfig.api.BetterConfig.ConfigComparator;
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

	ConfigCategory(DefaultSupplier<Type> type) {
		super(type);
		if (!ConfigUtil.isCategory(this.type().getOrDefault())) {
			throw new IllegalArgumentException();
		}
	}

	@Override
	boolean isDefault() {
		return false; // TODO: need to find a good implementation for this
	}

	@Override
	void clear() {
		super.clear();
		this.elements.clear();
		this.subcategories.clear();
	}

	List<Map.Entry<String, ? extends ConfigElement>> elements(ConfigComparator... order) {
		List<Map.Entry<String, ? extends ConfigElement>> list = new ArrayList<>();
		list.addAll(this.subcategories.entrySet());
		list.addAll(this.elements.entrySet());
		if (ConfigUtil.isNonMapCategory(this.type().getOrDefault())) {
			list.sort(OrderUtil.buildComparator(order, this.type().getOrDefault(), Map.Entry::getKey, e -> e.getValue().type().getOrDefault(), e -> e.getValue().metadata() != null ? e.getValue().metadata().order() : 0));
		}
		return list;
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
				element = new ConfigValue(DefaultSupplier.fallback(parseValueType(matcher.group(1))));
			} else if ((matcher = reader.readMatching(LIST)) != null) {
				name = ObjectUtils.defaultIfNull(matcher.group(2), matcher.group(3));
				element = new ConfigList(DefaultSupplier.fallback(parseListType(matcher.group(1))));
			} else if ((matcher = reader.readMatching(CATEGORY)) != null) {
				name = ObjectUtils.defaultIfNull(matcher.group(1), matcher.group(2));
				element = new ConfigCategory(DefaultSupplier.fallback(Map.class));
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
	void write(ConfigWriter writer, BetterConfig settings) throws IOException {
		writer.writeLine('{');
		writer.incrementIndentation();
		writer.write(this.elements(settings.elementOrder()), (writer1, entry) -> {
			if (writeEntry(writer1, settings, entry.getKey(), entry.getValue(), TypeUtil.isMap(this.type().getOrDefault()) || ConfigUtil.isList(this.type().getOrDefault()))) {
				writer1.newLine();
				return true;
			}
			return false;
		});
		writer.decrementIndentation();
		writer.write('}');
	}

	static boolean writeEntry(ConfigWriter writer, BetterConfig settings, String name, ConfigElement element, boolean ownedByMapOrList) throws IOException {
		// write comment
		ConfigElementMetadata metadata = element.metadata();
		if (metadata != null) {
			if (!ownedByMapOrList && metadata.optional() && element.isDefault()) {
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
						writer.write(TypeUtil.toString(element.type().get(), metadata.defaultValue()));
					}
					writer.newLine();
				}
			}
		} else {
			writer.writeCommentLine("~DEPRECATED~");
		}

		// write type and name
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

		// write value
		element.write(writer, settings);

		return true;
	}

	static String serializeType(ConfigValue value) {
		return Character.toString(serializeType(value.type().getOrDefault()));
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
		Type type = TypeUtil.getComponentOrElementType(list.type().getOrDefault());
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
	void loadAnnotations(BetterConfig settings, Type type, ConfigElementMetadata metadata, @Nullable Object instance) {
		super.loadAnnotations(settings, type, metadata, instance);

		if (TypeUtil.isMap(type)) {
			Type valueType = TypeUtil.getValueType(type);

			if (ConfigUtil.isCategory(valueType)) {
				this.subcategories.forEach((name, subcategory) -> {
					subcategory.loadAnnotations(settings, valueType, metadata, TypeUtil.newInstance(valueType));
				});
			} else {
				this.elements.forEach((name, element) -> {
					if (element.isConfigTypeEqual(valueType)) {
						element.loadAnnotations(settings, valueType, metadata, TypeUtil.newInstance(valueType));
					}
				});
			}
		} else {
			for (Field field : ConfigUtil.getConfigFields(type, instance == null)) {
				String name = getName(settings, type, field);
				ConfigElement element;
				if (ConfigUtil.isCategory(field.getGenericType())) {
					element = this.subcategories.computeIfAbsent(name, k -> new ConfigCategory(DefaultSupplier.of(field.getGenericType())));
				} else {
					element = this.elements.compute(name, (k, v) -> {
						if (v == null || !v.isConfigTypeEqual(field.getGenericType())) {
							v = ConfigElement.create(field.getGenericType());
							try {
								v.saveToConfig(settings, field.getGenericType(), field.get(instance));
							} catch (IllegalArgumentException | IllegalAccessException e) {
								throw new UnsupportedOperationException(e);
							}
						}
						return v;
					});
				}
				try {
					element.loadAnnotations(settings, field.getGenericType(), ConfigElementMetadata.fromField(instance, field), field.get(instance));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new UnsupportedOperationException(e);
				}
			}

			if (settings.removeDeprecatedEntries()) {
				this.subcategories.values().removeIf(e -> e.metadata() == null);
				this.elements.values().removeIf(e -> e.metadata() == null);
			}
		}
	}

	@Override
	void saveToConfig(BetterConfig settings, Type type, @Nullable Object instance) {
		Objects.requireNonNull(type);
		if (!ConfigUtil.isCategory(type)) {
			throw new IllegalArgumentException();
		}

		this.type().set(type);
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
				element.saveToConfig(settings, valueType, v);
			});
		} else {
			for (Field field : ConfigUtil.getConfigFields(type, instance == null)) {
				String name = getName(settings, type, field);
				ConfigElement element;
				if (ConfigUtil.isCategory(field.getGenericType())) {
					element = this.subcategories.computeIfAbsent(name, k -> new ConfigCategory(DefaultSupplier.of(field.getGenericType())));
				} else {
					element = this.elements.compute(name, (k, v) -> v != null && v.isConfigTypeEqual(field.getGenericType()) ? v : ConfigElement.create(field.getGenericType()));
				}
				try {
					element.saveToConfig(settings, field.getGenericType(), field.get(instance));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new UnsupportedOperationException(e);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	Object loadFromConfig(BetterConfig settings, Type type, @Nullable Object instance) {
		Objects.requireNonNull(type);
		if (!ConfigUtil.isCategory(type) || this.type().existsAndNotEqual(type)) {
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
					map.put(keyAdapter.deserialize(name), subcategory.loadFromConfig(settings, valueType, TypeUtil.newInstance(valueType)));
				});
			} else {
				this.elements.forEach((name, element) -> {
					if (element.isConfigTypeEqual(valueType)) {
						map.put(keyAdapter.deserialize(name), element.loadFromConfig(settings, valueType, TypeUtil.newInstance(valueType)));
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
						Object value = element.loadFromConfig(settings, field.getGenericType(), field.get(instance));
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
