package dev.bhored.zocular.camera;

import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.config.ZocularConfig.Reframe;
import dev.bhored.zocular.config.ZocularConfig.ShotStyle;
import dev.bhored.zocular.util.Motion;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Decides where the cinematic camera sits and what it looks at.
 *
 * <p>The automatic style works like a camera crew: it picks a shot (tracking alongside you, a low chase, leading
 * from the front, a fixed roadside camera you ride past, a high wide shot or a slow orbit), holds it for a while,
 * and then cuts to a different one. The other styles stick to a single kind of shot.
 */
final class Director {
	private enum Shot {
		SIDE, CHASE, FRONT, ROADSIDE, WIDE, ORBIT, TRIPOD
	}

	private static final float MOVING_SPEED = 1.2F;
	private static final float OCCLUSION_GRACE = 0.6F;
	private static final float GLIDE_SECONDS = 1.4F;
	private static final int ATTEMPTS = 14;

	private final RandomSource random = RandomSource.create();

	private Vec3 position = Vec3.ZERO;
	private float yaw;
	private float pitch;

	private Shot shot = Shot.SIDE;
	private float shotAge;
	private float shotLength;
	private int side = 1;
	private float distance;
	private float height;
	private float along;
	private float lateral;
	private float angle;
	private Vec3 anchor = Vec3.ZERO;

	private Vec3 lastSubject = Vec3.ZERO;
	private Vec3 motion = Vec3.ZERO;
	private float heading;
	private float reach = 1.0F;
	private float occludedFor;
	private boolean panning;

	private Vec3 glideFrom = Vec3.ZERO;
	private float glide = 1.0F;

	void start(Level level, LocalPlayer player, float partialTicks) {
		ZocularConfig.Cinematic config = ZocularConfig.get().cinematic;
		Vec3 subject = subject(player, partialTicks);
		lastSubject = subject;
		motion = player.getDeltaMovement().scale(20.0);
		heading = speed() > MOVING_SPEED ? travelYaw() : player.getYRot();
		side = random.nextBoolean() ? 1 : -1;
		glide = 1.0F;

		Shot first = switch (config.style) {
			case AUTO -> speed() > MOVING_SPEED ? pick(Shot.SIDE, Shot.CHASE) : pick(Shot.SIDE, Shot.FRONT);
			default -> shotFor(config.style);
		};
		cut(level, player, subject, config, first, false);
	}

	Pose update(Level level, LocalPlayer player, float dt, float partialTicks) {
		ZocularConfig.Cinematic config = ZocularConfig.get().cinematic;
		Vec3 subject = subject(player, partialTicks);
		if (subject.distanceToSqr(lastSubject) > 400.0) {
			// Teleported: start over around the new position.
			lastSubject = subject;
			motion = Vec3.ZERO;
			cut(level, player, subject, config, choose(config), false);
		}
		track(subject, dt);
		shotAge += dt;

		if (!fits(shot, config.style)) {
			cut(level, player, subject, config, choose(config), false);
		}

		Vec3 target = target(level, player, subject, config, dt);
		if (glide < 1.0F) {
			glide = Math.min(1.0F, glide + dt / GLIDE_SECONDS);
			position = glideFrom.lerp(target, Motion.easeInOut(glide));
		} else if (isFixed(shot)) {
			position = target;
		} else {
			position = position.lerp(target, Motion.follow(0.1 + config.followSmoothing * 0.6, dt));
		}

		aim(subject, dt, config);
		occludedFor = canSee(level, player, position, subject) ? 0.0F : occludedFor + dt;
		if (glide >= 1.0F && shouldCut(subject, config)) {
			cut(level, player, subject, config, choose(config), config.reframe == Reframe.GLIDE);
		}
		return new Pose(position, yaw, pitch, 0.0F);
	}

	/** Cuts straight to a different shot. */
	void nextShot(Level level, LocalPlayer player, float partialTicks) {
		ZocularConfig.Cinematic config = ZocularConfig.get().cinematic;
		cut(level, player, subject(player, partialTicks), config, choose(config), false);
	}

