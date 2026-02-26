package meldexun.betterconfig.mixin.configuration;

import meldexun.betterconfig.gui.configuration.ConfigurationGuiFactory;
import net.minecraft.client.gui.GuiButton;
import net.minecraftforge.fml.client.config.GuiConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiConfig.class)
public abstract class GuiConfigMixin {
    @Shadow(remap = false) @Final public String modID;

    @Inject(
            method = "actionPerformed",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/common/eventhandler/EventBus;post(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z", ordinal = 1, remap = false)
    )
    private void actionPerformedMixin(GuiButton button, CallbackInfo ci) {
        ConfigurationGuiFactory.save(modID);
    }
}
