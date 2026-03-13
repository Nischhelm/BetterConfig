package meldexun.betterconfig.mixin.configuration;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meldexun.betterconfig.gui.configuration.ConfigurationGuiFactory;
import meldexun.betterconfig.gui.configuration.ConfigurationGuiRegistry;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.common.ModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiModList.class)
public abstract class GuiModListMixin {
    @Shadow(remap = false) private ModContainer selectedMod;

    @ModifyExpressionValue(
            method = {"actionPerformed", "func_146284_a(Lnet/minecraft/client/gui/GuiButton;)V", "updateCache"}, remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/client/FMLClientHandler;getGuiFactoryFor(Lnet/minecraftforge/fml/common/ModContainer;)Lnet/minecraftforge/fml/client/IModGuiFactory;")
    )
    private IModGuiFactory betterConfig_injectConfigurationGui(IModGuiFactory original) {
        if(!ConfigurationGuiFactory.hasGuiFor(selectedMod.getModId()))
            return original;
        if(original != null) { //mod got its own factory, no need to keep it registered
            ConfigurationGuiRegistry.unregister(selectedMod.getModId());
            return original;
        }
        return ConfigurationGuiFactory.INSTANCE;
    }
}
