package meldexun.betterconfig.mixin.configuration;

import net.minecraftforge.common.config.ConfigCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.ArrayList;

@Mixin(ConfigCategory.class)
public interface ConfigCategoryAccessor {
    @Accessor("children")
    ArrayList<ConfigCategory> getChildrenList();
}
