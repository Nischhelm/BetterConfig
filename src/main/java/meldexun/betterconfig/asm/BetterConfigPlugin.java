package meldexun.betterconfig.asm;

import java.util.Map;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions({ "meldexun.betterconfig.asm", "meldexun.asmutil2" })
public class BetterConfigPlugin implements IFMLLoadingPlugin {

	public static boolean coreModInitiationComplete = false;

	public BetterConfigPlugin() {
		Launch.classLoader.registerTransformer(BetterConfigClassTransformer.class.getName());
		Launch.classLoader.registerTransformer(LoadEarlyClassTransformer.class.getName());
		Launch.classLoader.registerTransformer(ConfigurationGuiClassTransformer.class.getName());
	}

	@Override
	public String[] getASMTransformerClass() {
		return null;
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {
		coreModInitiationComplete = true; // bit sketchy but mainly want to be done with mixins into Loader
		MixinBootstrap.init();
		MixinExtrasBootstrap.init();
		if (Boolean.FALSE.equals(data.get("runtimeDeobfuscationEnabled"))) {
			MixinEnvironment.getDefaultEnvironment().setObfuscationContext("searge");
			CoreModManager.getIgnoredMods().add("mixin-0.8.7.jar");
			CoreModManager.getIgnoredMods().add("asm-util-6.2.jar");
			CoreModManager.getIgnoredMods().add("asm-analysis-6.2.jar");
			CoreModManager.getIgnoredMods().add("asm-tree-6.2.jar");
			CoreModManager.getIgnoredMods().add("asm-6.2.jar");
		}
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}

}
