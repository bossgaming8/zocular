package dev.bhored.zocular.util;

import net.minecraft.util.Mth;

public final class Motion {
	private Motion() {
	}

	/**
	 * How far to move toward a target this frame so the gap shrinks exponentially, independent of frame rate.
	 * {@code seconds} is the time constant; zero or less means "snap".
	 */
	public static double follow(double seconds, double dt) {
		return seconds <= 0.0 ? 1.0 : 1.0 - Math.exp(-dt / seconds);
	}

	public static float easeInOut(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0) / 2.0F;
	}

	public static float easeInOutSine(float t) {
		return 0.5F - 0.5F * Mth.cos(Mth.clamp(t, 0.0F, 1.0F) * Mth.PI);
	}

	public static float easeOut(float t) {
		t = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
		return 1.0F - t * t * t;
	}
}
