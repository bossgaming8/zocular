package dev.bhored.zocular.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bhored.zocular.Keybinds;
import dev.bhored.zocular.Zoom;
import dev.bhored.zocular.camera.CameraControl;
import dev.bhored.zocular.camera.FarView;
import dev.bhored.zocular.config.Settings;
import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.gui.HudEditorScreen;
import dev.bhored.zocular.gui.SettingsScreen;
import dev.bhored.zocular.hud.Splash;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Drives the real client: clicks through every settings page, then checks zoom, freecam, camera paths and the
 * cinematic camera in a flat test world. Screenshots land in build/run/clientGameTest/screenshots.
 */
public final class ZocularClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (Boolean.getBoolean("zocular.showcase")) {
			return;
		}
		TestInput input = context.getInput();
		input.resizeWindow(1920, 1080);
		context.waitTicks(2);
		checkDefaultKeys(context);

		settingsPages(context, "title");
		compactLayout(context, input);

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("fill ~5 ~ ~5 ~9 ~6 ~9 minecraft:oak_log");
			world.getServer().runCommand("fill ~-8 ~ ~3 ~-4 ~3 ~7 minecraft:stone_bricks");
			world.getServer().runCommand("fill ~-14 ~ ~-10 ~-11 ~4 ~-7 minecraft:oak_planks");
			world.getServer().runCommand("fill ~8 ~-1 ~-12 ~12 ~-1 ~-8 minecraft:water");
			world.getServer().runCommand("summon minecraft:sheep ~3 ~ ~-4 {NoAI:1b}");
			world.getServer().runCommand("summon minecraft:cow ~-3 ~ ~-6 {NoAI:1b}");
			world.getServer().runCommand("tp @p ~ ~ ~ 45 10");
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("world");

			zoom(context, input);
			farView(context, input);
			freecamAndPath(context, input);
			cinematic(context, input);
			shoulder(context, input);
			overhead(context, input, world);
			hudEditor(context, input);
			settingsPages(context, "ingame");
		}
	}

	private static void settingsPages(ClientGameTestContext context, String prefix) {
		context.setScreen(() -> new SettingsScreen(null));
		context.waitTicks(8);
		for (int page = 0; page < Settings.PAGES.size(); page++) {
			clickSidebar(context, page);
			context.waitTicks(6);
			context.takeScreenshot(prefix + "-settings-" + Settings.PAGES.get(page).id());
		}
		clickSidebar(context, 0);
		context.waitTicks(4);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(2);
	}

	private static void compactLayout(ClientGameTestContext context, TestInput input) {
		input.resizeWindow(1024, 768);
		context.setScreen(() -> new SettingsScreen(null));
		context.waitTicks(6);
		clickSidebar(context, 1);
		context.waitTicks(6);
		context.takeScreenshot("compact-settings");
		input.pressKey(InputConstants.KEY_ESCAPE);
		input.resizeWindow(1920, 1080);
		context.waitTicks(2);
	}

	private static void clickSidebar(ClientGameTestContext context, int page) {
		double[] point = context.computeOnClient(minecraft -> {
			int width = minecraft.getWindow().getGuiScaledWidth();
			int height = minecraft.getWindow().getGuiScaledHeight();
			int panelWidth = Math.min(width - 16, 470);
			int panelHeight = Math.min(height - 16, 300);
			int panelX = (width - panelWidth) / 2;
			int panelY = (height - panelHeight) / 2;
			boolean compact = panelWidth < 380;
			double guiX = panelX + 20;
			double guiY = panelY + (compact ? 36 : 46) + page * 22 + 10;
			double scale = minecraft.getWindow().getScreenWidth() / (double) width;
			return new double[] {guiX * scale, guiY * scale};
		});
		context.getInput().setCursorPos(point[0], point[1]);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static void zoom(ClientGameTestContext context, TestInput input) {
		input.holdKey(Keybinds.ZOOM);
		context.waitTicks(15);
		float level = context.computeOnClient(minecraft -> Zoom.magnification());
		check(Math.abs(level - ZocularConfig.get().zoom.startLevel) < 0.05F, "zoom should settle at the starting level, got " + level);
		context.takeScreenshot("zoom");

		input.scroll(3.0);
		context.waitTicks(15);
		float scrolled = context.computeOnClient(minecraft -> Zoom.magnification());
		check(scrolled > level * 1.5F, "scrolling should zoom in further, got " + scrolled);
		context.takeScreenshot("zoom-scrolled");

		input.releaseKey(Keybinds.ZOOM);
		context.waitTicks(20);
		check(!context.computeOnClient(minecraft -> Zoom.isVisible()), "zoom should ease back out after releasing the key");
	}

	/** In third person the Zoom key swings the camera far back, and scrolling takes it further out. */
	private static void farView(ClientGameTestContext context, TestInput input) {
		int renderDistance = context.computeOnClient(minecraft -> minecraft.options.renderDistance().get());
		context.runOnClient(minecraft -> {
			minecraft.options.renderDistance().set(12);
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			minecraft.player.setXRot(40.0F);
		});
		context.waitTicks(5);
		double normal = cameraGap(context);
		check(normal < 6.0, "vanilla third person should sit close, got " + normal);

		input.pressKey(Keybinds.ZOOM);
		context.waitTicks(30);
		check(FarView.isPulled(), "the Zoom key should pull the camera back in third person");
		check(!Zoom.isEngaged(), "the normal zoom must not engage in third person");
		double pulled = cameraGap(context);
		check(pulled > 18.0, "the camera should be far back, got " + pulled);
		context.takeScreenshot("far-view");

		input.scroll(-12.0);
		context.runOnClient(minecraft -> minecraft.player.setXRot(65.0F));
		context.waitTicks(40);
		double far = cameraGap(context);
		check(far > 60.0, "scrolling should take the camera much further out, got " + far);
		check(FarView.extraDistance() > 50.0F, "the fog should be pushed out with the camera");
		context.takeScreenshot("far-view-max");

		input.pressKey(Keybinds.ZOOM);
		context.waitTicks(30);
		check(!FarView.isPulled(), "pressing Zoom again should bring the camera back");
		double back = cameraGap(context);
		check(back < 6.0, "the camera should be back at the normal distance, got " + back);

		context.runOnClient(minecraft -> {
			minecraft.options.renderDistance().set(renderDistance);
			minecraft.options.setCameraType(CameraType.FIRST_PERSON);
			minecraft.player.setXRot(10.0F);
		});
		context.waitTicks(3);
	}

	private static double cameraGap(ClientGameTestContext context) {
		return context.computeOnClient(minecraft ->
			minecraft.gameRenderer.mainCamera().position().distanceTo(minecraft.player.getEyePosition()));
	}

	private static void freecamAndPath(ClientGameTestContext context, TestInput input) {
		Vec3 playerBefore = context.computeOnClient(minecraft -> minecraft.player.position());
		input.pressKey(Keybinds.FREECAM);
		context.waitTicks(2);
		check(CameraControl.get().mode() == CameraControl.Mode.FREECAM, "freecam should be active");

		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		input.holdKeyFor(options -> options.keyDown, 12);
		input.holdKeyFor(options -> options.keyJump, 8);
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		input.holdKeyFor(options -> options.keyLeft, 14);
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
		context.takeScreenshot("freecam");

		input.pressKey(Keybinds.FREECAM_PANEL);
		context.waitTicks(2);
		check(ZocularConfig.get().hud.freecamPanel == ZocularConfig.PanelMode.COMPACT, "the panel key should collapse the freecam panel");
		context.takeScreenshot("freecam-compact");
		input.pressKey(Keybinds.FREECAM_PANEL);
		context.waitTicks(2);
		check(ZocularConfig.get().hud.freecamPanel == ZocularConfig.PanelMode.FULL, "the panel key should expand it again");

		Vec3 playerAfter = context.computeOnClient(minecraft -> minecraft.player.position());
		check(playerAfter.distanceTo(playerBefore) < 0.01, "the player must stay put while freecam moves");
		check(CameraControl.get().path().size() == 3, "three keyframes should be recorded");
		Vec3 camera = context.computeOnClient(minecraft -> minecraft.gameRenderer.mainCamera().position());
		check(camera.distanceTo(playerBefore) > 2.0, "the camera should have flown away from the player");

		input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitTicks(40);
		check(CameraControl.get().path().isPlaying(), "the path should be playing");
		context.takeScreenshot("path-playing");

		input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		input.pressKey(Keybinds.FREECAM);
		context.waitTicks(3);
		check(CameraControl.get().mode() == CameraControl.Mode.NONE, "freecam should be off");
		check(context.computeOnClient(minecraft -> minecraft.options.getCameraType()) == CameraType.FIRST_PERSON, "first person should be restored");
		check(!context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden()), "the HUD should be visible again");

		pathPlayAlong(context, input);
	}

	/** With Play while it runs on, the player walks during the shot and gets the camera back when it ends. */
	private static void pathPlayAlong(ClientGameTestContext context, TestInput input) {
		ZocularConfig.CameraPath config = ZocularConfig.get().path;
		boolean previousPlayAlong = config.playAlong;
		float previousDuration = config.duration;
		config.playAlong = true;
		config.duration = 3.0F;
		try {
			input.pressKey(Keybinds.FREECAM);
			context.waitTicks(2);
			input.holdKeyFor(options -> options.keyJump, 10);
			input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			input.holdKeyFor(options -> options.keyRight, 10);
			check(CameraControl.get().path().size() >= 2, "the path should have keyframes to play");

			Vec3 before = context.computeOnClient(minecraft -> minecraft.player.position());
			input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(2);
			check(CameraControl.get().playingAlong(), "the player should have control while the path plays");
			check(!CameraControl.get().capturesLook(), "the mouse should turn the player again");

			input.holdKeyFor(options -> options.keyUp, 20);
			Vec3 after = context.computeOnClient(minecraft -> minecraft.player.position());
			check(after.distanceTo(before) > 1.0, "the player should walk during the shot, moved " + after.distanceTo(before));
			check(CameraControl.get().mode() == CameraControl.Mode.FREECAM, "the shot should still be running");
			double gap = context.computeOnClient(minecraft ->
				minecraft.gameRenderer.mainCamera().position().distanceTo(minecraft.player.getEyePosition()));
			check(gap > 0.5, "the camera should stay on the path, not on the player, gap " + gap);
			context.takeScreenshot("path-play-along");

			context.waitTicks(50);
			check(CameraControl.get().mode() == CameraControl.Mode.NONE, "the camera should come back to the player when the shot ends");
			check(context.computeOnClient(minecraft -> minecraft.options.getCameraType()) == CameraType.FIRST_PERSON, "first person should be restored");
			check(!context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden()), "the HUD should be visible again");
		} finally {
			config.playAlong = previousPlayAlong;
			config.duration = previousDuration;
		}
	}

	/**
	 * The overhead view: put the cursor over a block on screen, click to mine it, click again to build there,
	 * and check that W walks up the screen.
	 */
	private static void overhead(ClientGameTestContext context, TestInput input, TestSingleplayerContext world) {
		world.getServer().runCommand("gamemode creative @a");
		world.getServer().runCommand("tp @p 0.5 -60 20.5 0 10");
		world.getServer().runCommand("setblock 2 -60 20 minecraft:gold_block");
		world.getServer().runCommand("clear @p");
		world.getServer().runCommand("give @p minecraft:oak_planks 16");
		input.pressKey(options -> options.keyHotbarSlots[0]);
		context.waitTicks(10);
		BlockPos gold = new BlockPos(2, -60, 20);

		input.pressKey(Keybinds.OVERHEAD);
		context.waitTicks(16);
		check(CameraControl.get().mode() == CameraControl.Mode.OVERHEAD, "the overhead view should be on");
		check(Splash.isShowing(), "the Zocular title should show when the overhead view switches on");
		context.takeScreenshot("overhead-splash");
		context.waitTicks(40);
		check(!Splash.isShowing(), "the title should be gone after a couple of seconds");
		check(!context.computeOnClient(minecraft -> minecraft.mouseHandler.isMouseGrabbed()), "the cursor should be free");

		double[] onGold = project(context, Vec3.atCenterOf(gold).add(0.0, 0.5, 0.0));
		input.setCursorPos(onGold[0], onGold[1]);
		context.waitTicks(3);
		BlockPos aimed = context.computeOnClient(minecraft ->
			minecraft.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : null);
		check(gold.equals(aimed), "the cursor should point the player at the gold block, got " + aimed);
		context.takeScreenshot("overhead");

		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
		check(context.computeOnClient(minecraft -> minecraft.level.getBlockState(gold).isAir()), "clicking should mine the block under the cursor");
		check(!context.computeOnClient(minecraft -> minecraft.mouseHandler.isMouseGrabbed()), "clicking must not grab the mouse");

		double[] onGround = project(context, Vec3.atCenterOf(gold).subtract(0.0, 0.5, 0.0));
		input.setCursorPos(onGround[0], onGround[1]);
		context.waitTicks(3);
		input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitTicks(5);
		check(context.computeOnClient(minecraft -> minecraft.level.getBlockState(gold).is(Blocks.OAK_PLANKS)), "right-clicking should place a block where the cursor is");
		context.takeScreenshot("overhead-built");

		input.pressKey(Keybinds.ROLL_RIGHT);
		context.waitTicks(20);
		Vec3 before = context.computeOnClient(minecraft -> minecraft.player.position());
		float cameraYaw = CameraControl.get().overhead().yaw();
		input.holdKeyFor(options -> options.keyUp, 20);
		Vec3 moved = context.computeOnClient(minecraft -> minecraft.player.position()).subtract(before).multiply(1.0, 0.0, 1.0);
		Vec3 up = Vec3.directionFromRotation(0.0F, cameraYaw);
		check(moved.length() > 1.0, "W should walk, moved " + moved.length());
		check(moved.normalize().dot(up) > 0.85, "W should walk up the screen, dot " + moved.normalize().dot(up));
		context.takeScreenshot("overhead-walked");

		input.pressKey(Keybinds.OVERHEAD);
		context.waitTicks(3);
		check(CameraControl.get().mode() == CameraControl.Mode.NONE, "the overhead view should be off");
		check(context.computeOnClient(minecraft -> minecraft.options.getCameraType()) == CameraType.FIRST_PERSON, "first person should be restored");
		world.getServer().runCommand("clear @p");
	}

	/** Where a world point lands on screen, in window pixels, using the camera the game actually rendered. */
	private static double[] project(ClientGameTestContext context, Vec3 point) {
		return context.computeOnClient(minecraft -> {
			Camera camera = minecraft.gameRenderer.mainCamera();
			Vec3 forward = new Vec3(camera.forwardVector().x(), camera.forwardVector().y(), camera.forwardVector().z());
			Vec3 up = new Vec3(camera.upVector().x(), camera.upVector().y(), camera.upVector().z());
			Vec3 right = new Vec3(-camera.leftVector().x(), -camera.leftVector().y(), -camera.leftVector().z());
			Vec3 offset = point.subtract(camera.position());
			double depth = offset.dot(forward);
			double width = minecraft.getWindow().getScreenWidth();
			double height = minecraft.getWindow().getScreenHeight();
			double tan = Math.tan(Math.toRadians(camera.getFov()) / 2.0);
			double x = offset.dot(right) / (depth * tan * width / height);
			double y = offset.dot(up) / (depth * tan);
			return new double[] {(x + 1.0) / 2.0 * width, (1.0 - y) / 2.0 * height};
		});
	}

	private static void cinematic(ClientGameTestContext context, TestInput input) {
		input.pressKey(Keybinds.CINEMATIC);
		context.waitTicks(40);
		check(CameraControl.get().mode() == CameraControl.Mode.CINEMATIC, "cinematic camera should be active");
		check(context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden()), "cinematic camera hides the HUD");
		context.takeScreenshot("cinematic");

		input.pressKey(Keybinds.NEXT_SHOT);
		context.waitTicks(10);
		context.takeScreenshot("cinematic-next-shot");

		input.holdKeyFor(options -> options.keyUp, 40);
		context.takeScreenshot("cinematic-walking");

		input.pressKey(Keybinds.CINEMATIC);
		context.waitTicks(45);
		check(CameraControl.get().mode() == CameraControl.Mode.NONE, "cinematic camera should have finished leaving");
		check(context.computeOnClient(minecraft -> minecraft.options.getCameraType()) == CameraType.FIRST_PERSON, "first person should be restored");
		check(!context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden()), "the HUD should be visible again");
	}

	private static void shoulder(ClientGameTestContext context, TestInput input) {
		context.runOnClient(minecraft -> {
			ZocularConfig.get().thirdPerson.shoulder = ZocularConfig.Shoulder.RIGHT;
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		context.waitTicks(5);
		context.takeScreenshot("shoulder");
		context.runOnClient(minecraft -> {
			ZocularConfig.get().thirdPerson.shoulder = ZocularConfig.Shoulder.CENTER;
			minecraft.options.setCameraType(CameraType.FIRST_PERSON);
		});
	}

	private static void hudEditor(ClientGameTestContext context, TestInput input) {
		context.setScreen(() -> new HudEditorScreen(null));
		context.waitTicks(4);
		context.takeScreenshot("hud-editor");

		// Drag the zoom indicator from the top right corner to the top middle.
		int[] gui = context.computeOnClient(minecraft -> new int[] {
			minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getScreenWidth()
		});
		double scale = gui[1] / (double) gui[0];
		input.setCursorPos((gui[0] - 14) * scale, 12 * scale);
		input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		for (int i = 0; i < 10; i++) {
			input.moveCursor(-(gui[0] / 2.0 - 30) * scale / 10.0, 2 * scale);
			context.waitTick();
		}
		input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		ZocularConfig.HudPosition moved = context.computeOnClient(minecraft -> ZocularConfig.get().hud.positions.get("zoom"));
		check(moved != null && moved.anchor == ZocularConfig.Anchor.TOP, "dragging should move the zoom indicator to the top middle, got "
			+ (moved == null ? "nothing" : moved.anchor));
		context.takeScreenshot("hud-editor-moved");

		input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitTicks(2);
		check(!context.computeOnClient(minecraft -> ZocularConfig.get().hud.positions.containsKey("zoom")), "right-click should reset it");
		input.pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(2);
	}

	/** Bindings left on the 2.0 defaults (Z and K) are moved to C and F4 on the first tick. */
	private static void checkDefaultKeys(ClientGameTestContext context) {
		int zoom = context.computeOnClient(minecraft -> KeyMappingHelper.getBoundKeyOf(Keybinds.ZOOM).getValue());
		int freecam = context.computeOnClient(minecraft -> KeyMappingHelper.getBoundKeyOf(Keybinds.FREECAM).getValue());
		check(zoom == InputConstants.KEY_C, "zoom should be bound to C, got " + zoom);
		check(freecam == InputConstants.KEY_F4, "freecam should be bound to F4, got " + freecam);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
