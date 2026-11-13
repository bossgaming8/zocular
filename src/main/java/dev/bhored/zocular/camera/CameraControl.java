package dev.bhored.zocular.camera;

import dev.bhored.zocular.Keybinds;
import dev.bhored.zocular.Zoom;
import dev.bhored.zocular.compat.IrisShaders;
import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.config.ZocularConfig.PanelMode;
import dev.bhored.zocular.config.ZocularConfig.ShaderMode;
import dev.bhored.zocular.hud.Notices;
import dev.bhored.zocular.hud.Splash;
import dev.bhored.zocular.util.Keys;
import dev.bhored.zocular.util.Motion;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Owns the camera while freecam or the cinematic camera is running.
 *
 * <p>Both modes switch the game into third-person internally, which hides the hand and crosshair and renders the
 * player model, and then replace the camera position every frame. The player's own perspective, HUD visibility
 * and Iris shader state are put back exactly as they were when the mode ends.
 */
public final class CameraControl {
	public enum Mode {
		NONE, FREECAM, CINEMATIC, OVERHEAD
	}

	private static final CameraControl INSTANCE = new CameraControl();

	private final Freecam freecam = new Freecam();
	private final Director director = new Director();
	private final KeyframePath path = new KeyframePath();
	private final Overhead overhead = new Overhead();

	private Mode mode = Mode.NONE;
	private @Nullable LocalPlayer owner;
	private CameraType previousCameraType = CameraType.FIRST_PERSON;
	private boolean hidHud;
	private @Nullable Boolean previousShaders;
	private int lastHurtTime;

	private Pose entryPose;
	private float blend;
	private boolean leaving;
	private boolean finishPending;

	private boolean playAlong;
	private boolean holdingStartClick;
	private boolean exitPending;
	private @Nullable Pose lastPathPose;

	private float roll;
	private boolean levellingRoll;
	private long lastFrame;

	private CameraControl() {
	}

	public static CameraControl get() {
		return INSTANCE;
	}

	public Mode mode() {
		return mode;
	}

	public Freecam freecam() {
		return freecam;
	}

	public KeyframePath path() {
		return path;
	}

	public Overhead overhead() {
		return overhead;
	}

	public void tick(Minecraft minecraft) {
		while (Keybinds.FREECAM.consumeClick()) {
			toggleFreecam(minecraft);
		}
		while (Keybinds.CINEMATIC.consumeClick()) {
			toggleCinematic(minecraft);
		}
		while (Keybinds.OVERHEAD.consumeClick()) {
			toggleOverhead(minecraft);
		}
		// The roll keys turn the overhead camera a step at a time; elsewhere they are read while held.
		while (Keybinds.ROLL_LEFT.consumeClick()) {
			if (mode == Mode.OVERHEAD) {
				overhead.turn(-1);
			}
		}
		while (Keybinds.ROLL_RIGHT.consumeClick()) {
			if (mode == Mode.OVERHEAD) {
				overhead.turn(1);
			}
		}
		while (Keybinds.NEXT_SHOT.consumeClick()) {
			if (mode == Mode.CINEMATIC && !leaving && owner != null) {
				director.nextShot(minecraft.level, owner, 1.0F);
			}
		}
		while (Keybinds.ROLL_RESET.consumeClick()) {
			levellingRoll = true;
		}
		while (Keybinds.FREECAM_PANEL.consumeClick()) {
			if (mode == Mode.FREECAM) {
				ZocularConfig.Hud hud = ZocularConfig.get().hud;
				hud.freecamPanel = hud.freecamPanel == PanelMode.FULL ? PanelMode.COMPACT : PanelMode.FULL;
			}
		}
		while (Keybinds.SHOULDER.consumeClick()) {
			ZocularConfig.ThirdPerson thirdPerson = ZocularConfig.get().thirdPerson;
			thirdPerson.shoulder = thirdPerson.shoulder.next();
			Notices.show(Component.translatable("zocular.notice.shoulder", thirdPerson.shoulder.label()));
		}

		if (mode == Mode.NONE) {
			return;
		}
		if (!minecraft.isPaused()) {
			path.tick();
		}
		LocalPlayer player = minecraft.player;
		if (player == null || player != owner || !player.isAlive()) {
			stopAll(minecraft);
			return;
		}
		if (mode == Mode.FREECAM && !playingAlong() && ZocularConfig.get().freecam.exitWhenHurt && player.hurtTime > lastHurtTime) {
			exitFreecam(minecraft);
			Notices.show(Component.translatable("zocular.notice.freecam_hurt"));
		}
		lastHurtTime = player.hurtTime;
		if (mode == Mode.OVERHEAD && minecraft.gui.screen() == null && minecraft.mouseHandler.isMouseGrabbed()) {
			minecraft.mouseHandler.releaseMouse();
		}
	}