	private void track(Vec3 subject, float dt) {
		if (dt <= 0.0F) {
			return;
		}
		Vec3 velocity = subject.subtract(lastSubject).scale(1.0 / dt);
		lastSubject = subject;
		if (velocity.lengthSqr() > 3600.0) {
			return;
		}
		motion = motion.lerp(velocity, Motion.follow(0.35, dt));
		if (speed() > MOVING_SPEED) {
			heading = Mth.rotLerp((float) Motion.follow(0.8, dt), heading, travelYaw());
		}
	}

	private Vec3 target(Level level, LocalPlayer player, Vec3 subject, ZocularConfig.Cinematic config, float dt) {
		return switch (shot) {
			case SIDE -> guarded(level, player, subject, frame(subject, along, side * distance, height), dt);
			case CHASE -> guarded(level, player, subject, frame(subject, -distance, side * lateral, height), dt);
			case FRONT -> guarded(level, player, subject, frame(subject, distance, side * lateral, height), dt);
			case WIDE -> {
				angle += 2.5F * dt;
				yield guarded(level, player, subject, around(subject, angle, distance, height), dt);
			}
			case ORBIT -> {
				angle += config.orbitSpeed * dt;
				yield guarded(level, player, subject, around(subject, angle, distance, height), dt);
			}
			case ROADSIDE, TRIPOD -> anchor;
		};
	}

	/**
	 * Keeps the camera out of walls. It moves in quickly when something gets between it and the player, then
	 * eases back out slowly, so a ray that flickers across a block edge doesn't make the picture jump.
	 */
	private Vec3 guarded(Level level, LocalPlayer player, Vec3 subject, Vec3 wanted, float dt) {
		double full = wanted.distanceTo(subject);
		if (full < 1.0E-3) {
			return wanted;
		}
		float clear = (float) (pullIn(level, player, subject, wanted).distanceTo(subject) / full);
		reach = Mth.lerp((float) Motion.follow(clear < reach ? 0.06 : 0.7, dt), reach, clear);
		return subject.add(wanted.subtract(subject).scale(reach));
	}

	private void aim(Vec3 subject, float dt, ZocularConfig.Cinematic config) {
		Vec3 look = subject;
		if (shot == Shot.CHASE || shot == Shot.SIDE) {
			// Leave room ahead of the player, the way an operator frames someone walking or riding.
			look = subject.add(forward().scale(Mth.clamp(speed() * (shot == Shot.CHASE ? 0.5F : 0.3F), 0.0F, 3.0F)));
		}

		float targetYaw = Pose.yawTowards(position, look);
		float targetPitch = Pose.pitchTowards(position, look);
		if (shot == Shot.TRIPOD && glide >= 1.0F) {
			float yawOff = Mth.wrapDegrees(targetYaw - yaw);
			float pitchOff = targetPitch - pitch;
			float offCenter = Mth.sqrt(yawOff * yawOff + pitchOff * pitchOff);
			if (offCenter > config.reframeAngle) {
				panning = true;
			} else if (offCenter < 1.5F) {
				panning = false;
			}
			if (!panning) {
				return;
			}
		}

		float amount = (float) Motion.follow(isFixed(shot) ? 0.3 : 0.12 + config.followSmoothing * 0.3, dt);
		yaw = Mth.rotLerp(amount, yaw, targetYaw);
		pitch = Mth.lerp(amount, pitch, targetPitch);
	}

	private boolean shouldCut(Vec3 subject, ZocularConfig.Cinematic config) {
		if (occludedFor > OCCLUSION_GRACE) {
			return true;
		}
		double away = position.distanceTo(subject);
		if (away < 0.9 || away > Math.max(distance * 3.0, 14.0)) {
			return true;
		}
		if (shot == Shot.ROADSIDE && away > distance * 1.8 && motion.dot(subject.subtract(position)) > 0.0) {
			return true;
		}
		if (shot == Shot.TRIPOD && away > config.distance * 2.5) {
			return true;
		}
		return config.style == ShotStyle.AUTO && shotAge > shotLength;
	}

