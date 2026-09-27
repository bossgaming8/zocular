package dev.bhored.zocular.hud;

import dev.bhored.zocular.Zocular;
import dev.bhored.zocular.Zoom;
import dev.bhored.zocular.camera.CameraControl;
import dev.bhored.zocular.camera.KeyframePath;
import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.gui.Theme;
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
		if (minecraft.gui.hud.isHidden() || minecraft.player == null) {
			return;
		}
		ZocularConfig.Hud config = ZocularConfig.get().hud;
		Font font = minecraft.font;

		if (config.zoomIndicator) {
			zoomIndicator(graphics, font);
		}
		CameraControl control = CameraControl.get();
		if (control.mode() == CameraControl.Mode.FREECAM && config.freecamPanel) {
			freecamPanel(graphics, font, minecraft.options, control, config.hints);
		}
		if (control.path().isPlaying()) {
			timeline(graphics, control.path());
		}
		notice(graphics, font);
	}

	private static void zoomIndicator(GuiGraphicsExtractor graphics, Font font) {
		float alpha = Zoom.indicatorAlpha();
		if (alpha <= 0.0F) {
			return;
		}
		float level = Zoom.isEngaged() ? Zoom.magnification() : Zoom.targetLevel();
		String text = String.format(Locale.ROOT, "%.1f×", level);
		int textWidth = font.width(text);
		int width = textWidth + 24;
		int height = 15;
		int x = (graphics.guiWidth() - width) / 2;
		int y = graphics.guiHeight() / 2 + 14;

		Theme.box(graphics, x, y, width, height, Theme.fade(Theme.HUD_BACKGROUND, alpha));
		Theme.sprite(graphics, Zocular.id("icon/zoom"), x + 5, y + 2, 9, 9, Theme.fade(Theme.ACCENT, alpha));
		graphics.text(font, text, x + 17, y + 3, Theme.fade(Theme.TEXT, alpha), false);

		double max = Math.log(ZocularConfig.get().zoom.maxLevel);
		float fill = (float) Math.min(1.0, Math.log(Math.max(level, 1.0F)) / max);
		int barWidth = width - 8;
		graphics.fill(x + 4, y + height - 3, x + 4 + barWidth, y + height - 2, Theme.fade(Theme.TRACK, alpha));
		graphics.fill(x + 4, y + height - 3, x + 4 + Math.round(barWidth * fill), y + height - 2, Theme.fade(Theme.ACCENT, alpha));
	}

	private static void freecamPanel(GuiGraphicsExtractor graphics, Font font, Options options, CameraControl control, boolean hints) {
		KeyframePath path = control.path();
		Component title = Component.translatable("zocular.hud.freecam");
		Component speed = Component.translatable("zocular.unit.speed",
			String.format(Locale.ROOT, "%.1f", ZocularConfig.get().freecam.speed));
		Component keyframes = Component.translatable("zocular.hud.keyframes", path.size());
		Component[] help = hints ? new Component[] {
			Component.translatable("zocular.hud.hint.keyframe", options.keyAttack.getTranslatedKeyMessage()),
			Component.translatable("zocular.hud.hint.play", options.keyUse.getTranslatedKeyMessage()),
			Component.translatable("zocular.hud.hint.undo", options.keyPickItem.getTranslatedKeyMessage()),
			Component.translatable("zocular.hud.hint.speed")
		} : new Component[0];

		int width = Math.max(font.width(title) + font.width(speed) + 34, font.width(keyframes) + 16);
		for (Component line : help) {
			width = Math.max(width, font.width(line) + 16);
		}
		int height = 30 + (help.length > 0 ? help.length * 10 + 6 : 0);
		int x = 6;
		int y = 6;

		Theme.box(graphics, x, y, width, height, Theme.HUD_BACKGROUND);
		Theme.sprite(graphics, Zocular.id("icon/freecam"), x + 7, y + 6, 12, 12, Theme.ACCENT);
		graphics.text(font, title, x + 23, y + 8, Theme.ACCENT, false);
		graphics.text(font, speed, x + width - 8 - font.width(speed), y + 8, Theme.TEXT, false);
		graphics.text(font, keyframes, x + 8, y + 19, Theme.TEXT_MUTED, false);

		if (help.length > 0) {
			graphics.fill(x + 8, y + 31, x + width - 8, y + 32, Theme.BORDER);
			int lineY = y + 36;
			for (Component line : help) {
				graphics.text(font, line, x + 8, lineY, Theme.TEXT_FAINT, false);
				lineY += 10;
			}
		}
	}

	private static void timeline(GuiGraphicsExtractor graphics, KeyframePath path) {
		int width = Math.min(graphics.guiWidth() - 40, 260);
		int x = (graphics.guiWidth() - width) / 2;
		int y = graphics.guiHeight() - 12;
		graphics.fill(x, y, x + width, y + 2, 0x66FFFFFF);
		graphics.fill(x, y, x + Math.round(width * path.fraction()), y + 2, Theme.ACCENT);
		for (int i = 0; i < path.size(); i++) {
			int markerX = x + Math.round(width * path.keyframeFraction(i));
			graphics.fill(markerX - 1, y - 2, markerX + 1, y + 4, Theme.TEXT);
		}
	}

	private static void notice(GuiGraphicsExtractor graphics, Font font) {
		Component message = Notices.current();
		if (message == null) {
			return;
		}
		float alpha = Notices.alpha();
		int width = font.width(message) + 16;
		int x = (graphics.guiWidth() - width) / 2;
		int y = graphics.guiHeight() / 4;
		Theme.box(graphics, x, y, width, 16, Theme.fade(Theme.HUD_BACKGROUND, alpha));
		graphics.centeredText(font, message, graphics.guiWidth() / 2, y + 4, Theme.fade(Theme.TEXT, alpha));
	}
}
