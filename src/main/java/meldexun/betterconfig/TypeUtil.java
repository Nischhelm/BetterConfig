package meldexun.betterconfig;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import org.apache.commons.lang3.reflect.TypeUtils;

public class TypeUtil {

	/**
	 * @see TypeUtils#getRawType(Type, Type)
	 */
	public static Class<?> getRawType(Type type) {
		return TypeUtils.getRawType(type, null);
	}

	/**
	 * @see TypeUtil#newInstance(Type, Object)
	 */
	public static Object newInstance(Type type) {
		return newInstance(type, null);
	}

	/**
	 * @see Class#newInstance()
	 */
	public static Object newInstance(Type type, @Nullable Object instance) {
		if (isArray(type)) {
			return Array.newInstance(getRawType(getComponentType(type)), 0);
		}
		if (TypeAdapters.hasAdapter(type)) {
			return TypeAdapters.get(type).defaultValue();
		}
		if (instance != null) {
			try {
				return instance.getClass().newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				// ignore
			}
		}
		try {
			return getRawType(type).newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new UnsupportedOperationException(e);
		}
	}

	/**
	 * @see Class#isEnum()
	 */
	public static boolean isEnum(Type type) {
		return type instanceof Class && ((Class<?>) type).isEnum();
	}

	/**
	 * @see Enum#valueOf(Class, String)
	 */
	@SuppressWarnings("unchecked")
	public static <E extends Enum<E>> E valueOf(Type type, String name) {
		return Enum.valueOf((Class<E>) type, name);
	}

	/**
	 * @see Class#getEnumConstants()
	 */
	@SuppressWarnings("unchecked")
	public static <E extends Enum<E>> E[] getEnumConstants(Type type) {
		if (type instanceof Class && ((Class<?>) type).isEnum()) {
			return ((Class<E>) type).getEnumConstants();
		}
		return null;
	}

	/**
	 * @see TypeUtils#isArrayType(Type)
	 */
	public static boolean isArray(Type type) {
		return TypeUtils.isArrayType(type);
	}

	/**
	 * @see TypeUtils#getArrayComponentType(Type)
	 */
	public static Type getComponentType(Type type) {
		return TypeUtils.getArrayComponentType(type);
	}

	/**
	 * @see TypeUtils#isAssignable(Type, Type)
	 */
	public static boolean isCollection(Type type) {
		return TypeUtils.isAssignable(type, Collection.class);
	}

	/**
	 * @see TypeUtils#getTypeArguments(Type, Class)
	 */
	public static Type getElementType(Type type) {
		return TypeUtils.getTypeArguments(type, Collection.class).get(Collection.class.getTypeParameters()[0]);
	}

	/**
	 * @see TypeUtils#isAssignable(Type, Type)
	 */
	public static boolean isMap(Type type) {
		return TypeUtils.isAssignable(type, Map.class);
	}

	/**
	 * @see TypeUtils#getTypeArguments(Type, Class)
	 */
	public static Type getKeyType(Type type) {
		return TypeUtils.getTypeArguments(type, Map.class).get(Map.class.getTypeParameters()[0]);
	}

	/**
	 * @see TypeUtils#getTypeArguments(Type, Class)
	 */
	public static Type getValueType(Type type) {
		return TypeUtils.getTypeArguments(type, Map.class).get(Map.class.getTypeParameters()[1]);
	}

	public static boolean isArrayOrCollection(Type type) {
		return isArray(type) || isCollection(type);
	}

	public static Type getComponentOrElementType(Type type) {
		if (isArray(type)) {
			return getComponentType(type);
		}
		if (isCollection(type)) {
			return getElementType(type);
		}
		throw new IllegalArgumentException();
	}

	public static boolean equals(Type type, @Nullable Object o1, @Nullable Object o2) {
		if ((o1 == null) != (o2 == null)) {
			return false;
		}
		if (TypeAdapters.hasAdapter(type)) {
			return Objects.equals(TypeAdapters.get(type).serialize(o1), TypeAdapters.get(type).serialize(o2));
		}
		if (TypeUtil.isArray(type)) {
			return arrayEquals(type, o1, o2);
		}
		if (TypeUtil.isCollection(type)) {
			return collectionEquals(type, o1, o2);
		}
		if (TypeUtil.isMap(type)) {
			return mapEquals(type, o1, o2);
		}
		return objectEquals(type, o1, o2);
	}

	private static boolean arrayEquals(Type type, Object o1, Object o2) {
		if (Array.getLength(o1) != Array.getLength(o2)) {
			return false;
		}
		Type componentType = TypeUtil.getComponentType(type);
		for (int i = 0; i < Array.getLength(o1); i++) {
			if (!equals(componentType, Array.get(o1, i), Array.get(o2, i))) {
				return false;
			}
		}
		return true;
	}