	private void cut(Level level, LocalPlayer player, Vec3 subject, ZocularConfig.Cinematic config, Shot next, boolean smooth) {
		// Repeating the same kind of shot (single-style modes) switches sides so the change is visible.
		int firstSide = next == shot ? -side : random.nextBoolean() ? 1 : -1;
		Vec3 start = null;
		for (int attempt = 0; attempt < ATTEMPTS && start == null; attempt++) {
			Shot candidate = attempt < ATTEMPTS / 2 ? next : choose(config);
			side = attempt == 0 ? firstSide : random.nextBoolean() ? 1 : -1;
			roll(candidate, subject, config, candidate == shot);
			start = placement(level, player, subject, candidate, 0.55);
			if (start != null) {
				next = candidate;
			}
		}
		if (start == null) {
			next = config.style == ShotStyle.AUTO ? Shot.CHASE : shotFor(config.style);
			roll(next, subject, config, false);
			start = placement(level, player, subject, next, 0.0);
		}

		boolean glideIn = smooth && shot != next;
		shot = next;
		shotAge = 0.0F;
		shotLength = config.shotLength * (0.75F + random.nextFloat() * 0.5F);
		occludedFor = 0.0F;
		panning = false;
		if (glideIn) {
			glideFrom = position;
			glide = 0.0F;
		} else {
			position = start;
			glide = 1.0F;
			yaw = Pose.yawTowards(position, subject);
			pitch = Pose.pitchTowards(position, subject);
		}
	}

	/** Picks the random details of a shot: how far away, how high, which angle. */
	private void roll(Shot candidate, Vec3 subject, ZocularConfig.Cinematic config, boolean repeat) {
		float base = config.distance;
		float lift = config.height;
		switch (candidate) {
			case SIDE -> {
				distance = base * range(0.75F, 1.05F);
				height = lift * range(0.4F, 1.2F);
				along = range(-1.5F, 1.5F);
			}
			case CHASE -> {
				distance = base * range(0.6F, 0.8F);
				height = lift * range(0.3F, 0.8F);
				lateral = range(0.6F, 1.4F);
			}
			case FRONT -> {
				distance = base * range(0.8F, 1.1F);
				height = lift * range(0.4F, 1.1F);
				lateral = range(1.0F, 2.5F);
			}
			case WIDE -> {
				distance = base * range(1.8F, 2.6F);
				height = lift * range(2.5F, 4.5F) + 3.0F;
				angle = random.nextFloat() * 360.0F;
			}
			case ORBIT -> {
				distance = base;
				height = lift;
				angle = repeat ? angle + range(90.0F, 270.0F) : heading + 180.0F + range(-60.0F, 60.0F);
			}
			case ROADSIDE -> {
				distance = base * range(0.6F, 0.9F);
				height = lift * range(0.6F, 1.5F);
				float ahead = Mth.clamp(speed() * 2.5F, 3.0F, 18.0F);
				anchor = frame(subject, ahead, side * distance, height);
			}
			case TRIPOD -> {
				distance = base * range(0.8F, 1.2F);
				height = lift * range(0.5F, 1.5F);
				angle = random.nextFloat() * 360.0F;
				anchor = around(subject, angle, distance, height);
			}
		}
	}

	/** Where the shot would put the camera right now, or null if the view from there is too blocked. */
	private Vec3 placement(Level level, LocalPlayer player, Vec3 subject, Shot candidate, double minimumReach) {
		Vec3 wanted = switch (candidate) {
			case SIDE -> frame(subject, along, side * distance, height);
			case CHASE -> frame(subject, -distance, side * lateral, height);
			case FRONT -> frame(subject, distance, side * lateral, height);
			case WIDE, ORBIT -> around(subject, angle, distance, height);
			case ROADSIDE, TRIPOD -> anchor;
		};
		Vec3 spot = pullIn(level, player, subject, wanted);
		double full = wanted.distanceTo(subject);
		double clear = full < 1.0E-3 ? 1.0 : spot.distanceTo(subject) / full;
		if (clear < minimumReach) {
			return null;
		}
		if (isFixed(candidate)) {
			// Fixed cameras can't slide in later, so they must have a clean view from where they stand.
			if (clear < 0.98 || !level.noCollision(AABB.ofSize(wanted, 0.8, 0.8, 0.8))) {
				return null;
			}
			spot = wanted;
		}
		if (minimumReach > 0.0 && clearance(level, player, spot, subject) < 0.5) {
			return null;
		}
		reach = (float) clear;
		return spot;
	}

