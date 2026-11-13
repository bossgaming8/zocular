package dev.bhored.zocular.camera;

import dev.bhored.zocular.config.ZocularConfig;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The overhead (top-down) view. The camera hangs above the player at a fixed angle and the mouse cursor is free.
 *
 * <p>Nothing about interaction is faked: every frame the player is turned to face whatever is under the cursor,
 * so vanilla's own crosshair pick, reach limit and server checks decide what a click does. Movement keys are
 * turned to match the screen in {@code KeyboardInputMixin}.
 */
public final class Overhead {
	private static final float TURN_STEP = 45.0F;
	private static final float MIN_DISTANCE = 6.0F;
	private static final float MAX_DISTANCE = 64.0F;
	private static final double CURSOR_RANGE = 160.0;

	private float yaw;
	private float targetYaw;
	private float distance;
	private float shownDistance;
	private @Nullable Vec3 focus;
	private @Nullable Vec3 aimPoint;

	void start(LocalPlayer player) {
		// Start looking the way the player was facing, snapped to the nearest turn step.
		targetYaw = Math.round(player.getYRot() / TURN_STEP) * TURN_STEP;
		yaw = targetYaw;
		distance = ZocularConfig.get().overhead.distance;
		shownDistance = distance;
		focus = null;
		aimPoint = null;
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
		yaw += (targetYaw - yaw) * (float) Motion.follow(0.12, dt);
		shownDistance += (distance - shownDistance) * (float) Motion.follow(0.1, dt);

		Vec3 target = player.getPosition(partialTicks).add(0.0, 1.0, 0.0);
		focus = focus == null ? target : focus.lerp(target, Motion.follow(0.06, dt));

		float pitch = config.angle;
		Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
		Vec3 position = focus.subtract(forward.scale(shownDistance));
		// Don't put the camera inside a hill or a roof; pull it in towards the player instead.
		HitResult wall = player.level().clip(new ClipContext(focus, position, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			position = wall.getLocation().add(forward.scale(0.3));
		}

		aim(minecraft, player, position, forward, pitch);
		return new Pose(position, yaw, pitch, 0.0F);
	}

	/** Turns the player towards whatever sits under the mouse cursor. */
	private void aim(Minecraft minecraft, LocalPlayer player, Vec3 cameraPosition, Vec3 forward, float pitch) {
		if (minecraft.gui.screen() != null) {
			return;
		}
		Vec3 ray = cursorRay(minecraft, forward, pitch);
		Vec3 end = cameraPosition.add(ray.scale(CURSOR_RANGE));

		Vec3 point;
		BlockHitResult block = player.level().clip(new ClipContext(cameraPosition, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		Vec3 blockEnd = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, cameraPosition, blockEnd,
			new AABB(cameraPosition, blockEnd).inflate(1.0), Overhead::targetable, cameraPosition.distanceToSqr(blockEnd));
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
	private static Vec3 cursorRay(Minecraft minecraft, Vec3 forward, float pitch) {
		double width = minecraft.getWindow().getScreenWidth();
		double height = minecraft.getWindow().getScreenHeight();
		if (width <= 0 || height <= 0) {
			return forward;
		}
		double x = 2.0 * minecraft.mouseHandler.xpos() / width - 1.0;
		double y = 1.0 - 2.0 * minecraft.mouseHandler.ypos() / height;
		double tan = Math.tan(Math.toRadians(minecraft.gameRenderer.mainCamera().getFov()) / 2.0);
		Vec3 right = new Vec3(-Mth.cos(yawRadians(forward)), 0.0, -Mth.sin(yawRadians(forward)));
		Vec3 up = right.cross(forward);
		return forward.add(right.scale(x * tan * width / height)).add(up.scale(y * tan)).normalize();
	}

	private static float yawRadians(Vec3 forward) {
		return (float) Mth.atan2(-forward.x, forward.z);
	}

	private static boolean targetable(Entity entity) {
		return !entity.isSpectator() && entity.isPickable();
	}
}
