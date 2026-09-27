package dev.bhored.zocular.camera;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record Pose(Vec3 position, float yaw, float pitch, float roll) {
	public Pose lerp(Pose to, float t) {
		return new Pose(
			position.lerp(to.position, t),
			Mth.rotLerp(t, yaw, to.yaw),
			Mth.lerp(t, pitch, to.pitch),
			Mth.lerp(t, roll, to.roll)
		);
	}

	public Pose withRoll(float roll) {
		return new Pose(position, yaw, pitch, roll);
	}

	public static float yawTowards(Vec3 from, Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
	}

	public static float pitchTowards(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		return (float) -(Mth.atan2(to.y - from.y, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
	}
}