	/** Runs at the start of every camera update, before vanilla positions the camera. */
	public void beforeCameraUpdate(Minecraft minecraft) {
		if (finishPending) {
			finishPending = false;
			endCinematic(minecraft);
		}
		if (exitPending) {
			exitPending = false;
			if (mode == Mode.FREECAM) {
				exitFreecam(minecraft);
			}
		}
	}

	/**
	 * Runs after vanilla has placed the camera for this frame. Returns the pose to use instead, or null to keep
	 * the vanilla one.
	 */
	public @Nullable Pose frame(Minecraft minecraft, Camera camera, float partialTicks) {
		long now = System.nanoTime();
		float dt = lastFrame == 0L ? 0.0F : Math.min((now - lastFrame) / 1.0E9F, 0.1F);
		lastFrame = now;

		Zoom.update(minecraft, dt);
		if (mode == Mode.NONE || owner == null || minecraft.level == null) {
			return null;
		}

		if (mode == Mode.OVERHEAD) {
			return overhead.update(minecraft, owner, dt, partialTicks);
		}
		updateRoll(dt);
		return mode == Mode.FREECAM ? freecamPose(minecraft, dt, partialTicks) : cinematicPose(minecraft, camera, dt, partialTicks);
	}

	private Pose freecamPose(Minecraft minecraft, float dt, float partialTicks) {
		if (path.isPlaying()) {
			Pose pose = path.current(partialTicks);
			if (pose != null) {
				lastPathPose = pose;
				return pose;
			}
			if (playAlong && lastPathPose != null) {
				// The player kept playing through the shot, so hand the camera back to them rather than
				// leaving them frozen in freecam. The switch happens before the next frame is placed.
				exitPending = true;
				return lastPathPose;
			}
			endPlayback(minecraft);
		}
		freecam.move(minecraft, dt);
		return new Pose(freecam.position(), freecam.yaw(), freecam.pitch(), roll);
	}

	private Pose cinematicPose(Minecraft minecraft, Camera camera, float dt, float partialTicks) {
		Pose shot = director.update(minecraft.level, owner, dt, partialTicks).withRoll(roll);
		float seconds = ZocularConfig.get().cinematic.transitionMs / 1000.0F;
		float step = seconds <= 0.0F ? 1.0F : dt / seconds;

		if (!leaving) {
			blend = Math.min(1.0F, blend + step);
			return entryPose.lerp(shot, Motion.easeInOut(blend));
		}

		blend = Math.max(0.0F, blend - step);
		Pose home = homePose(camera, partialTicks);
		if (blend <= 0.0F) {
			finishPending = true;
		}
		return home.lerp(shot, Motion.easeInOut(blend));
	}

	private Pose homePose(Camera camera, float partialTicks) {
		if (previousCameraType.isFirstPerson()) {
			return new Pose(owner.getEyePosition(partialTicks), owner.getViewYRot(partialTicks), owner.getViewXRot(partialTicks), 0.0F);
		}
		return new Pose(camera.position(), camera.yRot(), camera.xRot(), 0.0F);
	}

	private void updateRoll(float dt) {
		ZocularConfig.Roll config = ZocularConfig.get().roll;
		int input = (Keys.isHeld(Keybinds.ROLL_RIGHT) ? 1 : 0) - (Keys.isHeld(Keybinds.ROLL_LEFT) ? 1 : 0);
		if (input != 0 && Minecraft.getInstance().gui.screen() == null) {
			levellingRoll = false;
			roll = Mth.clamp(roll + input * config.speed * dt, -config.max, config.max);
		} else if (levellingRoll) {
			roll = Mth.approach(roll, 0.0F, config.speed * 2.0F * dt);
			levellingRoll = roll != 0.0F;
		}
	}

	public void toggleFreecam(Minecraft minecraft) {
		if (mode == Mode.FREECAM) {
			exitFreecam(minecraft);
			return;
		}
		if (minecraft.player == null) {
			return;
		}
		if (mode == Mode.CINEMATIC) {
			endCinematic(minecraft);
		}
		endOverhead(minecraft);

		Camera camera = minecraft.gameRenderer.mainCamera();
		takeOver(minecraft);
		freecam.start(camera.position(), camera.yRot(), camera.xRot());
		mode = Mode.FREECAM;
		Notices.show(Component.translatable("zocular.notice.freecam_on"));
	}

	private void exitFreecam(Minecraft minecraft) {
		path.stop();
		playAlong = false;
		holdingStartClick = false;
		exitPending = false;
		lastPathPose = null;
		release(minecraft);
		mode = Mode.NONE;
	}

