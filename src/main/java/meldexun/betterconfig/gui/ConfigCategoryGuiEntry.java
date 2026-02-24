package meldexun.betterconfig.gui;

import java.lang.reflect.Field;
import java.util.Objects;

import javax.annotation.Nullable;

import meldexun.betterconfig.ConfigUtil;
import meldexun.betterconfig.IGuiListEntryExtended;
import meldexun.betterconfig.gui.entry.AbstractEntry;
import meldexun.betterconfig.gui.entry.CategoryEntry;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.config.GuiConfigEntries.ListEntryBase;
import net.minecraftforge.fml.client.config.HoverChecker;
import net.minecraftforge.fml.client.config.IConfigElement;

public class ConfigCategoryGuiEntry extends ListEntryBase implements IGuiListEntryExtended, ListEntryBaseExt {

	@Nullable
	protected final Object instance;
	protected final Field field;
	protected final EntryInfo info;
	protected final Object beforeValue;
	protected final AbstractEntry entry;

	@SuppressWarnings("unchecked")
	public <T extends GuiScreen & ConfigGui> ConfigCategoryGuiEntry(ConfigCategoryGui.Entries owningEntryList, @Nullable Object instance, Field field) {
		super(owningEntryList.owningScreen, owningEntryList, new DummyConfigElement(EntryInfo.fromField(instance, field), field.getGenericType()));
		this.instance = instance;
		this.field = field;
		this.info = EntryInfo.fromField(this.instance, this.field);
		try {
			this.beforeValue = Objects.requireNonNull(this.field.get(this.instance));
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new UnsupportedOperationException(e);
		}
		this.entry = AbstractEntry.create((T) this.owningScreen, this.info.guiName(), this.info, this.field.getGenericType(), this.info.defaultValue(), this.beforeValue);
		if (this.entry instanceof CategoryEntry) {
			this.drawLabel = false;
			((CategoryEntry) this.entry).getButton().displayString = this.getName();
			this.tooltipHoverChecker = new HoverChecker(((CategoryEntry) this.entry).getButton(), 800);
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends GuiScreen & ConfigGui> ConfigCategoryGuiEntry(ConfigCategoryGui.Entries owningEntryList, Class<?> type) {
		super(owningEntryList.owningScreen, owningEntryList, new DummyConfigElement(EntryInfo.create(type), type));
		this.instance = null;
		this.field = null;
		this.info = EntryInfo.create(type);
		this.beforeValue = null;
		this.entry = AbstractEntry.create((T) this.owningScreen, this.info.guiName(), this.info, type, this.info.defaultValue(), this.beforeValue);
		if (this.entry instanceof CategoryEntry) {
			this.drawLabel = false;
			((CategoryEntry) this.entry).getButton().displayString = this.getName();
			this.tooltipHoverChecker = new HoverChecker(((CategoryEntry) this.entry).getButton(), 800);
		}
	}

	@Override
	public void drawEntry(int index, int x, int y, int width, int height, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
		this.isValidValue = this.entry.isValueSavable();
		super.drawEntry(index, x, y, width, height, mouseX, mouseY, isSelected, partialTicks);
		this.entry.drawEntry(index, this.drawLabel ? this.owningEntryList.controlX : this.owningEntryList.labelX, y, this.drawLabel ? this.owningEntryList.controlWidth : this.owningEntryList.controlWidth + this.owningEntryList.controlX - this.owningEntryList.labelX, height, mouseX, mouseY, isSelected, partialTicks);
	}

	@Override
	public void drawToolTip(int mouseX, int mouseY) {
		super.drawToolTip(mouseX, mouseY);
		this.entry.drawToolTip(mouseX, mouseY);
	}

	@Override
	public boolean mousePressedAll(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {
		if (this.entry.mousePressed(index, x, y, mouseEvent, relativeX, relativeY)) {
			return true;
		}
		return mouseEvent == 0 && this.mousePressed(index, x, y, mouseEvent, relativeX, relativeY);
	}

	@Override
	public void mouseReleased(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {
		super.mouseReleased(index, x, y, mouseEvent, relativeX, relativeY);
		this.entry.mouseReleased(index, x, y, mouseEvent, relativeX, relativeY);
	}

	@Override
	public void mouseClicked(int x, int y, int mouseEvent) {
		this.entry.mouseClicked(x, y, mouseEvent);
	}

	@Override
	public void keyTyped(char eventChar, int eventKey) {
		this.entry.keyTyped(eventChar, eventKey);
	}

	@Override
	public void updateCursorCounter() {
		this.entry.updateCursorCounter();
	}

	@Override
	public boolean isDefault() {
		return this.entry.isDefault();
	}

	@Override
	public void setToDefault() {
		this.entry.setToDefault();
	}

	@Override
	public boolean isChanged() {
		return this.entry.isChanged();
	}

	@Override
	public void undoChanges() {
		this.entry.undoChanges();
	}

	@Override
	public boolean saveConfigElement() {
		if (!this.enabled()) {
			return false;
		}
		if (!this.isValidValue) {
			return false;
		}
		if (!this.isChanged()) {
			return false;
		}
		boolean requiresMcRestart = this.entry.saveChanges();
		if (this.field != null && ConfigUtil.isNonMapCategory(this.field.getGenericType())) {
			try {
				this.field.set(this.instance, this.entry.getValue());
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new UnsupportedOperationException(e);
			}
		}
		return requiresMcRestart;
	}

	@Override
	public boolean enabled() {
		return this.entry.enabled();
	}

	@Override
	public String getName() {
		return this.info.name();
	}

	@Override
	public int getLabelWidth() {
		return this.drawLabel ? super.getLabelWidth() : 0;
	}

	@Override
	public int getEntryRightBound() {
		return this.drawLabel ? super.getEntryRightBound() : this.owningEntryList.width / 2 + 155 + 22 + 18;
	}

	@Deprecated
	@Override
	public final IConfigElement getConfigElement() {
		throw new UnsupportedOperationException();
	}

	@Deprecated
	@Override
	public Object getCurrentValue() {
		throw new UnsupportedOperationException();
	}

	@Deprecated
	@Override
	public Object[] getCurrentValues() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int buttonOffsetY() {
		return -1;
	}

}
