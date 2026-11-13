package dev.bhored.zocular.camera;

import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.config.ZocularConfig.LeagueCamera;
import dev.bhored.zocular.util.Motion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The overhead (top-down) view, and League mode on top of it. The camera hangs above the player at a fixed angle
 * and the mouse cursor is free.
 *
 * <p>Nothing about interaction is faked: every frame the player is turned to face whatever is under the cursor,
 * so vanilla's own crosshair pick, reach limit and server checks decide what a click does. Movement keys are
 * turned to match the screen in {@code KeyboardInputMixin}.
 *
 * <p>League mode adds click-to-walk (the player is steered with ordinary movement input, never moved directly) and
 * three cameras: Follow, Free (pan by pushing the mouse to a screen edge) and Auto-turn (swings round behind the
 * way the player walks).
 */
public final class Overhead {
	private static final float TURN_STEP = 45.0F;
	private static final float MIN_DISTANCE = 6.0F;
	private static final float MAX_DISTANCE = 64.0F;
	private static final double CURSOR_RANGE = 160.0;
	private static final double EDGE = 0.02;
	private static final long STUCK_MS = 1500L;

	private float yaw;
	private float targetYaw;
	private float distance;
	private float shownDistance;
	private @Nullable Vec3 focus;
	private @Nullable Vec3 aimPoint;
	private @Nullable Entity aimEntity;

	private boolean league;
	private LeagueCamera camera = LeagueCamera.FOLLOW;
	private boolean recentering;

	private @Nullable Vec3 walkTarget;
	private @Nullable Entity walkEntity;
	private double stopAt;
	private double bestDistance;
	private long lastProgress;

	void start(LocalPlayer player, boolean leagueMode) {
		// Start looking the way the player was facing, snapped to the nearest turn step.
		targetYaw = Math.round(player.getYRot() / TURN_STEP) * TURN_STEP;
		yaw = targetYaw;
		distance = ZocularConfig.get().overhead.distance;
		shownDistance = distance;
		focus = null;
		aimPoint = null;
		aimEntity = null;
		setLeague(leagueMode);
	}

	void setLeague(boolean leagueMode) {
		league = leagueMode;
		camera = ZocularConfig.get().overhead.leagueCamera;
		recentering = false;
		clearWalk();
	}

	public boolean isLeague() {
		return league;
	}

	public LeagueCamera camera() {
		return camera;
	}

	/** Moves to the next League camera and remembers it for next time. */
	LeagueCamera cycleCamera() {
		camera = camera.next();
		ZocularConfig.get().overhead.leagueCamera = camera;
		recentering = true;
		return camera;
	}

	/** Brings a Free camera back over the player. */
	public void recenter() {
		recentering = true;
	}

	void turn(int steps) {
		targetYaw += steps * TURN_STEP;
	}

	/** Moves the camera up or down; scrolling up brings it closer. */
	public void scroll(double amount) {
		distance = Mth.clamp((float) (distance * Math.pow(1.15, -amount)), MIN_DISTANCE, MAX_DISTANCE);
	}

	public float yaw() {
		return yaw;
	}

	public @Nullable Vec3 aimPoint() {
		return aimPoint;
	}

