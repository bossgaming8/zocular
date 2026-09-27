package dev.bhored.zocular;

import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.config.ZocularConfig.ZoomMode;
import dev.bhored.zocular.util.Motion;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Zoom state. The magnification is animated in log space so zooming from 2x to 4x feels the same as 16x to 32x.
 */
public final class Zoom {
	private static final float MIN_LEVEL = 1.1F;
	private static final float SCROLL_STEP = 1.2F;
	private static final long INDICATOR_LINGER_MS = 900L;

	private static boolean engaged;
	private static boolean toggled;
	private static float level = 1.0F;
	private static float remembered;
	private static double shownLog;
	private static long lastChange;

	private Zoom() {
	}

	/** Called once per rendered frame, before the camera works out its field of view. */
	public static void update(Minecraft minecraft, float dt) {
		ZocularConfig.Zoom config = ZocularConfig.get().zoom;
		boolean wanted = readKey(config.mode) && config.enabled && minecraft.player != null;

		if (wanted != engaged) {
			engaged = wanted;
			if (engaged) {
				level = config.rememberLevel && remembered > 0.0F ? remembered : config.startLevel;
			} else if (config.rememberLevel) {
				remembered = level;
			}
			lastChange = System.currentTimeMillis();
		}

		while (Keybinds.ZOOM_IN.consumeClick()) {
			adjust(1.0);
		}
		while (Keybinds.ZOOM_OUT.consumeClick()) {
			adjust(-1.0);
		}

		double target = engaged ? Math.log(level) : 0.0;
		shownLog += (target - shownLog) * Motion.follow(config.animationMs / 3000.0, dt);
		if (Math.abs(target - shownLog) < 1.0E-4) {
			shownLog = target;
		}
	}

	private static boolean readKey(ZoomMode mode) {
		if (mode == ZoomMode.TOGGLE) {
			while (Keybinds.ZOOM.consumeClick()) {
				toggled = !toggled;
			}
			return toggled;
		}

		toggled = false;
		while (Keybinds.ZOOM.consumeClick()) {
			// Hold mode only cares whether the key is down; drop queued presses.
		}
		return Keybinds.ZOOM.isDown();
	}

	/** Scroll handling; returns true when the scroll was used for zooming. */
	public static boolean scroll(double amount) {
		if (!engaged || !ZocularConfig.get().zoom.scrollToZoom) {
			return false;
		}
		adjust(amount);
		return true;
	}

	private static void adjust(double steps) {
		if (!engaged) {
			return;
		}
		float max = ZocularConfig.get().zoom.maxLevel;
		level = Mth.clamp((float) (level * Math.pow(SCROLL_STEP, steps)), MIN_LEVEL, max);
		lastChange = System.currentTimeMillis();
	}

	public static void reset() {
		engaged = false;
		toggled = false;
		shownLog = 0.0;
	}

	public static boolean isEngaged() {
		return engaged;
	}

	/** True while any zoom is visible on screen, including the ease back out. */
	public static boolean isVisible() {
		return shownLog > 1.0E-3;
	}

	public static float magnification() {
		return (float) Math.exp(shownLog);
	}

	public static float fovScale() {
		return (float) Math.exp(-shownLog);
	}

	/** Mouse speed multiplier that keeps aim feeling the same on screen at any zoom. */
	public static double sensitivityScale() {
		if (!isVisible() || !ZocularConfig.get().zoom.matchMouseSpeed) {
			return 1.0;
		}
		double half = Math.toRadians(Minecraft.getInstance().options.fov().get()) / 2.0;
		return Math.tan(half * fovScale()) / Math.tan(half);
	}

	public static float lookSmoothing() {
		if (!isVisible()) {
			return 0.0F;
		}
		float progress = (float) Mth.clamp(shownLog / Math.log(Math.max(level, MIN_LEVEL)), 0.0, 1.0);
		return ZocularConfig.get().zoom.lookSmoothing * progress;
	}

	public static boolean hidesHand() {
		return isVisible() && ZocularConfig.get().zoom.hideHand;
	}

	/** 0..1 visibility for the on-screen indicator. */
	public static float indicatorAlpha() {
		if (engaged) {
			return 1.0F;
		}
		long since = System.currentTimeMillis() - lastChange;
		return since >= INDICATOR_LINGER_MS ? 0.0F : 1.0F - since / (float) INDICATOR_LINGER_MS;
	}

	public static float targetLevel() {
		return level;
	}
}
