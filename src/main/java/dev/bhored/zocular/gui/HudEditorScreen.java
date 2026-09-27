package dev.bhored.zocular.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.config.ZocularConfig.PanelMode;
import dev.bhored.zocular.hud.HudLayout;
import dev.bhored.zocular.hud.HudLayout.Element;
import dev.bhored.zocular.hud.Overlay;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/** Lets players drag Zocular's HUD elements to wherever they like. */
public final class HudEditorScreen extends Screen {
	private static final int SNAP = 6;
	private static final int BUTTON_WIDTH = 70;

	private final @Nullable Screen parent;
	private final Map<Element, int[]> bounds = new EnumMap<>(Element.class);
	private @Nullable Element hovered;
	private @Nullable Element dragging;
	private int grabX;
	private int grabY;

	public HudEditorScreen(@Nullable Screen parent) {
		super(Component.translatable("zocular.hud_editor.title"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level == null) {
			extractPanorama(graphics, partialTick);
		}
		graphics.fill(0, 0, width, height, 0x40000000);
		graphics.fill(width / 2, 0, width / 2 + 1, height, 0x18FFFFFF);
		graphics.fill(0, height / 2, width, height / 2 + 1, 0x18FFFFFF);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		ZocularConfig.Hud hud = ZocularConfig.get().hud;
		bounds.put(Element.TIMELINE, Overlay.timeline(graphics, 0.4F, new float[] {0.0F, 0.35F, 0.7F, 1.0F}));
		bounds.put(Element.NOTICE, Overlay.notice(graphics, font, Component.translatable("zocular.notice.keyframe_added", 3), 1.0F));
		bounds.put(Element.ZOOM, Overlay.zoomIndicator(graphics, font, 4.0F, 1.0F));
		bounds.put(Element.FREECAM, Overlay.freecamPanel(graphics, font, minecraft.options, 10.0F, 3, hud.freecamPanel != PanelMode.COMPACT));

		hovered = dragging != null ? dragging : elementAt(mouseX, mouseY);
		for (Map.Entry<Element, int[]> entry : bounds.entrySet()) {
			int[] box = entry.getValue();
			boolean hot = entry.getKey() == hovered;
			Theme.outline(graphics, box[0] - 3, box[1] - 3, box[2] + 6, box[3] + 6, hot ? Theme.ACCENT : 0x70FFFFFF);
			if (!isEnabled(entry.getKey(), hud)) {
				graphics.fill(box[0] - 2, box[1] - 2, box[0] + box[2] + 2, box[1] + box[3] + 2, 0x90101014);
			}
		}
		if (hovered != null) {
			label(graphics, hovered, bounds.get(hovered), !isEnabled(hovered, hud));
		}
		toolbar(graphics, mouseX, mouseY);
	}

	private void label(GuiGraphicsExtractor graphics, Element element, int[] box, boolean disabled) {
		Component name = disabled
			? Component.translatable("zocular.hud_editor.hidden", element.label())
			: element.label();
		int width = font.width(name) + 10;
		int x = Math.min(Math.max(2, box[0] - 3), this.width - width - 2);
		int y = box[1] - 18 >= 2 ? box[1] - 18 : box[1] + box[3] + 5;
		Theme.box(graphics, x, y, width, 13, Theme.ACCENT);
		graphics.text(font, name, x + 5, y + 3, Theme.ON_ACCENT, false);
	}

	private void toolbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int[] box = toolbarBox();
		Theme.box(graphics, box[0], box[1], box[2], box[3], Theme.WINDOW);
		Theme.outline(graphics, box[0], box[1], box[2], box[3], Theme.BORDER);
		graphics.centeredText(font, title, width / 2, box[1] + 6, Theme.ACCENT);
		graphics.centeredText(font, Component.translatable("zocular.hud_editor.hint"), width / 2, box[1] + 18, Theme.TEXT_MUTED);

		int buttonY = box[1] + 31;
		int resetX = width / 2 - BUTTON_WIDTH - 3;
		int doneX = width / 2 + 3;
		boolean resetHover = inside(mouseX, mouseY, resetX, buttonY, BUTTON_WIDTH, 16);
		boolean doneHover = inside(mouseX, mouseY, doneX, buttonY, BUTTON_WIDTH, 16);
		Theme.box(graphics, resetX, buttonY, BUTTON_WIDTH, 16, resetHover ? Theme.SURFACE_ACTIVE : Theme.SURFACE);
		graphics.centeredText(font, Component.translatable("zocular.hud_editor.reset_all"), resetX + BUTTON_WIDTH / 2, buttonY + 4, resetHover ? Theme.TEXT : Theme.TEXT_MUTED);
		Theme.box(graphics, doneX, buttonY, BUTTON_WIDTH, 16, doneHover ? Theme.ACCENT_BRIGHT : Theme.ACCENT);
		graphics.centeredText(font, Component.translatable("gui.done"), doneX + BUTTON_WIDTH / 2, buttonY + 4, Theme.ON_ACCENT);
	}

	private int[] toolbarBox() {
		int boxWidth = Math.max(2 * BUTTON_WIDTH + 30, font.width(Component.translatable("zocular.hud_editor.hint")) + 24);
		return new int[] {(width - boxWidth) / 2, height / 2 + 24, boxWidth, 53};
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int[] box = toolbarBox();
		int buttonY = box[1] + 31;
		if (inside(mouseX, mouseY, width / 2 - BUTTON_WIDTH - 3, buttonY, BUTTON_WIDTH, 16)) {
			for (Element element : Element.values()) {
				HudLayout.reset(element);
			}
			playClickSound();
			return true;
		}
		if (inside(mouseX, mouseY, width / 2 + 3, buttonY, BUTTON_WIDTH, 16)) {
			playClickSound();
			onClose();
			return true;
		}

		Element element = elementAt(mouseX, mouseY);
		if (element == null) {
			return super.mouseClicked(event, doubleClick);
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			HudLayout.reset(element);
			playClickSound();
		} else {
			int[] at = bounds.get(element);
			dragging = element;
			grabX = (int) mouseX - at[0];
			grabY = (int) mouseY - at[1];
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) {
			return super.mouseDragged(event, dx, dy);
		}
		int[] at = bounds.get(dragging);
		int boxWidth = at[2];
		int boxHeight = at[3];
		int left = (int) event.x() - grabX;
		int top = (int) event.y() - grabY;

		if (Math.abs(left + boxWidth / 2 - width / 2) < SNAP) {
			left = (width - boxWidth) / 2;
		}
		if (Math.abs(top + boxHeight / 2 - height / 2) < SNAP) {
			top = (height - boxHeight) / 2;
		}
		HudLayout.move(dragging, left, top, boxWidth, boxHeight, width, height);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		ZocularConfig.save();
		minecraft.gui.setScreen(parent);
	}

	private @Nullable Element elementAt(double mouseX, double mouseY) {
		Element found = null;
		for (Map.Entry<Element, int[]> entry : bounds.entrySet()) {
			int[] box = entry.getValue();
			if (inside(mouseX, mouseY, box[0] - 3, box[1] - 3, box[2] + 6, box[3] + 6)) {
				found = entry.getKey();
			}
		}
		return found;
	}

	private static boolean isEnabled(Element element, ZocularConfig.Hud hud) {
		return switch (element) {
			case ZOOM -> hud.zoomIndicator;
			case FREECAM -> hud.freecamPanel != PanelMode.HIDDEN;
			case NOTICE -> hud.notices;
			case TIMELINE -> hud.pathTimeline;
		};
	}

	private void playClickSound() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
