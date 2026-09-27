package dev.bhored.zocular.config;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * One editable value in {@link ZocularConfig}. The settings screen is built from these, and loading the
 * config runs every value through {@link #sanitize()} so hand-edited files can't go out of range.
 */
public abstract sealed class Setting<T> implements Settings.Entry permits Setting.Toggle, Setting.Slider, Setting.Cycle {
	private final String key;
	private final Function<ZocularConfig, T> getter;
	private final BiConsumer<ZocularConfig, T> setter;
	private BooleanSupplier visible = () -> true;

	private Setting(String key, Function<ZocularConfig, T> getter, BiConsumer<ZocularConfig, T> setter) {
		this.key = key;
		this.getter = getter;
		this.setter = setter;
	}

	public String key() {
		return key;
	}

	public Component name() {
		return Component.translatable("zocular.option." + key);
	}

	public Component description() {
		return Component.translatable("zocular.option." + key + ".desc");
	}

	public T get() {
		return getter.apply(ZocularConfig.get());
	}

	public void set(T value) {
		setter.accept(ZocularConfig.get(), clean(value));
	}

	public T defaultValue() {
		return getter.apply(ZocularConfig.DEFAULTS);
	}

	public boolean isDefault() {
		return Objects.equals(get(), defaultValue());
	}

	public void reset() {
		set(defaultValue());
	}

	public Setting<T> visibleWhen(BooleanSupplier condition) {
		this.visible = condition;
		return this;
	}

	public boolean isVisible() {
		return visible.getAsBoolean();
	}

	void sanitize() {
		T value = get();
		set(value == null ? defaultValue() : value);
	}

	protected abstract T clean(T value);

	public static final class Toggle extends Setting<Boolean> {
		Toggle(String key, Function<ZocularConfig, Boolean> getter, BiConsumer<ZocularConfig, Boolean> setter) {
			super(key, getter, setter);
		}

		public void flip() {
			set(!get());
		}

		@Override
		protected Boolean clean(Boolean value) {
			return value;
		}
	}

	public static final class Slider extends Setting<Float> {
		private final float min;
		private final float max;
		private final float step;
		private final Function<Float, Component> format;

		Slider(String key, float min, float max, float step, Function<Float, Component> format,
				Function<ZocularConfig, Float> getter, BiConsumer<ZocularConfig, Float> setter) {
			super(key, getter, setter);
			this.min = min;
			this.max = max;
			this.step = step;
			this.format = format;
		}

		public float progress() {
			return (get() - min) / (max - min);
		}

		public void setProgress(double progress) {
			set((float) (min + Mth.clamp(progress, 0.0, 1.0) * (max - min)));
		}

		public void nudge(int steps) {
			set(get() + steps * step);
		}

		public Component display() {
			return format.apply(get());
		}

		@Override
		protected Float clean(Float value) {
			float snapped = min + Math.round((value - min) / step) * step;
			return Mth.clamp(snapped, min, max);
		}
	}

	public static final class Cycle<E extends Enum<E> & ZocularConfig.Labeled> extends Setting<E> {
		private final E[] values;

		Cycle(String key, E[] values, Function<ZocularConfig, E> getter, BiConsumer<ZocularConfig, E> setter) {
			super(key, getter, setter);
			this.values = values;
		}

		public void cycle(int direction) {
			int index = Math.floorMod(get().ordinal() + direction, values.length);
			set(values[index]);
		}

		public Component display() {
			return get().label();
		}

		@Override
		protected E clean(E value) {
			return value;
		}
	}
}