	public void toggleCinematic(Minecraft minecraft) {
		if (mode == Mode.CINEMATIC) {
			leaving = !leaving;
			return;
		}
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		if (mode == Mode.FREECAM) {
			exitFreecam(minecraft);
		}
		endOverhead(minecraft);

		Camera camera = minecraft.gameRenderer.mainCamera();
		entryPose = new Pose(camera.position(), camera.yRot(), camera.xRot(), 0.0F);
		takeOver(minecraft);

		ZocularConfig.Cinematic config = ZocularConfig.get().cinematic;
		if (config.hideHud) {
			hideHud(minecraft);
		}
		if (config.shaders != ShaderMode.UNCHANGED) {
			previousShaders = IrisShaders.enabled();
			if (previousShaders != null) {
				IrisShaders.setEnabled(config.shaders == ShaderMode.ON);
			}
		}

		director.start(minecraft.level, minecraft.player, 1.0F);
		blend = 0.0F;
		leaving = false;
		finishPending = false;
		mode = Mode.CINEMATIC;
	}

	private void endCinematic(Minecraft minecraft) {
		if (mode != Mode.CINEMATIC) {
			return;
		}
		if (previousShaders != null) {
			IrisShaders.setEnabled(previousShaders);
			previousShaders = null;
		}
		release(minecraft);
		mode = Mode.NONE;
		leaving = false;
		blend = 0.0F;
	}

	public void toggleOverhead(Minecraft minecraft) {
		if (mode == Mode.OVERHEAD) {
			endOverhead(minecraft);
			return;
		}
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		if (mode == Mode.FREECAM) {
			exitFreecam(minecraft);
		} else if (mode == Mode.CINEMATIC) {
			endCinematic(minecraft);
		}

		takeOver(minecraft);
		overhead.start(minecraft.player);
		mode = Mode.OVERHEAD;
		minecraft.mouseHandler.releaseMouse();
		if (ZocularConfig.get().overhead.splash) {
			Splash.show(Component.translatable("zocular.splash.overhead"),
				Component.translatable("zocular.splash.overhead.hint", Keybinds.OVERHEAD.getTranslatedKeyMessage()));
		} else {
			Notices.show(Component.translatable("zocular.notice.overhead_on"));
		}
	}

	private void endOverhead(Minecraft minecraft) {
		if (mode != Mode.OVERHEAD) {
			return;
		}
		release(minecraft);
		mode = Mode.NONE;
		Splash.hide();
		if (minecraft.gui.screen() == null) {
			minecraft.mouseHandler.grabMouse();
		}
	}

	private void takeOver(Minecraft minecraft) {
		owner = minecraft.player;
		lastHurtTime = owner.hurtTime;
		previousCameraType = minecraft.options.getCameraType();
		minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		roll = 0.0F;
		levellingRoll = false;
	}

	private void release(Minecraft minecraft) {
		minecraft.options.setCameraType(previousCameraType);
		restoreHud(minecraft);
		owner = null;
		roll = 0.0F;
	}

	public void stopAll(Minecraft minecraft) {
		if (mode == Mode.FREECAM) {
			exitFreecam(minecraft);
		} else if (mode == Mode.CINEMATIC) {
			endCinematic(minecraft);
		} else if (mode == Mode.OVERHEAD) {
			endOverhead(minecraft);
		}
		finishPending = false;
		Zoom.reset();
	}

	/** Clears everything tied to the current world. */
	public void onDisconnect(Minecraft minecraft) {
		stopAll(minecraft);
		path.clear();
	}

	/**
	 * In freecam the attack, use and pick-block buttons edit the camera path instead of reaching the world.
	 */
	public void handleMouseActions(Minecraft minecraft) {
		if (mode == Mode.NONE) {
			return;
		}
		while (minecraft.options.keyTogglePerspective.consumeClick()) {
			// The perspective is ours while a camera mode runs.
		}
		if (holdingStartClick && !minecraft.options.keyUse.isDown()) {
			holdingStartClick = false;
		}
		if (mode != Mode.FREECAM || playingAlong()) {
			return;
		}

		while (minecraft.options.keyAttack.consumeClick()) {
			if (path.add(new Pose(freecam.position(), freecam.yaw(), freecam.pitch(), roll))) {
				Notices.show(Component.translatable("zocular.notice.keyframe_added", path.size()));
			}
		}
		while (minecraft.options.keyUse.consumeClick()) {
			togglePathPlayback(minecraft);
		}
		while (minecraft.options.keyPickItem.consumeClick()) {
			if (Keys.isHeld(minecraft.options.keyShift)) {
				path.clear();
				Notices.show(Component.translatable("zocular.notice.path_cleared"));
			} else if (path.removeLast()) {
				Notices.show(Component.translatable("zocular.notice.keyframe_removed", path.size()));
			}
		}
	}

