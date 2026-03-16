package meldexun.betterconfig;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import meldexun.betterconfig.api.BetterConfig;
import meldexun.betterconfig.api.OptionalConfig;
import meldexun.betterconfig.api.Order;
import meldexun.betterconfig.api.RangeLong;
import meldexun.betterconfig.api.Unmodifiable;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.Config;

public interface ConfigElementMetadata {

	String name();

	default boolean hasLangKey() {
		return !StringUtils.isBlank(this.langKey());
	}

	String langKey();

	default String guiName() {
		return this.hasLangKey() && I18n.hasKey(this.langKey()) ? I18n.format(this.langKey()) : this.name();
	}

	default boolean hasComment() {
		return !StringUtils.isBlank(this.comment());
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

	boolean optional();

	default boolean hasDefaultValue() {
		return this.defaultValue() != null;
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
		private boolean modifiable = true;
		private boolean optional = false;
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

		public void setOptional(boolean optional) {
			this.optional = optional;
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

		public ConfigElementMetadata build() {
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
			boolean optional = this.optional;
			Object defaultValue = this.defaultValue;
			boolean requiresMcRestart = this.requiresMcRestart;
			boolean requiresWorldRestart = this.requiresWorldRestart;
			int order = this.order;
			return new ConfigElementMetadata() {
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
				public boolean optional() {
					return optional;
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

	static final Map<Object, Map<Field, ConfigElementMetadata>> CACHE = new WeakHashMap<>();

	static ConfigElementMetadata fromField(@Nullable Object instance, Field field) {
		return CACHE.computeIfAbsent(instance, k -> new HashMap<>()).computeIfAbsent(field, k -> {
			Builder builder = new Builder(AnnotationUtil.map(field, Config.Name.class, Config.Name::value, field.getName()));
			AnnotationUtil.ifPresent(field, Config.LangKey.class, langKey -> builder.setLangKey(langKey.value()));
			AnnotationUtil.ifPresent(field, Config.Comment.class, comment -> builder.setComment(comment.value()));
			AnnotationUtil.ifPresent(field, Config.RequiresMcRestart.class, a -> builder.setRequiresMcRestart(true));
			AnnotationUtil.ifPresent(field, Config.RequiresWorldRestart.class, a -> builder.setRequiresWorldRestart(true));
			AnnotationUtil.ifPresent(field, Config.RangeInt.class, rangeInt -> builder.setLongRange(rangeInt.min(), rangeInt.max()));
			AnnotationUtil.ifPresent(field, Config.RangeDouble.class, rangeDouble -> builder.setDoubleRange(rangeDouble.min(), rangeDouble.max()));
			AnnotationUtil.ifPresent(field, Config.SlidingOption.class, a -> builder.setSlidingOption(true));
			try {
				builder.setDefaultValue(TypeUtil.copy(field.getGenericType(), field.get(instance)));
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new UnsupportedOperationException(e);
			}
			AnnotationUtil.ifPresent(field, RangeLong.class, rangeLong -> builder.setLongRange(rangeLong.min(), rangeLong.max()));
			AnnotationUtil.ifPresent(field, Unmodifiable.class, a -> builder.setModifiable(false));
			AnnotationUtil.ifPresent(field, OptionalConfig.class, a -> builder.setOptional(true));
			AnnotationUtil.ifPresent(field, Order.class, order -> builder.setOrder(order.value()));
			return builder.build();
		});
	}

	static ConfigElementMetadata create(Class<?> type) {
		BetterConfig annotation = AnnotationUtil.getOrThrow(type, BetterConfig.class);
		Builder builder = new Builder(StringUtils.defaultIfEmpty(annotation.name(), annotation.modid()));
		AnnotationUtil.ifPresent(type, Config.LangKey.class, langKey -> builder.setLangKey(langKey.value()));
		AnnotationUtil.ifPresent(type, Config.Comment.class, comment -> builder.setComment(comment.value()));
		AnnotationUtil.ifPresent(type, Config.RequiresMcRestart.class, a -> builder.setRequiresMcRestart(true));
		AnnotationUtil.ifPresent(type, Config.RequiresWorldRestart.class, a -> builder.setRequiresWorldRestart(true));
		return builder.build();
	}

}