	Pose update(Minecraft minecraft, LocalPlayer player, float dt, float partialTicks) {
		ZocularConfig.Overhead config = ZocularConfig.get().overhead;
		if (league && camera == LeagueCamera.AUTO_TURN) {
			autoTurn(player, dt);
		}
		yaw += (targetYaw - yaw) * (float) Motion.follow(0.12, dt);
		shownDistance += (distance - shownDistance) * (float) Motion.follow(0.1, dt);

		Vec3 target = player.getPosition(partialTicks).add(0.0, 1.0, 0.0);
		if (focus == null) {
			focus = target;
		} else if (league && camera == LeagueCamera.FREE && !recentering) {
			focus = focus.add(edgePan(minecraft, dt));
		} else {
			focus = focus.lerp(target, Motion.follow(recentering ? 0.12 : 0.06, dt));
			if (recentering && focus.distanceTo(target) < 0.25) {
				recentering = false;
			}
		}

		float pitch = config.angle;
		Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
		Vec3 position = focus.subtract(forward.scale(shownDistance));
		// Don't put the camera inside a hill or a roof; pull it in towards the focus instead.
		HitResult wall = player.level().clip(new ClipContext(focus, position, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			position = wall.getLocation().add(forward.scale(0.3));
		}

		aim(minecraft, player, position, forward, pitch);
		return new Pose(position, yaw, pitch, 0.0F);
	}

	/** Free camera: pushing the mouse against a screen edge slides the camera that way, faster when it is higher. */
	private Vec3 edgePan(Minecraft minecraft, float dt) {
		if (minecraft.gui.screen() != null || !minecraft.isWindowActive()) {
			return Vec3.ZERO;
		}
		double width = minecraft.getWindow().getScreenWidth();
		double height = minecraft.getWindow().getScreenHeight();
		double margin = Math.max(6.0, Math.min(width, height) * EDGE);
		double x = minecraft.mouseHandler.xpos();
		double y = minecraft.mouseHandler.ypos();
		int sideways = x <= margin ? -1 : x >= width - margin ? 1 : 0;
		int ahead = y <= margin ? 1 : y >= height - margin ? -1 : 0;
		if (sideways == 0 && ahead == 0) {
			return Vec3.ZERO;
		}
		float yawRad = yaw * Mth.DEG_TO_RAD;
		Vec3 up = new Vec3(-Mth.sin(yawRad), 0.0, Mth.cos(yawRad));
		Vec3 right = new Vec3(-Mth.cos(yawRad), 0.0, -Mth.sin(yawRad));
		double speed = shownDistance * 1.2 * dt;
		return up.scale(ahead).add(right.scale(sideways)).normalize().scale(speed);
	}

	/** Auto-turn: while the player walks forwards or sideways on screen, swing the camera round behind them. */
	private void autoTurn(LocalPlayer player, float dt) {
		Vec3 motion = player.getDeltaMovement();
		double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		if (speed < 0.05) {
			return;
		}
		float heading = (float) (Mth.atan2(-motion.x, motion.z) * Mth.RAD_TO_DEG);
		float offset = Mth.wrapDegrees(heading - targetYaw);
		// Walking towards the camera would spin it round; only follow headings ahead of or beside it.
		if (Math.abs(offset) > 110.0F) {
			return;
		}
		targetYaw += offset * (float) Motion.follow(0.9, dt);
	}

	/** Turns the player towards whatever sits under the mouse cursor. */
	private void aim(Minecraft minecraft, LocalPlayer player, Vec3 cameraPosition, Vec3 forward, float pitch) {
		if (minecraft.gui.screen() != null) {
			return;
		}
		Vec3 ray = cursorRay(minecraft, forward);
		Vec3 end = cameraPosition.add(ray.scale(CURSOR_RANGE));

		Vec3 point;
		BlockHitResult block = player.level().clip(new ClipContext(cameraPosition, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		Vec3 blockEnd = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, cameraPosition, blockEnd,
			new AABB(cameraPosition, blockEnd).inflate(1.0), Overhead::targetable, cameraPosition.distanceToSqr(blockEnd));
		aimEntity = entity == null ? null : entity.getEntity();
		if (entity != null) {
			point = entity.getLocation();
		} else if (block.getType() != HitResult.Type.MISS) {
			// Nudge into the block so the player's own look ray lands on this face and not the edge next to it.
			point = block.getLocation().add(ray.scale(0.05));
		} else if (ray.y < -1.0E-3) {
			// Over the void or the sky: aim at the height of the player's feet.
			double t = (player.getY() - cameraPosition.y) / ray.y;
			point = cameraPosition.add(ray.scale(t));
		} else {
			return;
		}
		aimPoint = point;

		Vec3 eye = player.getEyePosition();
		Vec3 look = point.subtract(eye);
		double flat = Math.sqrt(look.x * look.x + look.z * look.z);
		if (flat < 1.0E-4 && Math.abs(look.y) < 1.0E-4) {
			return;
		}
		float wantedYaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
		float wantedPitch = (float) -(Mth.atan2(look.y, flat) * Mth.RAD_TO_DEG);
		float newYaw = player.getYRot() + Mth.wrapDegrees(wantedYaw - player.getYRot());
		float newPitch = Mth.clamp(wantedPitch, -90.0F, 90.0F);
		player.setYRot(newYaw);
		player.setXRot(newPitch);
		player.yRotO = newYaw;
		player.xRotO = newPitch;
		player.setYHeadRot(newYaw);
	}

	/** The direction from the camera through the mouse cursor. */
	private static Vec3 cursorRay(Minecraft minecraft, Vec3 forward) {
		double width = minecraft.getWindow().getScreenWidth();
		double height = minecraft.getWindow().getScreenHeight();
		if (width <= 0 || height <= 0) {
			return forward;
		}
		double x = 2.0 * minecraft.mouseHandler.xpos() / width - 1.0;
		double y = 1.0 - 2.0 * minecraft.mouseHandler.ypos() / height;
		double tan = Math.tan(Math.toRadians(minecraft.gameRenderer.mainCamera().getFov()) / 2.0);
		float yawRad = (float) Mth.atan2(-forward.x, forward.z);
		Vec3 right = new Vec3(-Mth.cos(yawRad), 0.0, -Mth.sin(yawRad));
		Vec3 up = right.cross(forward);
		return forward.add(right.scale(x * tan * width / height)).add(up.scale(y * tan)).normalize();
	}

	private static boolean targetable(Entity entity) {
		return !entity.isSpectator() && entity.isPickable();
	}

	// --- League mode: click to walk -------------------------------------------------------------------------

	/** Right-click: walk to the mob or the spot under the cursor. */
	public void walkToCursor() {
		if (aimEntity != null) {
			walkTo(aimEntity.position(), aimEntity, 1.6);
		} else if (aimPoint != null) {
			walkTo(aimPoint, null, 0.35);
		}
	}

	/** Left-click on something out of reach: walk until it is in reach. */
	public void approachCursor(double reach) {
		if (aimEntity != null) {
			walkTo(aimEntity.position(), aimEntity, Math.max(1.0, reach - 0.8));
		} else if (aimPoint != null) {
			walkTo(aimPoint, null, Math.max(1.0, reach - 0.8));
		}
	}

	private void walkTo(Vec3 point, @Nullable Entity entity, double stopDistance) {
		walkTarget = point;
		walkEntity = entity;
		stopAt = stopDistance;
		bestDistance = Double.MAX_VALUE;
		lastProgress = System.currentTimeMillis();
	}

	public void clearWalk() {
		walkTarget = null;
		walkEntity = null;
	}

	/** Where the player is walking to, for the marker on screen. */
	public @Nullable Vec3 walkMarker() {
		if (walkEntity != null) {
			return walkEntity.isRemoved() ? null : walkEntity.position();
		}
		return walkTarget;
	}

	/**
	 * The movement input that steers the player towards the walk target, in the player's own frame (x = left,
	 * y = forward), or null when there is nowhere to go. Stops on arrival, or when the player has made no progress
	 * for a moment (a wall, a cliff, a fence).
	 */
	public @Nullable Vec2 walkInput(LocalPlayer player) {
		Vec3 target = walkMarker();
		if (target == null) {
			clearWalk();
			return null;
		}
		double dx = target.x - player.getX();
		double dz = target.z - player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance <= stopAt) {
			clearWalk();
			return null;
		}
		long now = System.currentTimeMillis();
		if (distance < bestDistance - 0.05) {
			bestDistance = distance;
			lastProgress = now;
		} else if (now - lastProgress > STUCK_MS) {
			clearWalk();
			return null;
		}
		// The world direction turned into the player's frame: the inverse of Entity#getInputVector's rotation.
		float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
		float cos = Mth.cos(yawRad);
		float sin = Mth.sin(yawRad);
		double nx = dx / distance;
		double nz = dz / distance;
		return new Vec2((float) (nx * cos + nz * sin), (float) (nz * cos - nx * sin));
	}
}