	public void togglePathPlayback(Minecraft minecraft) {
		if (path.isPlaying()) {
			endPlayback(minecraft);
			return;
		}
		if (!path.play()) {
			Notices.show(Component.translatable("zocular.notice.path_too_short"));
			return;
		}
		playAlong = ZocularConfig.get().path.playAlong;
		// The Use press that started the shot must not also reach the world once the player has control.
		holdingStartClick = playAlong;
		lastPathPose = null;
		if (ZocularConfig.get().path.hideHud) {
			hideHud(minecraft);
		}
	}

	private void endPlayback(Minecraft minecraft) {
		path.stop();
		playAlong = false;
		holdingStartClick = false;
		restoreHud(minecraft);
	}

	public void adjustFreecamSpeed(double steps) {
		ZocularConfig.Freecam config = ZocularConfig.get().freecam;
		config.speed = Mth.clamp((float) (config.speed * Math.pow(1.15, steps)), 1.0F, 100.0F);
		Notices.show(Component.translatable("zocular.notice.speed", String.format(Locale.ROOT, "%.1f", config.speed)));
	}

	private void hideHud(Minecraft minecraft) {
		if (!hidHud && !minecraft.gui.hud.isHidden()) {
			minecraft.gui.hud.toggle();
			hidHud = true;
		}
	}

	private void restoreHud(Minecraft minecraft) {
		if (hidHud) {
			if (minecraft.gui.hud.isHidden()) {
				minecraft.gui.hud.toggle();
			}
			hidHud = false;
		}
	}

	/** True while freecam or the cinematic camera is placing the camera. */
	public boolean isActive() {
		return mode != Mode.NONE;
	}

	/**
	 * The field of view to render with. Cinematic shots use their own lens, and neither mode lets sprinting or
	 * flying change the FOV, which would make a detached camera pump in and out.
	 */
	public float fov(float vanilla) {
		return switch (mode) {
			case NONE -> vanilla;
			case FREECAM -> Minecraft.getInstance().options.fov().get();
			case CINEMATIC -> Mth.lerp(Motion.easeInOut(blend), vanilla, ZocularConfig.get().cinematic.fov);
			case OVERHEAD -> ZocularConfig.get().overhead.fov;
		};
	}

	/** Mouse look goes to the free camera instead of the player. */
	public boolean capturesLook() {
		return mode == Mode.FREECAM && !playingAlong();
	}

	public void turn(double dx, double dy) {
		if (!path.isPlaying()) {
			freecam.turn(dx, dy);
		}
	}

	public float lookSensitivity() {
		return mode == Mode.CINEMATIC && !leaving ? ZocularConfig.get().cinematic.lookSensitivity : 1.0F;
	}

	/** True while a camera path plays and the player has been handed back their own controls. */
	public boolean playingAlong() {
		return mode == Mode.FREECAM && playAlong && path.isPlaying();
	}

	/** True while the overhead view owns the mouse cursor, so the game must not grab it. */
	public boolean keepsCursor() {
		return mode == Mode.OVERHEAD;
	}

	/**
	 * How far to turn the movement keys, in radians, so forward means up the screen rather than the way the
	 * player is facing.
	 */
	public float overheadMoveTurn(LocalPlayer player) {
		return (overhead.yaw() - player.getYRot()) * Mth.DEG_TO_RAD;
	}

	public boolean freezesPlayer() {
		return mode == Mode.FREECAM && !playingAlong();
	}

	public boolean blocksInteraction() {
		return mode == Mode.FREECAM && (!playingAlong() || holdingStartClick);
	}

	/** True when the camera sits inside the player, where rendering them would only block the view. */
	public boolean isInsideOwner(Vec3 cameraPosition) {
		return owner != null && owner.getBoundingBox().inflate(0.3).contains(cameraPosition);
	}

	public boolean showsOwnNameTag(Entity entity) {
		return mode == Mode.FREECAM && entity == owner && ZocularConfig.get().freecam.showNameTag
			&& !Minecraft.getInstance().gui.hud.isHidden() && !entity.isInvisible();
	}

	/** How much of the cinematic letterbox to show, 0..1. */
	public float letterboxAmount() {
		if (mode == Mode.CINEMATIC) {
			return Motion.easeInOut(blend);
		}
		return mode == Mode.FREECAM && path.isPlaying() && ZocularConfig.get().path.letterbox ? 1.0F : 0.0F;
	}

	public float vignetteAmount() {
		return mode == Mode.CINEMATIC ? Motion.easeInOut(blend) : 0.0F;
	}

	public boolean isLeaving() {
		return leaving;
	}
}
