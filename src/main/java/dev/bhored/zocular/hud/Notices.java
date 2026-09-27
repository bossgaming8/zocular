package dev.bhored.zocular.hud;

import dev.bhored.zocular.config.ZocularConfig;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Short status messages such as "Keyframe 3 added". */
public final class Notices {
	private static final long VISIBLE_MS = 1600L;
	private static final long FADE_MS = 350L;

	private static @Nullable Component message;
	private static long shownAt;

	private Notices() {
	}

	public static void show(Component text) {
		if (ZocularConfig.get().hud.notices) {
			message = text;
			shownAt = System.currentTimeMillis();
		}
	}

	static @Nullable Component current() {
		return alpha() > 0.0F ? message : null;
	}

	static float alpha() {
		if (message == null) {
			return 0.0F;
		}
		long age = System.currentTimeMillis() - shownAt;
		if (age >= VISIBLE_MS + FADE_MS) {
			return 0.0F;
		}
		return age <= VISIBLE_MS ? 1.0F : 1.0F - (age - VISIBLE_MS) / (float) FADE_MS;
	}
}
