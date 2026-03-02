package meldexun.betterconfig.gui;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import meldexun.betterconfig.TypeUtil;
import meldexun.betterconfig.api.Order;
import meldexun.betterconfig.api.RangeLong;
import meldexun.betterconfig.api.Unmodifiable;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.Config;

public interface EntryInfo {

	String name();

	default boolean hasLangKey() {
		return !StringUtils.isBlank(langKey());
	}

	String langKey();

	default String guiName() {
		return hasLangKey() && I18n.hasKey(langKey()) ? I18n.format(langKey()) : name();
	}

	default boolean hasComment() {
		return !StringUtils.isBlank(comment());
	}

	String comment();

	boolean hasLongRange();

	long minLong();

	long maxLong();

	boolean hasDoubleRange();

	double minDouble();

	double maxDouble();

	boolean slidingOption();

	boolean modifiable();

	default boolean hasDefaultValue() {
		return defaultValue() != null;
	}

	Object defaultValue();

	boolean requiresMcRestart();

	boolean requiresWorldRestart();

	int order();

	static class Builder {

		private final String name;
		private String langKey;
		private String comment;
		private boolean hasLongRange;
		private long minLong = Long.MIN_VALUE;
		private long maxLong = Long.MAX_VALUE;
		private boolean hasDoubleRange;
		private double minDouble = -Double.MAX_VALUE;
		private double maxDouble = Double.MAX_VALUE;
		private boolean slidingOption;
		private boolean modifiable;
		private Object defaultValue;
		private boolean requiresMcRestart;
		private boolean requiresWorldRestart;
		private int order;

		public Builder(String name) {
			this.name = Objects.requireNonNull(name);
		}

		public void setLangKey(String langKey) {
			this.langKey = langKey;
		}

		public void setComment(String[] comment) {
			this.comment = StringUtils.join(comment, '\n');
		}

		public void setLongRange(long min, long max) {
			this.hasLongRange = true;
			this.minLong = min;
			this.maxLong = max;
		}

		public void setDoubleRange(double min, double max) {
			this.hasDoubleRange = true;
			this.minDouble = min;
			this.maxDouble = max;
		}

		public void setSlidingOption(boolean slidingOption) {
			this.slidingOption = slidingOption;
		}

		public void setModifiable(boolean modifiable) {
			this.modifiable = modifiable;
		}

		public void setDefaultValue(Object defaultValue) {
			this.defaultValue = defaultValue;
		}

		public void setRequiresMcRestart(boolean requiresMcRestart) {
			this.requiresMcRestart = requiresMcRestart;
		}

		public void setRequiresWorldRestart(boolean requiresWorldRestart) {
			this.requiresWorldRestart = requiresWorldRestart;
		}

		public void setOrder(int order) {
			this.order = order;
		}

		public EntryInfo build() {
			String name = this.name;
			String langKey = this.langKey;
			String comment = this.comment;
			boolean hasLongRange = this.hasLongRange;
			long minLong = this.minLong;
			long maxLong = this.maxLong;
			boolean hasDoubleRange = this.hasDoubleRange;
			double minDouble = this.minDouble;
			double maxDouble = this.maxDouble;
			boolean slidingOption = this.slidingOption;
			boolean modifiable = this.modifiable;
			Object defaultValue = this.defaultValue;
			boolean requiresMcRestart = this.requiresMcRestart;
			boolean requiresWorldRestart = this.requiresWorldRestart;
			int order = this.order;
			return new EntryInfo() {
				@Override
				public String name() {
					return name;
				}

				@Override
				public String langKey() {
					return langKey;
				}

				@Override
				public String comment() {
					return comment;
				}

				@Override
				public boolean hasLongRange() {
					return hasLongRange;
				}

				@Override
				public long minLong() {
					return minLong;
				}

				@Override
				public long maxLong() {
					return maxLong;
				}

				@Override
				public boolean hasDoubleRange() {
					return hasDoubleRange;
				}

				@Override
				public double minDouble() {
					return minDouble;
				}

				@Override
				public double maxDouble() {
					return maxDouble;
				}

				@Override
				public boolean slidingOption() {
					return slidingOption;
				}

				@Override
				public boolean modifiable() {
					return modifiable;
				}

				@Override
				public Object defaultValue() {
					return defaultValue;
				}

				@Override
				public boolean requiresMcRestart() {
					return requiresMcRestart;
				}

				@Override
				public boolean requiresWorldRestart() {
					return requiresWorldRestart;
				}

				@Override
				public int order() {
					return order;
				}
			};
		}

	}

	static final Map<Object, Map<Field, EntryInfo>> CACHE = new WeakHashMap<>();

	static EntryInfo fromField(@Nullable Object instance, Field field) {
		return CACHE.computeIfAbsent(instance, k -> new HashMap<>()).computeIfAbsent(field, k -> {
			Builder builder = new Builder(field.isAnnotationPresent(Config.Name.class) ? field.getAnnotation(Config.Name.class).value() : field.getName());
			if (field.isAnnotationPresent(Config.LangKey.class)) {
				builder.setLangKey(field.getAnnotation(Config.LangKey.class).value());
			}
			if (field.isAnnotationPresent(Config.Comment.class)) {
				builder.setComment(field.getAnnotation(Config.Comment.class).value());
			}
			if (field.isAnnotationPresent(RangeLong.class)) {
				builder.setLongRange(field.getAnnotation(RangeLong.class).min(), field.getAnnotation(RangeLong.class).max());
			} else if (field.isAnnotationPresent(Config.RangeInt.class)) {
				builder.setLongRange(field.getAnnotation(Config.RangeInt.class).min(), field.getAnnotation(Config.RangeInt.class).max());
			}
			if (field.isAnnotationPresent(Config.RangeDouble.class)) {
				builder.setDoubleRange(field.getAnnotation(Config.RangeDouble.class).min(), field.getAnnotation(Config.RangeDouble.class).max());
			}
			builder.setSlidingOption(field.isAnnotationPresent(Config.SlidingOption.class));
			builder.setModifiable(!field.isAnnotationPresent(Unmodifiable.class));
			try {
				builder.setDefaultValue(TypeUtil.copy(field.getGenericType(), field.get(instance)));
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new UnsupportedOperationException(e);
			}
			builder.setRequiresMcRestart(field.isAnnotationPresent(Config.RequiresMcRestart.class));
			builder.setRequiresWorldRestart(field.isAnnotationPresent(Config.RequiresWorldRestart.class));
			if (field.isAnnotationPresent(Order.class)) {
				builder.setOrder(field.getAnnotation(Order.class).value());
			}
			return builder.build();
		});
	}

	static EntryInfo create(Class<?> type) {
		if (!type.isAnnotationPresent(Config.class)) {
			throw new IllegalArgumentException();
		}
		Config annotation = type.getAnnotation(Config.class);
		Builder builder = new Builder(!annotation.name().isEmpty() ? annotation.name() : annotation.modid());
		if (type.isAnnotationPresent(Config.LangKey.class)) {
			builder.setLangKey(type.getAnnotation(Config.LangKey.class).value());
		}
		if (type.isAnnotationPresent(Config.Comment.class)) {
			builder.setComment(type.getAnnotation(Config.Comment.class).value());
		}
		builder.setRequiresMcRestart(type.isAnnotationPresent(Config.RequiresMcRestart.class));
		builder.setRequiresWorldRestart(type.isAnnotationPresent(Config.RequiresWorldRestart.class));
		return builder.build();
	}

}
