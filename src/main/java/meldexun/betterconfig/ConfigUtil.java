package meldexun.betterconfig;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraftforge.common.config.Config;

public class ConfigUtil {

	private static final Map<Type, Field[]> STATIC_FIELDS = new HashMap<>();
	private static final Map<Type, Field[]> NON_STATIC_FIELDS = new HashMap<>();

	public static boolean isValue(Type type) {
		return TypeAdapters.hasAdapter(type);
	}

	public static boolean isList(Type type) {
		return TypeUtil.isArrayOrCollection(type);
	}

	public static boolean isCategory(Type type) {
		return !isValue(type) && !isList(type);
	}

	public static boolean isNonMapCategory(Type type) {
		return !isValue(type) && !isList(type) && !TypeUtil.isMap(type);
	}

	public static boolean isConfigTypeEqual(Type type1, Type type2) {
		if (isValue(type1)) {
			return isValue(type2);
		}
		if (isValue(type2)) {
			return false;
		}
		if (isList(type1)) {
			return isList(type2);
		}
		if (isList(type2)) {
			return false;
		}
		return true;
	}

	public static Field[] getConfigFields(Type type, boolean staticFields) {
		Map<Type, Field[]> map = staticFields ? STATIC_FIELDS : NON_STATIC_FIELDS;
		Field[] fields = map.get(type);
		if (fields == null) {
			Class<?> rawType = TypeUtil.getRawType(type);
			Stream<Field> stream = Arrays.stream(rawType.getDeclaredFields())
					.filter(f -> Modifier.isPublic(f.getModifiers()))
					.filter(f -> Modifier.isStatic(f.getModifiers()) == staticFields)
					.filter(f -> !f.isAnnotationPresent(Config.Ignore.class));
			Class<?> rawSuperType = rawType.getSuperclass();
			if (rawSuperType != null && !rawSuperType.equals(Object.class)) {
				stream = Stream.concat(Arrays.stream(getConfigFields(rawSuperType, staticFields)), stream);
			}
			map.put(type, fields = stream.toArray(Field[]::new));
		}
		return fields;
	}

}
