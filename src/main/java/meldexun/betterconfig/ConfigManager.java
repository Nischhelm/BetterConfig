package meldexun.betterconfig;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;

import meldexun.betterconfig.api.BetterConfig;
import meldexun.betterconfig.api.Sync;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.LoaderException;

public class ConfigManager {

	private static final Path CONFIG_DIRECTORY = (Launch.minecraftHome != null ? Launch.minecraftHome : new File(".")).toPath().resolve("config");
	private static final Map<String, SetMultimap<Path, Class<?>>> MODID_2_FILE_2_CONFIG_CLASSES = new HashMap<>();
	private static final Map<Path, Config> CONFIGS = new HashMap<>();
	private static final SetMultimap<Path, String> LOADED_CATEGORIES = HashMultimap.create();
	private static final Map<Class<?>, Class<?>> SYNCED_CONFIGS = new HashMap<>();

	public static void register(Class<?> configClass) {
		register(configClass, false);
	}

	public static void registerAndLoad(Class<?> configClass) {
		register(configClass, true);
	}

	private static synchronized void register(Class<?> configClass, boolean load) {
		if (AnnotationUtil.isPresent(configClass, Sync.class)) {
			configClass = SYNCED_CONFIGS.computeIfAbsent(configClass, k -> {
				try {
					String className = k.getName();
					String classNameInternal = className.replace('.', '/');
					String dummyClassName = className + "$BetterConfigSync";
					String dummyClassNameInternal = dummyClassName.replace('.', '/');

					ClassReader classReader = new ClassReader(Launch.classLoader.getClassBytes(className));
					ClassWriter classWriter = new ClassWriter(classReader, 0);
					classReader.accept(new ClassVisitor(Opcodes.ASM5, classWriter) {
						@Override
						public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
							super.visit(version, access, dummyClassNameInternal, signature, superName, interfaces);
						}

						@Override
						public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
							if ((access & Opcodes.ACC_STATIC) == 0) {
								return null;
							}
							return new MethodVisitor(this.api, super.visitMethod(access, name, desc, signature, exceptions)) {
								@Override
								public void visitFieldInsn(int opcode, String owner, String name, String desc) {
									if ((opcode == Opcodes.PUTSTATIC || opcode == Opcodes.GETSTATIC) && owner.equals(classNameInternal)) {
										owner = dummyClassNameInternal;
									}
									super.visitFieldInsn(opcode, owner, name, desc);
								}

								@Override
								public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
									if (opcode == Opcodes.INVOKESTATIC && owner.equals(classNameInternal)) {
										owner = dummyClassNameInternal;
									}
									super.visitMethodInsn(opcode, owner, name, desc, itf);
								}

								@Override
								public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
									for (int i = 0; i < bsmArgs.length; i++) {
										Object bsmArg = bsmArgs[i];
										if (!(bsmArg instanceof Handle)) {
											continue;
										}
										Handle bsmArgHandle = (Handle) bsmArg;
										if (bsmArgHandle.getOwner().equals(classNameInternal)) {
											bsmArgs[i] = new Handle(bsmArgHandle.getTag(), dummyClassNameInternal, bsmArgHandle.getName(), bsmArgHandle.getDesc(), bsmArgHandle.isInterface());
										}
									}
									super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
								}
							};
						}

						@Override
						public void visitInnerClass(String name, String outerName, String innerName, int access) {
							// ignore
						}

						@Override
						public void visitOuterClass(String owner, String name, String desc) {
							// ignore
						}
					}, 0);
					byte[] dummyClassBytes = classWriter.toByteArray();
					Files.write(Paths.get(dummyClassName + ".class"), dummyClassBytes);

					Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
					defineClass.setAccessible(true);
					return (Class<?>) defineClass.invoke(Launch.classLoader, dummyClassName, dummyClassBytes, 0, dummyClassBytes.length);
				} catch (Exception e) {
					throw new UnsupportedOperationException(e);
				}
			});
		}

		BetterConfig configAnnotation = AnnotationUtil.getOrThrow(configClass, BetterConfig.class);
		String modid = configAnnotation.modid();
		if (StringUtils.isBlank(modid)) {
			throw new LoaderException("BetterConfig annotation modid of class " + configClass.getName() + " may not be blank!");
		}
		String configName = !configAnnotation.name().isEmpty() ? configAnnotation.name() : modid;
		Path file = CONFIG_DIRECTORY.resolve(configName + ".cfg");
		MODID_2_FILE_2_CONFIG_CLASSES.computeIfAbsent(modid, k -> HashMultimap.create()).put(file, configClass);
		if (load) {
			try {
				Config config = CONFIGS.computeIfAbsent(file, k -> {
					try {
						Config v = new Config();
						v.load(k);
						return v;
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});

				String categoryName = configAnnotation.category();
				ConfigCategory category = config.getOrCreateCategory(categoryName);
				if (LOADED_CATEGORIES.put(file, categoryName)) {
					category.loadAnnotations(configAnnotation, configClass, ConfigElementMetadata.create(configClass), null);
					category.loadFromConfig(configAnnotation, configClass, null);
				}
			} catch (Exception e) {
				throw new LoaderException(e);
			}
		}
	}

	public static synchronized void sync(String modid) {
		MODID_2_FILE_2_CONFIG_CLASSES.getOrDefault(modid, ImmutableSetMultimap.of())
				.asMap()
				.forEach((file, configClasses) -> {
					try {
						Config config = CONFIGS.computeIfAbsent(file, k -> {
							try {
								Config v = new Config();
								v.load(k);
								return v;
							} catch (IOException e) {
								throw new UncheckedIOException(e);
							}
						});

						for (Class<?> configClass : configClasses) {
							BetterConfig settings = AnnotationUtil.getOrThrow(configClass, BetterConfig.class);
							String categoryName = settings.category();
							ConfigCategory category = config.getOrCreateCategory(categoryName);
							if (LOADED_CATEGORIES.put(file, categoryName)) {
								category.loadAnnotations(settings, configClass, ConfigElementMetadata.create(configClass), null);
								category.loadFromConfig(settings, configClass, null);
							}
							category.saveToConfig(settings, configClass, null);
						}

						config.save(file, configClasses.stream()
								.map(c -> AnnotationUtil.getOrThrow(c, BetterConfig.class))
								.collect(Collectors.toMap(BetterConfig::category, Function.identity()))::get);
					} catch (Exception e) {
						throw new LoaderException(e);
					}
				});
	}

	public static synchronized boolean has(String modId) {
		return MODID_2_FILE_2_CONFIG_CLASSES.containsKey(modId);
	}

	public static synchronized Class<?>[] get(String modid) {
		return MODID_2_FILE_2_CONFIG_CLASSES.get(modid).values().toArray(new Class[0]);
	}

	public static synchronized Class<?>[] syncedConfigs() {
		return SYNCED_CONFIGS.values().toArray(new Class[0]);
	}

}
