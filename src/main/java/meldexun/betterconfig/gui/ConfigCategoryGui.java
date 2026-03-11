package meldexun.betterconfig.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import meldexun.betterconfig.ConfigUtil;
import meldexun.betterconfig.ConfigurationManager;
import meldexun.betterconfig.OrderUtil;
import meldexun.betterconfig.api.BetterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.GuiConfigEntries;

public class ConfigCategoryGui extends GuiConfig implements TitledGui, ConfigGui {

	private final Supplier<String> titleSupplier;
	private final BetterConfig settings;

	public ConfigCategoryGui(GuiScreen parentScreen, String title, String modID) {
		super(parentScreen, Collections.emptyList(), modID, null, false, false, null, null);
		this.titleSupplier = () -> title;
		Class<?>[] configClasses = ConfigurationManager.get(modID);
		if (configClasses.length == 1) {
			this.settings = Objects.requireNonNull(configClasses[0].getAnnotation(BetterConfig.class));
			this.entryList = new Entries(configClasses[0], null);
		} else {
			this.settings = null;
			this.entryList = new Entries(configClasses);
		}
	}

	public <T extends GuiScreen & ConfigGui> ConfigCategoryGui(T parentScreen, Supplier<String> titleSupplier, Type type, @Nullable Object instance) {
		this(parentScreen, parentScreen.settings(), titleSupplier, type, instance);
	}

	public <T extends GuiScreen & ConfigGui> ConfigCategoryGui(T parentScreen, BetterConfig settings, Supplier<String> titleSupplier, Type type, @Nullable Object instance) {
		super(parentScreen, Collections.emptyList(), "UNKNOWN", null, false, false, null, null);
		this.titleSupplier = titleSupplier;
		this.settings = settings;
		this.entryList = new Entries(type, instance);
	}

	@Override
	public void initGui() {
		if (this.parentScreen instanceof TitledGui) {
			this.title = ((TitledGui) this.parentScreen).title();
			this.titleLine2 = ((TitledGui) this.parentScreen).subscreen(this.titleSupplier.get());
		} else {
			this.title = this.titleSupplier.get();
			this.titleLine2 = null;
		}
		this.needsRefresh = false;
		super.initGui();
	}

	@Override
	public String title() {
		return this.title;
	}

	@Override
	public String subtitle() {
		return this.titleLine2;
	}

	@Override
	public BetterConfig settings() {
		return this.settings;
	}

	@Override
	public void recalculateState() {

	}

	public class Entries extends GuiConfigEntries implements GuiSlotExt {

		public Entries(Class<?>... configTypes) {
			super(ConfigCategoryGui.this, Minecraft.getMinecraft());
			for (Class<?> type : configTypes) {
				this.listEntries.add(new ConfigCategoryGuiEntry(this, type));
			}
		}

		public Entries(Type type, @Nullable Object instance) {
			super(ConfigCategoryGui.this, Minecraft.getMinecraft());
			Arrays.stream(ConfigUtil.getConfigFields(type, instance == null))
					.sorted(OrderUtil.buildComparator(ConfigCategoryGui.this.settings().elementOrder(), type, f -> EntryInfo.fromField(instance, f).name(), Field::getGenericType, f -> EntryInfo.fromField(instance, f).order()))
					.forEach(field -> {
						this.listEntries.add(this.create(instance, field));
					});
		}

		private IConfigEntry create(@Nullable Object instance, Field field) {
			return new ConfigCategoryGuiEntry(this, instance, field);
		}

		@Override
		public boolean areAnyEntriesEnabled(boolean includeChildren) {
			return this.listEntries.stream()
					.map(ConfigCategoryGuiEntry.class::cast)
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.CategoryEntry))
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.ListEntry))
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.MapEntry))
					.anyMatch(IConfigEntry::enabled);
		}

		@Override
		public boolean areAllEntriesDefault(boolean includeChildren) {
			return this.listEntries.stream()
					.map(ConfigCategoryGuiEntry.class::cast)
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.CategoryEntry))
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.ListEntry))
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.MapEntry))
					.allMatch(IConfigEntry::isDefault);
		}

		@Override
		public void setAllToDefault(boolean includeChildren) {
			this.listEntries.stream()
					.map(ConfigCategoryGuiEntry.class::cast)
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.CategoryEntry))
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.ListEntry))
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.MapEntry))
					.forEach(IConfigEntry::setToDefault);
		}

		@Override
		public boolean hasChangedEntry(boolean includeChildren) {
			return this.listEntries.stream()
					.map(ConfigCategoryGuiEntry.class::cast)
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.CategoryEntry))
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.ListEntry))
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.MapEntry))
					.anyMatch(IConfigEntry::isChanged);
		}

		@Override
		public void undoAllChanges(boolean includeChildren) {
			this.listEntries.stream()
					.map(ConfigCategoryGuiEntry.class::cast)
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.CategoryEntry))
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.ListEntry))
					.filter(e -> includeChildren || !(e.entry instanceof meldexun.betterconfig.gui.entry.MapEntry))
					.forEach(IConfigEntry::undoChanges);
		}

		@Override
		public int getListWidth() {
			return ConfigCategoryGui.this.width - 4;
		}

		@Override
		public int offsetLeft() {
			return 0;
		}

	}

}
