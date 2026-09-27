package dev.bhored.zocular.hud;

import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.config.ZocularConfig.Anchor;
import dev.bhored.zocular.config.ZocularConfig.HudPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Positions of the movable HUD elements. Each one is stored as an anchor (a corner, an edge or the center of the
 * screen) plus an offset from it, so a layout keeps its shape at any window size or GUI scale.
 */
public final class HudLayout {
	public enum Element {
		ZOOM("zoom", Anchor.TOP_RIGHT, -6, 6),
		FREECAM("freecam", Anchor.TOP_LEFT, 6, 6),
		NOTICE("notice", Anchor.CENTER, 0, -52),
		TIMELINE("timeline", Anchor.BOTTOM, 0, -52);

		private final String id;
		private final Anchor anchor;
		private final int x;
		private final int y;

		Element(String id, Anchor anchor, int x, int y) {
			this.id = id;
			this.anchor = anchor;
			this.x = x;
			this.y = y;
		}

		public Component label() {
			return Component.translatable("zocular.hud.element." + id);
		}

		HudPosition defaults() {
			return new HudPosition(anchor, x, y);
		}
	}

	private static final int EFFECT_ICON = 25;
	private static final int EDGE = 2;

	private HudLayout() {
	}

	public static HudPosition position(Element element) {
		HudPosition stored = ZocularConfig.get().hud.positions.get(element.id);
		return stored != null ? stored : element.defaults();
	}

	public static boolean isMoved(Element element) {
		return ZocularConfig.get().hud.positions.containsKey(element.id);
	}

	public static void reset(Element element) {
		ZocularConfig.get().hud.positions.remove(element.id);
	}

	/** Top-left corner for an element of the given size, kept on screen and clear of the potion effect icons. */
	public static int[] place(Element element, int width, int height, int screenWidth, int screenHeight) {
		HudPosition position = position(element);
		int x = anchorX(position.anchor, width, screenWidth) + position.x;
		int y = anchorY(position.anchor, height, screenHeight) + position.y;

		if (position.anchor.row == 0) {
			int[] effects = effectArea(screenWidth);
			boolean overlaps = x + width > effects[0] && y < effects[1];
			if (overlaps) {
				y = effects[1] + 3;
			}
		}
		x = Mth.clamp(x, EDGE, Math.max(EDGE, screenWidth - width - EDGE));
		y = Mth.clamp(y, EDGE, Math.max(EDGE, screenHeight - height - EDGE));
		return new int[] {x, y};
	}

	/** Stores a new top-left corner, picking whichever anchor the element now sits closest to. */
	public static void move(Element element, int left, int top, int width, int height, int screenWidth, int screenHeight) {
		int column = thirds(left + width / 2, screenWidth);
		int row = thirds(top + height / 2, screenHeight);
		Anchor anchor = Anchor.of(column, row);
		HudPosition position = new HudPosition(anchor,
			left - anchorX(anchor, width, screenWidth),
			top - anchorY(anchor, height, screenHeight));
		ZocularConfig.get().hud.positions.put(element.id, position);
	}

	private static int thirds(int value, int size) {
		return value < size / 3 ? 0 : value > size * 2 / 3 ? 2 : 1;
	}

	private static int anchorX(Anchor anchor, int width, int screenWidth) {
		return switch (anchor.column) {
			case 0 -> 0;
			case 1 -> (screenWidth - width) / 2;
			default -> screenWidth - width;
		};
	}

	private static int anchorY(Anchor anchor, int height, int screenHeight) {
		return switch (anchor.row) {
			case 0 -> 0;
			case 1 -> (screenHeight - height) / 2;
			default -> screenHeight - height;
		};
	}

	/** Left edge and bottom of the area vanilla uses for effect icons in the top right, or the right edge and 0. */
	private static int[] effectArea(int screenWidth) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
			return new int[] {screenWidth, 0};
		}
		int beneficial = 0;
		int harmful = 0;
		for (MobEffectInstance instance : minecraft.player.getActiveEffects()) {
			if (instance.showIcon()) {
				Holder<MobEffect> effect = instance.getEffect();
				if (effect.value().isBeneficial()) {
					beneficial++;
				} else {
					harmful++;
				}
			}
		}
		int bottom = harmful > 0 ? 52 : beneficial > 0 ? 26 : 0;
		if (bottom > 0 && minecraft.isDemo()) {
			bottom += 15;
		}
		return new int[] {screenWidth - EFFECT_ICON * Math.max(beneficial, harmful), bottom};
	}
}
