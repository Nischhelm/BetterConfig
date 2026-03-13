package meldexun.betterconfig.handler;

import meldexun.betterconfig.gui.configuration.ConfigurationGuiRegistry;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber
public class OnConfigChangedHandler {

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        ConfigurationGuiRegistry.save(event.getModID());
    }

}