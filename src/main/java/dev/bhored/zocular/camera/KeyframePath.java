package dev.bhored.zocular.camera;

import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.util.Motion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A camera path through recorded keyframes. Positions follow a centripetal Catmull-Rom spline, which never loops
 * or overshoots between unevenly spaced points; rotations use a uniform spline over unwrapped angles.
 */
public final class KeyframePath {
	private static final int SAMPLES_PER_SEGMENT = 48;

	private final List<Pose> keyframes = new ArrayList<>();
	private boolean playing;
	private int ticks;
	private float fraction;

	private float[] yaws = new float[0];
	private float[] pitches = new float[0];
	private float[] rolls = new float[0];
	private double[] distances = new double[0];

	public int size() {
		return keyframes.size();
	}

	public boolean isPlaying() {
		return playing;
	}

	/** How far along the path the camera is, 0..1. */
	public float fraction() {
		return fraction;
	}

	/** Where keyframe {@code index} sits along the path, 0..1, matching {@link #fraction()}. */
	public float keyframeFraction(int index) {
		int segments = keyframes.size() - 1;
		if (segments <= 0) {
			return 0.0F;
		}
		if (ZocularConfig.get().path.constantSpeed && distances.length > 0) {
			return (float) (distances[index * SAMPLES_PER_SEGMENT] / distances[distances.length - 1]);
		}
		return index / (float) segments;
	}

	public boolean add(Pose pose) {
		if (playing) {
			return false;
		}
		keyframes.add(pose);
		return true;
	}

	public boolean removeLast() {
		if (playing || keyframes.isEmpty()) {
			return false;
		}
		keyframes.removeLast();
		return true;
	}

	public void clear() {
		playing = false;
		keyframes.clear();
	}

	boolean play() {
		if (keyframes.size() < 2) {
			return false;
		}
		prepare();
		playing = true;
		ticks = 0;
		fraction = 0.0F;
		return true;
	}

	void stop() {
		playing = false;
	}

	/** Playback runs on game ticks, so it pauses with the game and every frame lands exactly on the path. */
	void tick() {
		if (playing) {
			ticks++;
		}
	}

	/** The pose for this frame. Returns null once a path that doesn't loop has finished. */
	Pose current(float partialTicks) {
		ZocularConfig.CameraPath config = ZocularConfig.get().path;
		float seconds = (ticks + partialTicks) / 20.0F;
		float t = seconds / config.duration;
		if (t >= 1.0F) {
			if (!config.loop) {
				playing = false;
				return null;
			}
			t %= 1.0F;
		}
		if (config.ease) {
			t = Motion.easeInOutSine(t);
		}
		fraction = t;
		return sample(t, config.constantSpeed);
	}

	private void prepare() {
		int count = keyframes.size();
		yaws = new float[count];
		pitches = new float[count];
		rolls = new float[count];
		for (int i = 0; i < count; i++) {
			Pose pose = keyframes.get(i);
			yaws[i] = i == 0 ? pose.yaw() : yaws[i - 1] + Mth.wrapDegrees(pose.yaw() - yaws[i - 1]);
			pitches[i] = pose.pitch();
			rolls[i] = pose.roll();
		}

		int segments = keyframes.size() - 1;
		distances = new double[segments * SAMPLES_PER_SEGMENT + 1];
		Vec3 previous = keyframes.getFirst().position();
		for (int i = 1; i < distances.length; i++) {
			Vec3 point = position(i / (double) SAMPLES_PER_SEGMENT);
			distances[i] = distances[i - 1] + point.distanceTo(previous);
			previous = point;
		}
	}

	private Pose sample(float t, boolean constantSpeed) {
		int segments = keyframes.size() - 1;
		double u = constantSpeed ? parameterAt(t * distances[distances.length - 1]) : t * segments;
		int i = Math.min((int) u, segments - 1);
		float local = (float) (u - i);
		return new Pose(
			position(u),
			spline(yaws, i, local),
			Mth.clamp(spline(pitches, i, local), -90.0F, 90.0F),
			spline(rolls, i, local)
		);
	}

	private double parameterAt(double distance) {
		int low = 0;
		int high = distances.length - 1;
		while (high - low > 1) {
			int mid = (low + high) >>> 1;
			if (distances[mid] < distance) {
				low = mid;
			} else {
				high = mid;
			}
		}
		double span = distances[high] - distances[low];
		double local = span <= 1.0E-9 ? 0.0 : (distance - distances[low]) / span;
		return (low + local) / SAMPLES_PER_SEGMENT;
	}

	private Vec3 position(double u) {
		int segments = keyframes.size() - 1;
		int i = Mth.clamp((int) u, 0, segments - 1);
		double t = u - i;
		Vec3 p1 = keyframes.get(i).position();
		Vec3 p2 = keyframes.get(i + 1).position();
		Vec3 p0 = i > 0 ? keyframes.get(i - 1).position() : p1.scale(2.0).subtract(p2);
		Vec3 p3 = i + 2 < keyframes.size() ? keyframes.get(i + 2).position() : p2.scale(2.0).subtract(p1);
		return centripetal(p0, p1, p2, p3, t);
	}

	private static float spline(float[] values, int i, float t) {
		float p1 = values[i];
		float p2 = values[i + 1];
		float p0 = i > 0 ? values[i - 1] : 2.0F * p1 - p2;
		float p3 = i + 2 < values.length ? values[i + 2] : 2.0F * p2 - p1;
		float t2 = t * t;
		float t3 = t2 * t;
		return 0.5F * (2.0F * p1 + (p2 - p0) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2 + (3.0F * p1 - p0 - 3.0F * p2 + p3) * t3);
	}

	private static Vec3 centripetal(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
		double t0 = 0.0;
		double t1 = t0 + knot(p0, p1);
		double t2 = t1 + knot(p1, p2);
		double t3 = t2 + knot(p2, p3);
		double tt = t1 + (t2 - t1) * t;

		Vec3 a1 = blend(p0, p1, t0, t1, tt);
		Vec3 a2 = blend(p1, p2, t1, t2, tt);
		Vec3 a3 = blend(p2, p3, t2, t3, tt);
		Vec3 b1 = blend(a1, a2, t0, t2, tt);
		Vec3 b2 = blend(a2, a3, t1, t3, tt);
		return blend(b1, b2, t1, t2, tt);
	}

	private static double knot(Vec3 a, Vec3 b) {
		return Math.max(Math.sqrt(a.distanceTo(b)), 1.0E-4);
	}

	private static Vec3 blend(Vec3 a, Vec3 b, double ta, double tb, double t) {
		double weight = (t - ta) / (tb - ta);
		return a.lerp(b, weight);
	}
}
