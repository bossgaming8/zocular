package dev.bhored.zocular.camera;

import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.config.ZocularConfig.Reframe;
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
 * <p>Tripod shots hold a fixed spot and only pan once the player drifts toward the edge of frame, then move to a
 * new spot when the player gets too far away or is hidden behind terrain. Chase and orbit shots follow the player
 * continuously.
 */
final class Director {
	private static final int CANDIDATES = 28;
	private static final float GLIDE_SECONDS = 1.6F;
	private static final float PAN_SECONDS = 0.45F;
	private static final float OCCLUSION_GRACE = 0.6F;

	private final RandomSource random = RandomSource.create();

	private Vec3 position = Vec3.ZERO;
	private float yaw;
	private float pitch;
	private boolean panning;
	private float occludedFor;

	private Vec3 glideFrom = Vec3.ZERO;
	private Vec3 glideTo = Vec3.ZERO;
	private float glide = 1.0F;

	private float orbitAngle;
	private float chaseYaw;
	private int chaseSide = 1;
	private float lastShotAngle = Float.NaN;

	void start(Level level, LocalPlayer player, float partialTicks) {
		ZocularConfig.Cinematic config = ZocularConfig.get().cinematic;
		Vec3 subject = subject(player, partialTicks);
		chaseYaw = player.getYRot();
		orbitAngle = player.getYRot() + 140.0F;
		glide = 1.0F;
		panning = false;
		occludedFor = 0.0F;

		position = switch (config.style) {
			case TRIPOD -> findSpot(level, player, subject, config);
			case CHASE -> pullIn(level, player, subject, chaseTarget(subject, config));
			case ORBIT -> pullIn(level, player, subject, orbitTarget(subject, config));
		};
		aim(subject, 1.0F);
	}

	Pose update(Level level, LocalPlayer player, float dt, float partialTicks) {
		ZocularConfig.Cinematic config = ZocularConfig.get().cinematic;
		Vec3 subject = subject(player, partialTicks);

		switch (config.style) {
			case TRIPOD -> tripod(level, player, subject, dt, config);
			case CHASE -> {
				chaseYaw = Mth.rotLerp((float) Motion.follow(0.25 + config.followSmoothing * 1.25, dt), chaseYaw, player.getYRot());
				follow(level, player, subject, chaseTarget(subject, config), dt, config);
			}
			case ORBIT -> {
				orbitAngle += config.orbitSpeed * dt;
				follow(level, player, subject, orbitTarget(subject, config), dt, config);
			}
		}
		return new Pose(position, yaw, pitch, 0.0F);
	}

	/** Cuts straight to a fresh angle. */
	void nextShot(Level level, LocalPlayer player, float partialTicks) {
		ZocularConfig.Cinematic config = ZocularConfig.get().cinematic;
		Vec3 subject = subject(player, partialTicks);
		switch (config.style) {
			case TRIPOD -> position = findSpot(level, player, subject, config);
			case CHASE -> {
				chaseSide = -chaseSide;
				position = pullIn(level, player, subject, chaseTarget(subject, config));
			}
			case ORBIT -> {
				orbitAngle += 90.0F + random.nextFloat() * 180.0F;
				position = pullIn(level, player, subject, orbitTarget(subject, config));
			}
		}
		glide = 1.0F;
		panning = false;
		occludedFor = 0.0F;
		aim(subject, 1.0F);
	}

	private void tripod(Level level, LocalPlayer player, Vec3 subject, float dt, ZocularConfig.Cinematic config) {
		if (glide < 1.0F) {
			glide = Math.min(1.0F, glide + dt / GLIDE_SECONDS);
			position = glideFrom.lerp(glideTo, Motion.easeInOut(glide));
			aim(subject, (float) Motion.follow(0.12, dt));
			return;
		}

		double distance = position.distanceTo(subject);
		occludedFor = canSee(level, player, position, subject) ? 0.0F : occludedFor + dt;
		if (distance > config.distance * 2.5F || distance < 1.5 || occludedFor > OCCLUSION_GRACE) {
			reframe(level, player, subject, config);
			return;
		}

		float yawOff = Mth.wrapDegrees(Pose.yawTowards(position, subject) - yaw);
		float pitchOff = Pose.pitchTowards(position, subject) - pitch;
		float offCenter = Mth.sqrt(yawOff * yawOff + pitchOff * pitchOff);
		if (offCenter > config.reframeAngle) {
			panning = true;
		}
		if (panning) {
			aim(subject, (float) Motion.follow(PAN_SECONDS, dt));
			if (offCenter < 1.5F) {
				panning = false;
			}
		}
	}

