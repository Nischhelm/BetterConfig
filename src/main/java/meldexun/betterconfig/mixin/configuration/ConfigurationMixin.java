package meldexun.betterconfig.mixin.configuration;

import meldexun.betterconfig.gui.configuration.ConfigurationGuiFactory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Configuration.class)
public abstract class ConfigurationMixin {
    @Shadow(remap = false) private String fileName;

    @Unique private static final String FILE_ENDING = "\\.\\w+$";

    @Inject(
            method = "runConfiguration",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/config/Configuration;load()V"),
            remap = false
    )
    private void betterConfig_saveConstructedConfiguration(CallbackInfo ci){
        ModContainer modContainer = Loader.instance().activeModContainer();
        if(modContainer == null) return;
        String modid = modContainer.getModId();
        //best guess: probably in /config, maybe in /config/modid. remove .cfg, .json etc
        String configName = this.fileName.replace("/config/","").replace(modid+"/","").replaceFirst(FILE_ENDING, "");
        ConfigurationGuiFactory.registerConfiguration(modid, configName, (Configuration) (Object) this);
    }
}
