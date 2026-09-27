package dev.bhored.zocular.camera;

import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.util.Keys;
import dev.bhored.zocular.util.Motion;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class Freecam {
	private static final double SIZE = 0.4;

	private Vec3 position = Vec3.ZERO;
	private Vec3 velocity = Vec3.ZERO;
	private float yaw;
	private float pitch;

	void start(Vec3 position, float yaw, float pitch) {
		this.position = position;
		this.velocity = Vec3.ZERO;
		this.yaw = yaw;
		this.pitch = pitch;
	}

	void turn(double dx, double dy) {
		yaw += (float) dx * 0.15F;
		pitch = Mth.clamp(pitch + (float) dy * 0.15F, -90.0F, 90.0F);
	}

	void move(Minecraft minecraft, float dt) {
		ZocularConfig.Freecam config = ZocularConfig.get().freecam;
		Vec3 target = Vec3.ZERO;

		if (minecraft.gui.screen() == null) {
			Options options = minecraft.options;
			float forward = axis(options.keyUp, options.keyDown);
			float strafe = axis(options.keyLeft, options.keyRight);
			float lift = axis(options.keyJump, options.keyShift);
			Vec3 wish = heading(forward, strafe, lift, config.flyWhereLooking);
			if (wish.lengthSqr() > 1.0E-6) {
				double speed = config.speed * (Keys.isHeld(options.keySprint) ? config.boost : 1.0);
				target = wish.normalize().scale(speed);
			}
		}

		velocity = velocity.lerp(target, Motion.follow(config.smoothing * 0.35, dt));
		Vec3 step = velocity.scale(dt);
		if (!config.noclip && minecraft.level != null && step.lengthSqr() > 0.0) {
			AABB box = AABB.ofSize(position, SIZE, SIZE, SIZE);
			step = Entity.collideBoundingBox(CollisionContext.empty(), step, box, minecraft.level, List.of());
		}
		position = position.add(step);
	}

	private Vec3 heading(float forward, float strafe, float lift, boolean followPitch) {
		float yawRad = yaw * Mth.DEG_TO_RAD;
		Vec3 ahead = followPitch
			? Vec3.directionFromRotation(pitch, yaw)
			: new Vec3(-Mth.sin(yawRad), 0.0, Mth.cos(yawRad));
		Vec3 left = new Vec3(Mth.cos(yawRad), 0.0, Mth.sin(yawRad));
		return ahead.scale(forward).add(left.scale(strafe)).add(0.0, lift, 0.0);
	}

	private static float axis(KeyMapping positive, KeyMapping negative) {
		return (Keys.isHeld(positive) ? 1.0F : 0.0F) - (Keys.isHeld(negative) ? 1.0F : 0.0F);
	}

	public Vec3 position() {
		return position;
	}

	public float yaw() {
		return yaw;
	}

	public float pitch() {
		return pitch;
	}

	/** Current speed in blocks per second, for the overlay. */
	public double speed() {
		return velocity.length();
	}
}