	private Shot choose(ZocularConfig.Cinematic config) {
		if (config.style != ShotStyle.AUTO) {
			return shotFor(config.style);
		}
		Shot[] shots;
		int[] weights;
		if (speed() > MOVING_SPEED) {
			shots = new Shot[] {Shot.SIDE, Shot.CHASE, Shot.FRONT, Shot.ROADSIDE, Shot.WIDE};
			weights = new int[] {3, 2, 2, 3, 1};
		} else {
			shots = new Shot[] {Shot.SIDE, Shot.FRONT, Shot.WIDE, Shot.ORBIT, Shot.CHASE};
			weights = new int[] {3, 2, 2, 2, 1};
		}
		int total = 0;
		for (int i = 0; i < shots.length; i++) {
			total += shots[i] == shot ? 0 : weights[i];
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < shots.length; i++) {
			if (shots[i] == shot) {
				continue;
			}
			roll -= weights[i];
			if (roll < 0) {
				return shots[i];
			}
		}
		return Shot.SIDE;
	}

	private static Shot shotFor(ShotStyle style) {
		return switch (style) {
			case AUTO, SIDE -> Shot.SIDE;
			case TRIPOD -> Shot.TRIPOD;
			case CHASE -> Shot.CHASE;
			case ORBIT -> Shot.ORBIT;
		};
	}

	private static boolean fits(Shot shot, ShotStyle style) {
		return style == ShotStyle.AUTO ? shot != Shot.TRIPOD : shot == shotFor(style);
	}

	private static boolean isFixed(Shot shot) {
		return shot == Shot.ROADSIDE || shot == Shot.TRIPOD;
	}

	/** A point in the player's frame of travel: ahead, to the right, and up. */
	private Vec3 frame(Vec3 subject, float ahead, float right, float up) {
		float rad = heading * Mth.DEG_TO_RAD;
		double x = -Mth.sin(rad) * ahead - Mth.cos(rad) * right;
		double z = Mth.cos(rad) * ahead - Mth.sin(rad) * right;
		return subject.add(x, up, z);
	}

	private static Vec3 around(Vec3 subject, float angle, float distance, float height) {
		float rad = angle * Mth.DEG_TO_RAD;
		return subject.add(-Mth.sin(rad) * distance, height, Mth.cos(rad) * distance);
	}

	private Vec3 forward() {
		float rad = heading * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(rad), 0.0, Mth.cos(rad));
	}

	private float speed() {
		return (float) Math.sqrt(motion.x * motion.x + motion.z * motion.z);
	}

	private float travelYaw() {
		return (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F;
	}

	private Shot pick(Shot first, Shot second) {
		return random.nextBoolean() ? first : second;
	}

	private float range(float min, float max) {
		return min + random.nextFloat() * (max - min);
	}

	/**
	 * How open the view is around the line of sight, 0..1. A single clear ray can still thread past a tree trunk
	 * that fills half the frame, so this also checks rays offset to each side of it.
	 */
	private static double clearance(Level level, LocalPlayer player, Vec3 camera, Vec3 subject) {
		Vec3 view = subject.subtract(camera).normalize();
		Vec3 side = view.cross(new Vec3(0.0, 1.0, 0.0));
		if (side.lengthSqr() < 1.0E-4) {
			return 0.5;
		}
		side = side.normalize();
		Vec3 up = side.cross(view).normalize();
		Vec3[] offsets = {side.scale(1.2), side.scale(-1.2), up.scale(0.9), up.scale(-0.6)};
		int clear = 0;
		for (Vec3 offset : offsets) {
			if (canSee(level, player, camera, subject.add(offset))) {
				clear++;
			}
		}
		return clear / (double) offsets.length;
	}

	private static Vec3 pullIn(Level level, LocalPlayer player, Vec3 from, Vec3 to) {
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			return to;
		}
		Vec3 direction = to.subtract(from).normalize();
		return hit.getLocation().subtract(direction.scale(0.3));
	}

	private static boolean canSee(Level level, LocalPlayer player, Vec3 from, Vec3 to) {
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < 0.25;
	}

	private static Vec3 subject(LocalPlayer player, float partialTicks) {
		return player.getPosition(partialTicks).add(0.0, player.getBbHeight() * 0.75, 0.0);
	}
}
