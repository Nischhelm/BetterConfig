package meldexun.betterconfig;

import java.io.BufferedWriter;
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

import com.google.common.base.Splitter;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

import meldexun.betterconfig.api.Order;
import net.minecraftforge.common.config.Config;

import static net.minecraftforge.common.config.Configuration.COMMENT_SEPARATOR;

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
	static final Comparator<Map.Entry<String, ? extends ConfigElement>> CATEGORY_ORDER = Comparator.comparing(Map.Entry::getValue, Comparator.comparingInt(ConfigElement::order));
	static final Comparator<Map.Entry<String, ? extends ConfigElement>> ELEMENT_ORDER = CATEGORY_ORDER.thenComparing(Map.Entry::getKey);
	final Map<String, ConfigCategory> subcategories = new LinkedHashMap<>();
	final Map<String, ConfigElement> elements = new LinkedHashMap<>();

	ConfigCategory(DefaultSupplier<Type> type) {
		super(type);
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
	void write(BufferedWriter writer, int indent) throws IOException {
		writer.write('{');
		writer.newLine();
		boolean isFirst = true;
		for (Map.Entry<String, ConfigElement> entry : this.elementsSorted()) {
			writeEntry(writer, indent + 1, entry.getKey(), entry.getValue(), isFirst);
			writer.newLine();
			isFirst = false;
		}
		for (Map.Entry<String, ConfigCategory> entry : this.subcategoriesSorted()) {
			writeEntry(writer, indent + 1, entry.getKey(), entry.getValue(), isFirst);
			writer.newLine();
			isFirst = false;
		}
		ConfigurationLoader.indent(writer, indent);
		writer.write('}');
	}

	static void writeEntry(BufferedWriter writer, int indent, String name, ConfigElement element, boolean isFirst) throws IOException {
		writeComment(writer, indent, name, element.comment, element instanceof ConfigCategory, isFirst);

		ConfigurationLoader.indent(writer, indent);
		if (element instanceof ConfigValue) {
			writer.write(serializeType((ConfigValue) element));
			writer.write(':');
			writeName(writer, indent, name);
			writer.write('=');
		} else if (element instanceof ConfigList) {
			writer.write(serializeType((ConfigList) element));
			writer.write(':');
			writeName(writer, indent, name);
			writer.write(' ');
		} else if (element instanceof ConfigCategory) {
			writeName(writer, indent, name);
			writer.write(' ');
		} else {
			throw new IllegalArgumentException();
		}
		element.write(writer, indent);
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

	static void writeName(BufferedWriter writer, int indent, String name) throws IOException {
		if (NO_QUOTING_NEEDED.matcher(name).matches()) {
			writer.write(name);
		} else {
			writer.write('"');
			writer.write(name);
			writer.write('"');
		}
	}

	enum EnumCommentLayout {
		MINIMAL, //treats categories and entries the same, will only print the actual comment, not the name of the category with lots of ###### and #-------#
		FORGE, //exactly on pair with how forge does it, including unclean behavior
		FORGE_FIXED, //close to forge with two small fixes for categories (no newline if first element, no newline to bottom/category entry, undecided about that one so added a boolean for it)
		FORGE_FIXED_ALWAYS_NAMES, //same as FORGE_FIXED with additionally always printing ####\n categoryname\n #### even if the category doesnt have comments. cleaner, but also prints #### general #### for the outermost category
		FORGE_FIXED_NO_NAMES //same as FORGE_FIXED but never printing category names as that is kinda not needed anymore (categories are not all lowercase anymore). close to MINIMAL, except that categories get extra #-----# lines above and below
	}
	static EnumCommentLayout layout = EnumCommentLayout.FORGE_FIXED_NO_NAMES;
	static boolean keepNewLineBelowCategoryComment = true; //TODO: im undecided about this fix vs forge, unused in MINIMAL

	static final Splitter splitter = Splitter.onPattern("\r?\n");

	static void writeComment(BufferedWriter writer, int indent, String name, String[] comments, boolean isCategory, boolean isFirstElement) throws IOException {
		boolean noComments = comments == null || comments.length == 0;

		switch (layout) {
			case MINIMAL: {
				if (noComments) return;
				if (!isFirstElement) writer.newLine(); //listed elements are separated by newline, first one isnt separated to the { in line before

				writeComments(writer, indent, comments);

				return;
			}
			case FORGE: {
				if(noComments) return;

				if (isCategory) {
					writer.newLine(); //unclean and not in parity to non-category entries, should only do it if !isFirstElement

					ConfigurationLoader.indent(writer, indent);
					writer.write(COMMENT_SEPARATOR);
					writer.newLine();

					ConfigurationLoader.indent(writer, indent);
					writer.write("# " + name);
					writer.newLine();

					ConfigurationLoader.indent(writer, indent);
					writer.write("#--------------------------------------------------------------------------------------------------------#");
					writer.newLine();

					writeComments(writer, indent, comments);

					ConfigurationLoader.indent(writer, indent);
					writer.write(COMMENT_SEPARATOR);
					writer.newLine();
					writer.newLine(); //unclean, separation of comment to config entry = keepNewLineBelowCategoryComment
				} else {
					if (!isFirstElement)
						writer.newLine(); //listed elements are separated by newline, first one isnt separated to the { of line before

					writeComments(writer, indent, comments);
				}
				return;
			}
			case FORGE_FIXED: {
				if (noComments) return;

				if (isCategory) {
					if(!isFirstElement) writer.newLine(); //listed elements are separated by newline, first one isnt separated to the { in line before

					ConfigurationLoader.indent(writer, indent);
					writer.write(COMMENT_SEPARATOR);
					writer.newLine();

					ConfigurationLoader.indent(writer, indent);
					writer.write("# " + name);
					writer.newLine();

					ConfigurationLoader.indent(writer, indent);
					writer.write("#--------------------------------------------------------------------------------------------------------#");
					writer.newLine();

					writeComments(writer, indent, comments);

					ConfigurationLoader.indent(writer, indent);
					writer.write(COMMENT_SEPARATOR);
					writer.newLine();
					if(keepNewLineBelowCategoryComment) writer.newLine();
				} else {
					if (!isFirstElement) writer.newLine(); //listed elements are separated by newline, first one isnt separated to the { of line before

					writeComments(writer, indent, comments);
				}
				return;
			}

			case FORGE_FIXED_ALWAYS_NAMES: {
				if(isCategory){
					if(!isFirstElement) writer.newLine(); //listed elements are separated by newline, first one isnt separated to the { in line before

					ConfigurationLoader.indent(writer, indent);
					writer.write(COMMENT_SEPARATOR);
					writer.newLine();

					ConfigurationLoader.indent(writer, indent);
					writer.write("# " + name);
					writer.newLine();

					if(noComments) {
						ConfigurationLoader.indent(writer, indent);
						writer.write(COMMENT_SEPARATOR);
						writer.newLine();
						if(keepNewLineBelowCategoryComment) writer.newLine();
					}
				}

				if (noComments) return;

				if (isCategory) {
					ConfigurationLoader.indent(writer, indent);
					writer.write("#--------------------------------------------------------------------------------------------------------#");
					writer.newLine();

					writeComments(writer, indent, comments);

					ConfigurationLoader.indent(writer, indent);
					writer.write(COMMENT_SEPARATOR);
					writer.newLine();
					if(keepNewLineBelowCategoryComment) writer.newLine();
				} else {
					if (!isFirstElement) writer.newLine(); //listed elements are separated by newline, first one isnt separated to the { of line before

					writeComments(writer, indent, comments);
				}
				return;
			}

			case FORGE_FIXED_NO_NAMES: {
				if (noComments) return;

				if(!isFirstElement) writer.newLine(); //listed elements are separated by newline, first one isnt separated to the { of line before

				if (isCategory) {
					ConfigurationLoader.indent(writer, indent);
					writer.write(COMMENT_SEPARATOR);
					writer.newLine();
					if(keepNewLineBelowCategoryComment) writer.newLine();
				}

				writeComments(writer, indent, comments);

				if(isCategory) {
					ConfigurationLoader.indent(writer, indent);
					writer.write(COMMENT_SEPARATOR);
					writer.newLine();
				}

				return;
			}
		}
	}

	static void writeComments(BufferedWriter writer, int indent, String[] comments) throws IOException {
		for (String comment : comments) {
			for (String line : splitter.split(comment)) {
				ConfigurationLoader.indent(writer, indent);
				writer.write("# " + line);
				writer.newLine();
				//TODO: range, defaultvalue etc maybe? forge Configuration does that, @Config doesnt
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
				ConfigElement element = ConfigElement.create(valueType);
				if (element instanceof ConfigCategory) {
					this.subcategories.put(name, (ConfigCategory) element);
				} else {
					this.elements.put(name, element);
				}
				element.saveToConfig(valueType, v);
			});
		} else {
			for (Field field : ConfigUtil.getConfigFields(type, instance == null)) {
				String name = field.isAnnotationPresent(Config.Name.class) ? field.getAnnotation(Config.Name.class).value() : field.getName();
				String[] comment = field.isAnnotationPresent(Config.Comment.class) ? field.getAnnotation(Config.Comment.class).value() : null;
				ConfigElement element;
				if (ConfigUtil.isCategory(field.getGenericType())) {
					element = this.subcategories.computeIfAbsent(name, k -> new ConfigCategory(DefaultSupplier.of(field.getGenericType())));
				} else {
					element = this.elements.compute(name, (k, v) -> v != null && v.isConfigTypeEqual(field.getGenericType()) ? v : ConfigElement.create(field.getGenericType()));
				}
				try {
					element.saveToConfig(field.getGenericType(), field.get(instance));
					element.setComment(comment);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new UnsupportedOperationException(e);
				}
				if (field.isAnnotationPresent(Order.class)) {
					element.order(field.getAnnotation(Order.class).value());
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
				String name = field.isAnnotationPresent(Config.Name.class) ? field.getAnnotation(Config.Name.class).value() : field.getName();
				ConfigElement element = (ConfigUtil.isCategory(field.getGenericType()) ? this.subcategories : this.elements).get(name);
				if (element != null && element.isConfigTypeEqual(field.getGenericType())) {
					try {
						field.set(instance, element.loadFromConfig(field.getGenericType(), field.get(instance)));
					} catch (IllegalArgumentException | IllegalAccessException e) {
						throw new UnsupportedOperationException(e);
					}
				}
			}

			return instance;
		}
	}

}
