package meldexun.betterconfig.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import meldexun.betterconfig.ConfigurationManager;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;

@Mixin(value = ConfigManager.class, remap = false)
public class ConfigManagerMixin {

	@Overwrite(remap = false)
	public static void sync(String modid, Config.Type type) {
		ConfigurationManager.sync(modid, type);
	}

}
