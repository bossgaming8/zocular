package dev.bhored.zocular.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bhored.zocular.Keybinds;
import dev.bhored.zocular.Zoom;
import dev.bhored.zocular.camera.CameraControl;
import dev.bhored.zocular.config.Settings;
import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.gui.HudEditorScreen;
import dev.bhored.zocular.gui.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
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

		settingsPages(context, "title");
		compactLayout(context, input);

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("fill ~5 ~ ~5 ~9 ~6 ~9 minecraft:oak_log");
			world.getServer().runCommand("fill ~-8 ~ ~3 ~-4 ~3 ~7 minecraft:stone_bricks");
			world.getServer().runCommand("tp @p ~ ~ ~ 45 10");
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("world");

			zoom(context, input);
			freecamAndPath(context, input);
			cinematic(context, input);
			shoulder(context, input);
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

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
