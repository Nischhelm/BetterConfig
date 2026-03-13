package meldexun.betterconfig.asm;

import java.util.MissingResourceException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import meldexun.asmutil2.AbstractClassTransformer;
import meldexun.asmutil2.reader.ClassUtil;
import net.minecraft.launchwrapper.IClassTransformer;

public class ConfigurationGuiClassTransformer extends AbstractClassTransformer implements IClassTransformer {

	@Override
	protected byte[] transformOrNull(String name, String transformedName, byte[] basicClass) {
		if (basicClass == null) {
			return null;
		}
		if (transformedName.startsWith("net.minecraft")) {
			return null; // ignore all mc and forge classes
		}
		try {
			if (ClassUtil.DEFAULT.findInClassHierarchy(name.replace('.', '/'), "net/minecraftforge/common/config/Configuration"::equals) != null) {
				// TODO: grab these classes extending Configuration differently, prob at tail of their <init>
				return null;
			}
		} catch (MissingResourceException e) {
			return null;
		}
		ClassReader reader = new ClassReader(basicClass);
		ClassWriter writer = new ClassWriter(reader, 0);
		AtomicBoolean modified = new AtomicBoolean();
		reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				return new MethodVisitor(this.api, super.visitMethod(access, name, desc, signature, exceptions)) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
						super.visitMethodInsn(opcode, owner, name, desc, itf);
						if (owner.equals("net/minecraftforge/common/config/Configuration") && name.equals("<init>") && !desc.equals("()V")) {
							this.mv.visitInsn(Opcodes.DUP);
							this.mv.visitMethodInsn(Opcodes.INVOKESTATIC, "meldexun/betterconfig/gui/configuration/ConfigurationGuiRegistry", "registerConfiguration", "(Lnet/minecraftforge/common/config/Configuration;)V", false);
							modified.set(true);
						}
					}
				};
			}
		}, 0);
		if (modified.get()) {
			return writer.toByteArray();
		}
		return null;
	}

}
