package meldexun.betterconfig;

import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import meldexun.betterconfig.api.BetterConfig.ConfigComparator;
import net.minecraft.launchwrapper.Launch;

public class OrderUtil {

	public static <T> Comparator<T> buildComparator(ConfigComparator[] configComparators, Type type, Function<T, String> getName, Function<T, Type> getType, ToIntFunction<T> getOrder) {
		Comparator<T> comparator = null;
		if (configComparators != null) {
			for (ConfigComparator configComparator : configComparators) {
				if (configComparator == null) {
					continue;
				}
				Comparator<T> next;
				switch (configComparator) {
				case EXPLICIT:
					next = comparingInt(getOrder);
					break;
				case CATEGORIES_FIRST:
					next = comparing(getType, comparingBoolean(((Predicate<Type>) ConfigUtil::isCategory).negate()));
					break;
				case CATEGORIES_LAST:
					next = comparing(getType, comparingBoolean(ConfigUtil::isCategory));
					break;
				case NON_MAP_CATEGORIES_FIRST:
					next = comparing(getType, comparingBoolean(((Predicate<Type>) ConfigUtil::isNonMapCategory).negate()));
					break;
				case NON_MAP_CATEGORIES_LAST:
					next = comparing(getType, comparingBoolean(ConfigUtil::isNonMapCategory));
					break;
				case NAME_CASE_SENSITIVE:
					next = comparing(getName);
					break;
				case NAME_CASE_INSENSITIVE:
					next = comparing(getName, String.CASE_INSENSITIVE_ORDER);
					break;
				case INITIALIZATION:
					next = initialization(type, getName);
					break;
				default:
					continue;
				}
				comparator = comparator != null ? comparator.thenComparing(next) : next;
			}
		}
		if (comparator == null) {
			comparator = comparing(getName, String.CASE_INSENSITIVE_ORDER);
		}
		return comparator;
	}

	private static <T> Comparator<T> comparingBoolean(Predicate<T> p) {
		return (t1, t2) -> Boolean.compare(p.test(t1), p.test(t2));
	}

	private static <T> Comparator<T> initialization(Type type, Function<T, String> getName) {
		if (!ConfigUtil.isNonMapCategory(type)) {
			throw new IllegalArgumentException();
		}
		return comparing(getName, comparingInt(initializationLines(TypeUtil.getRawType(type))::getInt));
	}

	private static final Map<Class<?>, Object2IntMap<String>> CACHE = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());

	private static Object2IntMap<String> initializationLines(Class<?> type) {
		synchronized (CACHE) {
			Object2IntMap<String> value = CACHE.get(type);
			if (value == null) {
				value = new Object2IntOpenHashMap<>();

				// copy entries from superclass
				if (type.getSuperclass() != null && !type.getSuperclass().equals(Object.class)) {
					for (Object2IntMap.Entry<String> entry : initializationLines(type.getSuperclass()).object2IntEntrySet()) {
						value.put(entry.getKey(), entry.getIntValue() - 1_000_000); // add offset to get a consistent ordering
					}
				}

				// read bytecode to get initialization lines
				byte[] classBytes;
				try {
					classBytes = Launch.classLoader.getClassBytes(type.getName());
				} catch (IOException e) {
					throw new UnsupportedOperationException(e);
				}
				if (classBytes != null) {
					String internalClassName = org.objectweb.asm.Type.getInternalName(type);
					Object2IntMap<String> value1 = value;
					new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM5) {
						@Override
						public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
							if (!name.equals("<clinit>") && !name.equals("<init>")) {
								return null;
							}
							return new MethodVisitor(Opcodes.ASM5) {
								int line;

								@Override
								public void visitLineNumber(int line, Label start) {
									this.line = line;
								}

								@Override
								public void visitFieldInsn(int opcode, String owner, String name, String desc) {
									if ((opcode == Opcodes.PUTSTATIC || opcode == Opcodes.PUTFIELD) && owner.equals(internalClassName)) {
										value1.put(name, this.line);
									}
								}
							};
						}
					}, ClassReader.SKIP_FRAMES);
				}

				CACHE.put(type, value = Object2IntMaps.unmodifiable(value));
			}
			return value;
		}
	}

}
