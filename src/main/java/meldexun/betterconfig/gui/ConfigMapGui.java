package meldexun.betterconfig.gui;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import meldexun.betterconfig.TypeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.config.GuiEditArray;
import net.minecraftforge.fml.client.config.GuiEditArrayEntries;
import net.minecraftforge.fml.client.config.HoverChecker;

public class ConfigMapGui extends GuiEditArray implements TitledGui, ConfigGui {

	private final Supplier<String> titleSupplier;
	private final EntryInfo info;
	private final Type type;
	private final Object defaultValue;
	private final Object beforeValue;

	public ConfigMapGui(GuiScreen parentScreen, Supplier<String> titleSupplier, EntryInfo info, Type type, Object defaultValue, Object beforeValue) {
		super(parentScreen, new DummyConfigElement(info, type, defaultValue), 0, new Object[0], true);
		this.titleSupplier = titleSupplier;
		this.info = info;
		this.type = type;
		this.defaultValue = defaultValue;
		this.beforeValue = beforeValue;
		this.entryList = new Entries(beforeValue);
	}

	@Override
	public void initGui() {
		if (this.parentScreen instanceof TitledGui) {
			this.title = ((TitledGui) this.parentScreen).title();
			this.titleLine2 = ((TitledGui) this.parentScreen).subscreen(this.titleSupplier.get());
			this.titleLine3 = null;
			this.tooltipHoverChecker = new HoverChecker(28, 37, 0, this.parentScreen.width, 800);
		} else {
			this.title = this.titleSupplier.get();
			this.titleLine2 = null;
			this.titleLine3 = null;
			this.tooltipHoverChecker = new HoverChecker(8, 17, 0, this.parentScreen.width, 800);
		}

		((Entries) this.entryList).initGui();

		super.initGui();
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (button.id == 2000) {
			this.mc.displayGuiScreen(this.parentScreen);
		} else if (button.id == 2001) {
			this.setValue(this.defaultValue);
		} else if (button.id == 2002) {
			this.setValue(this.beforeValue);
		}
	}

	@Deprecated
	@Override
	protected GuiEditArrayEntries createEditArrayEntries() {
		return this.entryList;
	}

	@Override
	public String title() {
		return this.title;
	}

	@Override
	public String subtitle() {
		return this.titleLine2;
	}

	public EntryInfo info() {
		return this.info;
	}

	public Object getValue() {
		return ((Entries) this.entryList).getValue();
	}

	public void setValue(Object value) {
		this.entryList = new Entries(value);
	}

	public void recalculateState() {
		this.entryList.recalculateState();
	}

	public class Entries extends GuiEditArrayEntries {

		public Entries(Object value) {
			super(ConfigMapGui.this, Minecraft.getMinecraft(), ConfigMapGui.this.configElement, new Object[0], new Object[0]);

			this.listEntries.clear();
			if (TypeUtil.isMap(ConfigMapGui.this.type)) {
				Type keyType = TypeUtil.getKeyType(ConfigMapGui.this.type);
				Type valueType = TypeUtil.getValueType(ConfigMapGui.this.type);
				((Map<?, ?>) value).forEach((k, v) -> {
					this.listEntries.add(this.create(keyType, valueType, k, v));
				});
			} else {
				throw new IllegalArgumentException();
			}
			if (ConfigMapGui.this.info.modifiable()) {
				this.listEntries.add(new BaseEntry(ConfigMapGui.this, this, this.configElement));
			}

			this.recalculateState();
		}

		protected void initGui() {
			this.width = this.owningGui.width;
			this.height = this.owningGui.height;
			this.top = this.owningScreen().subtitle() != null ? 33 : 23;
			this.bottom = this.owningGui.height - 32;
			this.left = 0;
			this.right = this.width;
			GuiUtil.setControlWidth(this, (this.owningGui.width / 2) - (this.configElement.isListLengthFixed() ? 0 : 48));
		}

		public ConfigMapGui owningScreen() {
			return ConfigMapGui.this;
		}

		private IArrayEntry create(Type keyType, Type valueType) {
			return this.create(keyType, valueType, Optional.empty(), Optional.empty());
		}

		private IArrayEntry create(Type keyType, Type valueType, Object key, Object value) {
			return this.create(keyType, valueType, Optional.of(key), Optional.of(value));
		}

		private IArrayEntry create(Type keyType, Type valueType, Optional<Object> key, Optional<Object> value) {
			return new ConfigMapGuiEntry(this, keyType, valueType, key, value);
		}

		@Override
		public void addNewEntry(int index) {
			this.listEntries.add(index, this.create(TypeUtil.getKeyType(ConfigMapGui.this.type), TypeUtil.getValueType(ConfigMapGui.this.type)));
		}

		@Override
		public void removeEntry(int index) {
			this.listEntries.remove(index);
		}

		@Override
		public void recalculateState() {
			this.isDefault = TypeUtil.equals(ConfigMapGui.this.type, this.getValue(), ConfigMapGui.this.defaultValue);
			this.isChanged = !TypeUtil.equals(ConfigMapGui.this.type, this.getValue(), ConfigMapGui.this.beforeValue);
		}

		@SuppressWarnings("unchecked")
		public Object getValue() {
			int size = this.listEntries.size() - (ConfigMapGui.this.info.modifiable() ? 1 : 0);
			Object value;
			if (TypeUtil.isMap(ConfigMapGui.this.type)) {
				value = TypeUtil.newInstance(ConfigMapGui.this.type, ConfigMapGui.this.beforeValue);
				for (int i = 0; i < size; i++) {
					ConfigMapGuiEntry entry = (ConfigMapGuiEntry) this.listEntries.get(i);
					((Map<Object, Object>) value).put(entry.getKey(), entry.getValue());
				}
			} else {
				throw new IllegalStateException();
			}
			return value;
		}

	}

}
