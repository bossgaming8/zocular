package dev.bhored.zocular.hud;

import dev.bhored.zocular.Zocular;
import dev.bhored.zocular.util.Motion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * A short title card: the Zocular title pops in at the top of the screen with a line of text under it, holds, then
 * fades. Used when the overhead view switches on.
 */
public final class Splash {
	public static final Identifier TITLE = Zocular.id("textures/gui/title.png");
	private static final int TITLE_WIDTH = 1024;
	private static final int TITLE_HEIGHT = 224;
	private static final float POP_IN = 0.28F;
	private static final float HOLD_UNTIL = 1.9F;
	private static final float GONE_AT = 2.4F;

	private static long shownAt = -1L;
	private static @Nullable Component heading;
	private static @Nullable Component hint;

	private Splash() {
	}

	public static void show(Component headingText, Component hintText) {
		heading = headingText;
		hint = hintText;
		shownAt = Util.getMillis();
	}

	public static void hide() {
		shownAt = -1L;
	}

	public static boolean isShowing() {
		return shownAt >= 0L;
	}

	static void draw(GuiGraphicsExtractor graphics, Font font) {
		if (shownAt < 0L) {
			return;
		}
		float t = (Util.getMillis() - shownAt) / 1000.0F;
		if (t >= GONE_AT) {
			shownAt = -1L;
			return;
		}
		float alpha;
		float scale = 1.0F;
		if (t < POP_IN) {
			float p = Motion.easeOut(t / POP_IN);
			alpha = p;
			scale = 0.86F + 0.14F * p;
		} else if (t > HOLD_UNTIL) {
			alpha = 1.0F - (t - HOLD_UNTIL) / (GONE_AT - HOLD_UNTIL);
		} else {
			alpha = 1.0F;
		}
		alpha = Mth.clamp(alpha, 0.0F, 1.0F);

		int screenWidth = graphics.guiWidth();
		float width = Math.min(screenWidth * 0.46F, 300.0F);
		float height = width * TITLE_HEIGHT / TITLE_WIDTH;
		float centreX = screenWidth / 2.0F;
		float top = graphics.guiHeight() * 0.11F;

		graphics.pose().pushMatrix();
		graphics.pose().translate(centreX, top + height / 2.0F);
		graphics.pose().scale(scale * width / TITLE_WIDTH, scale * width / TITLE_WIDTH);
		int x = -TITLE_WIDTH / 2;
		int y = -TITLE_HEIGHT / 2;
		// A hard drop shadow, the title's own shape in black, so it reads on snow and sky as well as grass.
		graphics.blit(RenderPipelines.GUI_TEXTURED, TITLE, x + 10, y + 16, 0.0F, 0.0F, TITLE_WIDTH, TITLE_HEIGHT,
			TITLE_WIDTH, TITLE_HEIGHT, TITLE_WIDTH, TITLE_HEIGHT, ARGB.color(Math.round(alpha * 110), 0, 0, 0));
		graphics.blit(RenderPipelines.GUI_TEXTURED, TITLE, x, y, 0.0F, 0.0F, TITLE_WIDTH, TITLE_HEIGHT,
			TITLE_WIDTH, TITLE_HEIGHT, TITLE_WIDTH, TITLE_HEIGHT, ARGB.white(alpha));
		graphics.pose().popMatrix();

		// Text with almost no alpha is drawn fully opaque by the font renderer, so skip it at the very end.
		if (alpha < 0.05F) {
			return;
		}
		float textTop = top + height + 8.0F;
		if (heading != null) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(centreX, textTop);
			graphics.pose().scale(2.0F, 2.0F);
			graphics.centeredText(font, heading, 0, 0, ARGB.white(alpha));
			graphics.pose().popMatrix();
		}
		if (hint != null) {
			graphics.centeredText(font, hint, Math.round(centreX), Math.round(textTop + 22.0F), ARGB.color(Math.round(alpha * 255), 200, 200, 200));
		}
	}
}
