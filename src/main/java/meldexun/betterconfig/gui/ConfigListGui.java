package meldexun.betterconfig.gui;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

import meldexun.betterconfig.TypeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.config.GuiEditArray;
import net.minecraftforge.fml.client.config.GuiEditArrayEntries;
import net.minecraftforge.fml.client.config.HoverChecker;

public class ConfigListGui extends GuiEditArray implements TitledGui, ConfigGui {

	private final Supplier<String> titleSupplier;
	private final EntryInfo info;
	private final Type type;
	private final Object defaultValue;
	private final Object beforeValue;

	public ConfigListGui(GuiScreen parentScreen, Supplier<String> titleSupplier, EntryInfo info, Type type, Object defaultValue, Object beforeValue) {
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
			super(ConfigListGui.this, Minecraft.getMinecraft(), ConfigListGui.this.configElement, new Object[0], new Object[0]);

			this.listEntries.clear();
			if (TypeUtil.isArray(ConfigListGui.this.type)) {
				Type componentType = TypeUtil.getComponentType(ConfigListGui.this.type);
				for (int i = 0; i < Array.getLength(value); i++) {
					this.listEntries.add(this.create(componentType, Array.get(value, i)));
				}
			} else if (TypeUtil.isCollection(ConfigListGui.this.type)) {
				Type componentType = TypeUtil.getElementType(ConfigListGui.this.type);
				for (Object element : (Collection<?>) value) {
					this.listEntries.add(this.create(componentType, element));
				}
			} else {
				throw new IllegalArgumentException();
			}
			if (ConfigListGui.this.info.modifiable()) {
				this.listEntries.add(new BaseEntry(ConfigListGui.this, this, this.configElement));
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

		public ConfigListGui owningScreen() {
			return ConfigListGui.this;
		}

		private IArrayEntry create(Type type) {
			return this.create(type, Optional.empty());
		}

		private IArrayEntry create(Type type, Object instance) {
			return this.create(type, Optional.of(instance));
		}

		private IArrayEntry create(Type type, Optional<Object> instance) {
			return new ConfigListGuiEntry(this, type, instance);
		}

		@Override
		public void addNewEntry(int index) {
			this.listEntries.add(index, this.create(TypeUtil.getComponentOrElementType(ConfigListGui.this.type)));
		}

		@Override
		public void removeEntry(int index) {
			this.listEntries.remove(index);
		}

		@Override
		public void recalculateState() {
			this.isDefault = TypeUtil.equals(ConfigListGui.this.type, this.getValue(), ConfigListGui.this.defaultValue);
			this.isChanged = !TypeUtil.equals(ConfigListGui.this.type, this.getValue(), ConfigListGui.this.beforeValue);
		}

		@SuppressWarnings("unchecked")
		public Object getValue() {
			int size = this.listEntries.size() - (ConfigListGui.this.info.modifiable() ? 1 : 0);
			Object value;
			if (TypeUtil.isArray(ConfigListGui.this.type)) {
				value = Array.newInstance(TypeUtil.getRawType(TypeUtil.getComponentType(ConfigListGui.this.type)), size);
				for (int i = 0; i < size; i++) {
					Array.set(value, i, this.listEntries.get(i).getValue());
				}
			} else if (TypeUtil.isCollection(ConfigListGui.this.type)) {
				value = TypeUtil.newInstance(ConfigListGui.this.type, ConfigListGui.this.beforeValue);
				for (int i = 0; i < size; i++) {
					((Collection<Object>) value).add(this.listEntries.get(i).getValue());
				}
			} else {
				throw new IllegalStateException();
			}
			return value;
		}

	}

}
