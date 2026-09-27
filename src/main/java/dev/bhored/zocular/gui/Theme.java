package dev.bhored.zocular.gui;

import dev.bhored.zocular.Zocular;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * Colors and shared drawing helpers. The amber accent is taken from the Zocular logo.
 */
public final class Theme {
	public static final int ACCENT = 0xFFF2A93B;
	public static final int ACCENT_BRIGHT = 0xFFFFC766;
	public static final int ON_ACCENT = 0xFF20150A;
	public static final int WARNING = 0xFFE9785E;
	public static final int TEXT = 0xFFF3EEE9;
	public static final int TEXT_MUTED = 0xFFA79E99;
	public static final int TEXT_FAINT = 0xFF6F676B;
	public static final int WINDOW = 0xF0141217;
	public static final int SIDEBAR = 0xFF0E0D11;
	public static final int SURFACE = 0xFF1E1B22;
	public static final int SURFACE_HOVER = 0xFF28242D;
	public static final int SURFACE_ACTIVE = 0xFF332E38;
	public static final int TRACK = 0xFF3B3541;
	public static final int BORDER = 0x22FFFFFF;
	public static final int HUD_BACKGROUND = 0xC0141217;

	public static final Identifier ROUNDED = Zocular.id("rounded");
	public static final Identifier ROUNDED_OUTLINE = Zocular.id("rounded_outline");
	public static final Identifier TOGGLE = Zocular.id("toggle");
	public static final Identifier KNOB = Zocular.id("knob");
	public static final Identifier TRACK_SPRITE = Zocular.id("track");
	public static final Identifier SHADOW = Zocular.id("shadow");
	public static final Identifier LOGO = Zocular.id("textures/gui/logo.png");
	public static final Identifier VIGNETTE = Zocular.id("textures/gui/vignette.png");
	public static final int LOGO_WIDTH = 512;
	public static final int LOGO_HEIGHT = 112;

	private Theme() {
	}

	public static void box(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ROUNDED, x, y, width, height, color);
	}

	public static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ROUNDED_OUTLINE, x, y, width, height, color);
	}

	public static void shadow(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float strength) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SHADOW, x - 9, y - 7, width + 18, height + 20, ARGB.white(strength));
	}

	public static void sprite(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, int height, int color) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, color);
	}

	public static void logo(GuiGraphicsExtractor graphics, int x, int y, int width, float alpha) {
		int height = Math.round(width * (float) LOGO_HEIGHT / LOGO_WIDTH);
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0.0F, 0.0F, width, height, LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT, ARGB.white(alpha));
	}

	/** Just the "Z" from the logo, for tight spaces. */
	public static void logoMark(GuiGraphicsExtractor graphics, int x, int y, int height) {
		int sourceWidth = 84;
		int width = Math.round(height * (float) sourceWidth / LOGO_HEIGHT);
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0.0F, 0.0F, width, height, sourceWidth, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);
	}

	public static int fade(int color, float alpha) {
		return ARGB.multiplyAlpha(color, alpha);
	}

	public static int mix(int from, int to, float t) {
		return ARGB.color(
			lerp(ARGB.alpha(from), ARGB.alpha(to), t),
			lerp(ARGB.red(from), ARGB.red(to), t),
			lerp(ARGB.green(from), ARGB.green(to), t),
			lerp(ARGB.blue(from), ARGB.blue(to), t)
		);
	}

	private static int lerp(int from, int to, float t) {
		return Math.round(from + (to - from) * t);
	}
}