	private void reframe(Level level, LocalPlayer player, Vec3 subject, ZocularConfig.Cinematic config) {
		Vec3 spot = findSpot(level, player, subject, config);
		occludedFor = 0.0F;
		panning = false;
		if (config.reframe == Reframe.CUT) {
			position = spot;
			aim(subject, 1.0F);
		} else {
			glideFrom = position;
			glideTo = spot;
			glide = 0.0F;
		}
	}

	private void follow(Level level, LocalPlayer player, Vec3 subject, Vec3 target, float dt, ZocularConfig.Cinematic config) {
		Vec3 desired = pullIn(level, player, subject, target);
		position = position.lerp(desired, Motion.follow(0.12 + config.followSmoothing * 0.9, dt));
		aim(subject, (float) Motion.follow(0.06 + config.followSmoothing * 0.3, dt));
	}

	private Vec3 chaseTarget(Vec3 subject, ZocularConfig.Cinematic config) {
		float angle = (chaseYaw + 180.0F + chaseSide * 22.0F) * Mth.DEG_TO_RAD;
		return subject.add(-Mth.sin(angle) * config.distance, config.height, Mth.cos(angle) * config.distance);
	}

	private Vec3 orbitTarget(Vec3 subject, ZocularConfig.Cinematic config) {
		float angle = orbitAngle * Mth.DEG_TO_RAD;
		return subject.add(-Mth.sin(angle) * config.distance, config.height, Mth.cos(angle) * config.distance);
	}

	/**
	 * Samples spots around the player and keeps the one with the clearest view, preferring angles that show the
	 * player's front and differ from the previous shot.
	 */
	private Vec3 findSpot(Level level, LocalPlayer player, Vec3 subject, ZocularConfig.Cinematic config) {
		Vec3 best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		float bestAngle = 0.0F;

		for (int i = 0; i < CANDIDATES; i++) {
			float angle = random.nextFloat() * 360.0F;
			float distance = config.distance * (0.75F + random.nextFloat() * 0.5F);
			float height = config.height * (0.4F + random.nextFloat());
			float rad = angle * Mth.DEG_TO_RAD;
			Vec3 wanted = subject.add(-Mth.sin(rad) * distance, height, Mth.cos(rad) * distance);
			Vec3 spot = pullIn(level, player, subject, wanted);
			double reach = spot.distanceTo(subject) / wanted.distanceTo(subject);
			if (reach < 0.45 || !level.noCollision(AABB.ofSize(spot, 1.2, 1.2, 1.2))) {
				continue;
			}

			double facing = Math.cos(Mth.wrapDegrees(angle - player.getYRot()) * Mth.DEG_TO_RAD);
			double variety = Float.isNaN(lastShotAngle) ? 1.0 : Math.abs(Mth.wrapDegrees(angle - lastShotAngle)) / 180.0;
			double score = reach * 1.2 + clearance(level, player, spot, subject) * 1.4 + facing * 0.35 + variety * 0.5
				+ random.nextFloat() * 0.15;
			if (score > bestScore) {
				bestScore = score;
				best = spot;
				bestAngle = angle;
			}
		}

		if (best == null) {
			chaseYaw = player.getYRot();
			return pullIn(level, player, subject, chaseTarget(subject, config));
		}
		lastShotAngle = bestAngle;
		return best;
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

	private void aim(Vec3 subject, float amount) {
		yaw = Mth.rotLerp(amount, yaw, Pose.yawTowards(position, subject));
		pitch = Mth.lerp(amount, pitch, Pose.pitchTowards(position, subject));
	}

	private static Vec3 subject(LocalPlayer player, float partialTicks) {
		return player.getPosition(partialTicks).add(0.0, player.getBbHeight() * 0.75, 0.0);
	}
}
