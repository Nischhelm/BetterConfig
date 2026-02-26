package meldexun.betterconfig.mixin.configuration;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meldexun.betterconfig.gui.configuration.ConfigurationGuiFactory;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.common.ModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiModList.class)
public abstract class GuiModListMixin {
    @WrapOperation(
            method = {"actionPerformed", "func_146284_a(Lnet/minecraft/client/gui/GuiButton;)V", "updateCache"}, remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/client/FMLClientHandler;getGuiFactoryFor(Lnet/minecraftforge/fml/common/ModContainer;)Lnet/minecraftforge/fml/client/IModGuiFactory;")
    )
    private IModGuiFactory betterConfig_injectConfigurationGui(FMLClientHandler instance, ModContainer selectedMod, Operation<IModGuiFactory> original) {
        IModGuiFactory originalFactory = original.call(instance, selectedMod);
        return originalFactory == null && ConfigurationGuiFactory.hasGuiFor(selectedMod.getModId()) ? ConfigurationGuiFactory.INSTANCE : originalFactory;
    }
}