	private static boolean collectionEquals(Type type, Object o1, Object o2) {
		if (((Collection<?>) o1).size() != ((Collection<?>) o2).size()) {
			return false;
		}
		Type elementType = TypeUtil.getElementType(type);
		Iterator<?> i1 = ((Collection<?>) o1).iterator();
		Iterator<?> i2 = ((Collection<?>) o2).iterator();
		while (i1.hasNext()) {
			if (!equals(elementType, i1.next(), i2.next())) {
				return false;
			}
		}
		return true;
	}

	private static boolean mapEquals(Type type, Object o1, Object o2) {
		if (((Map<?, ?>) o1).size() != ((Map<?, ?>) o2).size()) {
			return false;
		}
		Type valueType = TypeUtil.getValueType(type);
		for (Map.Entry<?, ?> e : ((Map<?, ?>) o1).entrySet()) {
			if (!((Map<?, ?>) o2).containsKey(e.getKey())) {
				return false;
			}
			if (!equals(valueType, e.getValue(), ((Map<?, ?>) o2).get(e.getKey()))) {
				return false;
			}
		}
		return true;
	}

	private static boolean objectEquals(Type type, Object o1, Object o2) {
		for (Field f : ConfigUtil.getConfigFields(type, false)) {
			try {
				if (!equals(f.getGenericType(), f.get(o1), f.get(o2))) {
					return false;
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new UnsupportedOperationException(e);
			}
		}
		return true;
	}

	public static Object copy(Type type, @Nullable Object instance) {
		if (instance == null) {
			return null;
		}
		if (TypeAdapters.hasAdapter(type)) {
			return TypeAdapters.get(type).copy(instance);
		}
		if (TypeUtil.isArray(type)) {
			return copyArray(type, instance);
		}
		if (TypeUtil.isCollection(type)) {
			return copyCollection(type, instance);
		}
		if (TypeUtil.isMap(type)) {
			return copyMap(type, instance);
		}
		return copyObject(type, instance);
	}

	private static Object copyArray(Type type, Object instance) {
		Type componentType = TypeUtil.getComponentType(type);
		Object array = Array.newInstance(TypeUtil.getRawType(componentType), Array.getLength(instance));
		for (int i = 0; i < Array.getLength(instance); i++) {
			Array.set(array, i, copy(componentType, Array.get(instance, i)));
		}
		return array;
	}

	@SuppressWarnings("unchecked")
	private static Object copyCollection(Type type, Object instance) {
		Type elementType = TypeUtil.getElementType(type);
		Collection<Object> collection = (Collection<Object>) TypeUtil.newInstance(type, instance);
		for (Object e : (Collection<?>) instance) {
			collection.add(copy(elementType, e));
		}
		return collection;
	}

	@SuppressWarnings("unchecked")
	private static Object copyMap(Type type, Object instance) {
		Type keyType = TypeUtil.getKeyType(type);
		Type valueType = TypeUtil.getValueType(type);
		Map<Object, Object> map = (Map<Object, Object>) TypeUtil.newInstance(type, instance);
		for (Map.Entry<?, ?> e : ((Map<?, ?>) instance).entrySet()) {
			map.put(copy(keyType, e.getKey()), copy(valueType, e.getValue()));
		}
		return map;
	}

	private static Object copyObject(Type type, Object instance) {
		Object object = TypeUtil.newInstance(type, instance);
		for (Field f : ConfigUtil.getConfigFields(type, false)) {
			try {
				f.set(object, copy(f.getGenericType(), f.get(instance)));
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new UnsupportedOperationException(e);
			}
		}
		return object;
	}

	public static String toString(Type type, @Nullable Object instance) {
		if (instance == null) {
			return "null";
		}
		if (TypeAdapters.hasAdapter(type)) {
			return TypeAdapters.get(type).serialize(instance);
		}
		if (isArray(type)) {
			return IntStream.range(0, Array.getLength(instance))
					.mapToObj(i -> Array.get(instance, i))
					.map(e -> toString(getComponentType(type), e))
					.collect(Collectors.joining(", ", "[", "]"));
		}
		if (isCollection(type)) {
			return ((Collection<?>) instance).stream()
					.map(e -> toString(getElementType(type), e))
					.collect(Collectors.joining(", ", "[", "]"));
		}
		if (isMap(type)) {
			return ((Map<?, ?>) instance).entrySet()
					.stream()
					.map(e -> toString(getKeyType(type), e.getKey()) + "=" + toString(getValueType(type), e.getValue()))
					.collect(Collectors.joining(", ", "{", "}"));
		}
		return Arrays.stream(ConfigUtil.getConfigFields(type, instance == null))
				.map(f -> {
					try {
						return f.getName() + ": " + toString(f.getGenericType(), f.get(instance));
					} catch (IllegalArgumentException | IllegalAccessException e) {
						throw new UnsupportedOperationException(e);
					}
				})
				.collect(Collectors.joining(", ", "{ ", " }"));
	}

}
