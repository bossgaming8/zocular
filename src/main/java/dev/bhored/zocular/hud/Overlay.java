package dev.bhored.zocular.hud;

import dev.bhored.zocular.Keybinds;
import dev.bhored.zocular.Zocular;
import dev.bhored.zocular.Zoom;
import dev.bhored.zocular.camera.CameraControl;
import dev.bhored.zocular.camera.KeyframePath;
import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.config.ZocularConfig.PanelMode;
import dev.bhored.zocular.gui.HudEditorScreen;
import dev.bhored.zocular.gui.Theme;
import dev.bhored.zocular.hud.HudLayout.Element;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/**
 * Zocular's HUD elements. Each draw method measures its element, asks {@link HudLayout} where it goes, draws it
 * and returns its bounds, which is also how the layout editor previews them.
 */
public final class Overlay {
	private static final int BLACK = 0xFF000000;

	private Overlay() {
	}

	public static void register() {
		// Letterbox and vignette sit before the sleep layer, the only vanilla layer that still draws with the HUD hidden.
		HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, Zocular.id("cinema"), Overlay::cinema);
		HudElementRegistry.addLast(Zocular.id("overlay"), Overlay::overlay);
	}

	private static void cinema(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		CameraControl control = CameraControl.get();
		ZocularConfig.Cinematic config = ZocularConfig.get().cinematic;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();

		float vignette = control.vignetteAmount() * config.vignette.strength;
		if (vignette > 0.01F) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, Theme.VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, 256, 256, 256, 256, ARGB.white(vignette));
		}

		int bar = Math.round(height * config.letterbox * control.letterboxAmount());
		if (bar > 0) {
			graphics.fill(0, 0, width, bar, BLACK);
			graphics.fill(0, height - bar, width, height, BLACK);
		}
	}

	private static void overlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.hud.isHidden() || minecraft.player == null || minecraft.gui.screen() instanceof HudEditorScreen) {
			return;
		}
		ZocularConfig.Hud config = ZocularConfig.get().hud;
		Font font = minecraft.font;
		CameraControl control = CameraControl.get();

		float zoomAlpha = Zoom.indicatorAlpha();
		if (config.zoomIndicator && zoomAlpha > 0.0F) {
			zoomIndicator(graphics, font, Zoom.isEngaged() ? Zoom.magnification() : Zoom.targetLevel(), zoomAlpha);
		}
		if (control.mode() == CameraControl.Mode.FREECAM && config.freecamPanel != PanelMode.HIDDEN) {
			KeyframePath path = control.path();
			freecamPanel(graphics, font, minecraft.options, ZocularConfig.get().freecam.speed, path.size(),
				config.freecamPanel == PanelMode.FULL);
		}
		if (control.path().isPlaying() && config.pathTimeline) {
			KeyframePath path = control.path();
			float[] markers = new float[path.size()];
			for (int i = 0; i < markers.length; i++) {
				markers[i] = path.keyframeFraction(i);
			}
			timeline(graphics, path.fraction(), markers);
		}
		if (control.leagueActive()) {
			walkMarker(graphics, minecraft);
			leagueBox(graphics, font, control);
		}
		Splash.draw(graphics, font);
		Component message = Notices.current();
		if (message != null) {
			notice(graphics, font, message, Notices.alpha());
		}
	}

	public static int[] zoomIndicator(GuiGraphicsExtractor graphics, Font font, float level, float alpha) {
		String text = String.format(Locale.ROOT, "%.1f×", level);
		int width = font.width(text) + 24;
		int height = 15;
		int[] at = HudLayout.place(Element.ZOOM, width, height, graphics.guiWidth(), graphics.guiHeight());
		int x = at[0];
		int y = at[1];

		Theme.box(graphics, x, y, width, height, Theme.fade(Theme.HUD_BACKGROUND, alpha));
		Theme.sprite(graphics, Zocular.id("icon/zoom"), x + 5, y + 2, 9, 9, Theme.fade(Theme.ACCENT, alpha));
		graphics.text(font, text, x + 17, y + 3, Theme.fade(Theme.TEXT, alpha), false);

		double max = Math.log(ZocularConfig.get().zoom.maxLevel);
		float fill = (float) Math.min(1.0, Math.log(Math.max(level, 1.0F)) / max);
		int barWidth = width - 8;
		graphics.fill(x + 4, y + height - 3, x + 4 + barWidth, y + height - 2, Theme.fade(Theme.TRACK, alpha));
		graphics.fill(x + 4, y + height - 3, x + 4 + Math.round(barWidth * fill), y + height - 2, Theme.fade(Theme.ACCENT, alpha));
		return new int[] {x, y, width, height};
	}

	public static int[] freecamPanel(GuiGraphicsExtractor graphics, Font font, Options options, float speed, int keyframes, boolean full) {
		Component title = Component.translatable("zocular.hud.freecam");
		Component speedText = Component.translatable("zocular.unit.speed", String.format(Locale.ROOT, "%.1f", speed));
		Component key = Keybinds.FREECAM_PANEL.isUnbound() ? null : Keybinds.FREECAM_PANEL.getTranslatedKeyMessage();

		if (!full) {
			int chip = key == null ? 0 : chipWidth(font, key, null) + 6;
			int width = 23 + font.width(title) + 8 + font.width(speedText) + 8 + chip;
			int height = 18;
			int[] at = HudLayout.place(Element.FREECAM, width, height, graphics.guiWidth(), graphics.guiHeight());
			int x = at[0];
			int y = at[1];
			Theme.box(graphics, x, y, width, height, Theme.HUD_BACKGROUND);
			Theme.sprite(graphics, Zocular.id("icon/freecam"), x + 6, y + 3, 12, 12, Theme.ACCENT);
			graphics.text(font, title, x + 23, y + 5, Theme.ACCENT, false);
			graphics.text(font, speedText, x + 23 + font.width(title) + 8, y + 5, Theme.TEXT, false);
			if (key != null) {
				chip(graphics, font, x + width - chip, y + 3, key, null);
			}
			return new int[] {x, y, width, height};
		}

		Component details = Component.translatable("zocular.hud.freecam_details", speedText, keyframes);
		Component hide = Component.translatable("zocular.hud.hide");
		Component[] help = {
			Component.translatable("zocular.hud.hint.keyframe", options.keyAttack.getTranslatedKeyMessage()),
			Component.translatable("zocular.hud.hint.play", options.keyUse.getTranslatedKeyMessage()),
			Component.translatable("zocular.hud.hint.undo", options.keyPickItem.getTranslatedKeyMessage()),
			Component.translatable("zocular.hud.hint.speed")
		};

		int chip = key == null ? 0 : chipWidth(font, key, hide);
		int width = Math.max(23 + font.width(title) + 12 + chip + 8, font.width(details) + 16);
		for (Component line : help) {
			width = Math.max(width, font.width(line) + 16);
		}
		int height = 36 + help.length * 10;
		int[] at = HudLayout.place(Element.FREECAM, width, height, graphics.guiWidth(), graphics.guiHeight());
		int x = at[0];
		int y = at[1];

		Theme.box(graphics, x, y, width, height, Theme.HUD_BACKGROUND);
		Theme.sprite(graphics, Zocular.id("icon/freecam"), x + 7, y + 6, 12, 12, Theme.ACCENT);
		graphics.text(font, title, x + 23, y + 8, Theme.ACCENT, false);
		if (key != null) {
			chip(graphics, font, x + width - 6 - chip, y + 5, key, hide);
		}
		graphics.text(font, details, x + 8, y + 20, Theme.TEXT_MUTED, false);
		graphics.fill(x + 8, y + 31, x + width - 8, y + 32, Theme.BORDER);
		int lineY = y + 36;
		for (Component line : help) {
			graphics.text(font, line, x + 8, lineY, Theme.TEXT_FAINT, false);
			lineY += 10;
		}
		return new int[] {x, y, width, height};
	}

	/** A small key cap, optionally followed by what the key does, so it reads as a button. */
	private static void chip(GuiGraphicsExtractor graphics, Font font, int x, int y, Component key, Component action) {
		int keyWidth = Math.max(12, font.width(key) + 6);
		Theme.box(graphics, x, y, keyWidth, 12, Theme.SURFACE_ACTIVE);
		Theme.outline(graphics, x, y, keyWidth, 12, Theme.fade(Theme.ACCENT, 0.7F));
		graphics.centeredText(font, key, x + keyWidth / 2, y + 2, Theme.TEXT);
		if (action != null) {
			graphics.text(font, action, x + keyWidth + 4, y + 2, Theme.TEXT_MUTED, false);
		}
	}

	private static int chipWidth(Font font, Component key, Component action) {
		int keyWidth = Math.max(12, font.width(key) + 6);
		return action == null ? keyWidth : keyWidth + 4 + font.width(action);
	}

	public static int[] timeline(GuiGraphicsExtractor graphics, float fraction, float[] markers) {
		int width = Math.min(graphics.guiWidth() - 40, 260);
		int height = 6;
		int[] at = HudLayout.place(Element.TIMELINE, width, height, graphics.guiWidth(), graphics.guiHeight());
		int x = at[0];
		int y = at[1] + 2;
		graphics.fill(x, y, x + width, y + 2, 0x66FFFFFF);
		graphics.fill(x, y, x + Math.round(width * fraction), y + 2, Theme.ACCENT);
		for (float marker : markers) {
			int markerX = x + Math.round(width * marker);
			graphics.fill(markerX - 1, y - 2, markerX + 1, y + 4, Theme.TEXT);
		}
		return new int[] {at[0], at[1], width, height};
	}

	/** The League mode box in the bottom right: which camera is on and the keys that matter. */
	private static void leagueBox(GuiGraphicsExtractor graphics, Font font, CameraControl control) {
		Component title = Component.translatable("zocular.hud.league");
		java.util.List<Component> lines = new java.util.ArrayList<>();
		lines.add(Component.translatable("zocular.hud.league.camera", control.overhead().camera().label()));
		lines.add(Component.translatable("zocular.hud.league.switch", Keybinds.NEXT_SHOT.getTranslatedKeyMessage()));
		if (control.overhead().camera() == ZocularConfig.LeagueCamera.FREE) {
			lines.add(Component.translatable("zocular.hud.league.back"));
		}
		lines.add(Component.translatable("zocular.hud.league.build"));
		int width = font.width(title);
		for (Component line : lines) {
			width = Math.max(width, font.width(line));
		}
		width += 14;
		int height = 18 + lines.size() * 11;
		int x = graphics.guiWidth() - width - 6;
		int y = graphics.guiHeight() - height - 26;
		Theme.box(graphics, x, y, width, height, Theme.HUD_BACKGROUND);
		graphics.text(font, title, x + 7, y + 5, Theme.ACCENT, false);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), x + 7, y + 17 + i * 11, Theme.TEXT, false);
		}
	}

	/** A green ring on the ground where League mode is walking you to. */
	private static void walkMarker(GuiGraphicsExtractor graphics, Minecraft minecraft) {
		net.minecraft.world.phys.Vec3 target = CameraControl.get().overhead().walkMarker();
		if (target == null) {
			return;
		}
		net.minecraft.client.Camera camera = minecraft.gameRenderer.mainCamera();
		net.minecraft.world.phys.Vec3 offset = target.subtract(camera.position());
		double depth = offset.x * camera.forwardVector().x() + offset.y * camera.forwardVector().y() + offset.z * camera.forwardVector().z();
		if (depth < 0.5) {
			return;
		}
		double right = -(offset.x * camera.leftVector().x() + offset.y * camera.leftVector().y() + offset.z * camera.leftVector().z());
		double up = offset.x * camera.upVector().x() + offset.y * camera.upVector().y() + offset.z * camera.upVector().z();
		double tan = Math.tan(Math.toRadians(camera.getFov()) / 2.0);
		double aspect = (double) graphics.guiWidth() / graphics.guiHeight();
		int gx = (int) Math.round((right / (depth * tan * aspect) + 1.0) / 2.0 * graphics.guiWidth());
		int gy = (int) Math.round((1.0 - up / (depth * tan)) / 2.0 * graphics.guiHeight());
		int green = 0xFF5ADC78;
		Theme.outline(graphics, gx - 9, gy - 5, 18, 10, green);
		graphics.fill(gx - 2, gy - 1, gx + 2, gy + 1, green);
	}

	public static int[] notice(GuiGraphicsExtractor graphics, Font font, Component message, float alpha) {
		int width = font.width(message) + 16;
		int height = 16;
		int[] at = HudLayout.place(Element.NOTICE, width, height, graphics.guiWidth(), graphics.guiHeight());
		Theme.box(graphics, at[0], at[1], width, height, Theme.fade(Theme.HUD_BACKGROUND, alpha));
		graphics.centeredText(font, message, at[0] + width / 2, at[1] + 4, Theme.fade(Theme.TEXT, alpha));
		return new int[] {at[0], at[1], width, height};
	}
}
