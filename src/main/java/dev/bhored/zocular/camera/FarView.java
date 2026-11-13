package dev.bhored.zocular.camera;

import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.hud.Notices;
import dev.bhored.zocular.util.Motion;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The far third-person view: in F5 the Zoom key pulls the camera back from the player instead of narrowing the
 * field of view, and the scroll wheel moves it further out or back in. Distances animate in log space, so going
 * from 8 to 16 blocks feels the same as 64 to 128.
 */
public final class FarView {
	public static final float MIN_DISTANCE = 4.0F;
	private static final float SCROLL_STEP = 1.15F;
	private static final float PULL_SECONDS = 0.45F;

	private static boolean pulled;
	private static float distance = 24.0F;
	private static float pull;
	private static double shownLog = Math.log(distance);
	private static float extra;

	private FarView() {
	}

	/** True when the Zoom key should drive the far view rather than the normal zoom. */
	public static boolean applies(Minecraft minecraft) {
		return ZocularConfig.get().thirdPerson.farView
			&& !CameraControl.get().isActive()
			&& !minecraft.options.getCameraType().isFirstPerson();
	}

	public static void toggle() {
		pulled = !pulled;
		if (pulled) {
			showDistance();
		}
	}

	/** Called once per rendered frame. */
	public static void update(Minecraft minecraft, float dt) {
		if (pulled && (minecraft.player == null || !applies(minecraft))) {
			pulled = false;
		}
		float max = ZocularConfig.get().thirdPerson.maxDistance;
		distance = Mth.clamp(distance, MIN_DISTANCE, max);

		pull = Mth.approach(pull, pulled ? 1.0F : 0.0F, dt / PULL_SECONDS);
		double target = Math.log(distance);
		shownLog += (target - shownLog) * Motion.follow(0.08, dt);
		if (Math.abs(target - shownLog) < 1.0E-4) {
			shownLog = target;
		}
	}

	/** Scroll handling; returns true when the scroll moved the camera. */
	public static boolean scroll(double amount) {
		if (!pulled) {
			return false;
		}
		// Scrolling up brings the camera in, like zooming in.
		float max = ZocularConfig.get().thirdPerson.maxDistance;
		distance = Mth.clamp((float) (distance * Math.pow(SCROLL_STEP, -amount)), MIN_DISTANCE, max);
		showDistance();
		return true;
	}

	/** The camera distance to use, given the normal third-person distance. */
	public static float apply(float normal) {
		if (pull <= 0.0F) {
			extra = 0.0F;
			return normal;
		}
		double from = Math.log(Math.max(normal, 0.01F));
		double to = Math.max(shownLog, from);
		float pulledBack = (float) Math.exp(Mth.lerp(Motion.easeInOut(pull), from, to));
		extra = pulledBack - normal;
		return pulledBack;
	}

	/** How much further than normal the camera currently sits, in blocks. */
	public static float extraDistance() {
		return extra;
	}

	public static boolean isPulled() {
		return pulled;
	}

	public static float distance() {
		return distance;
	}

	public static void reset() {
		pulled = false;
		pull = 0.0F;
		extra = 0.0F;
	}

	private static void showDistance() {
		Notices.show(Component.translatable("zocular.notice.far_view", String.format(Locale.ROOT, "%.0f", distance)));
	}
}
