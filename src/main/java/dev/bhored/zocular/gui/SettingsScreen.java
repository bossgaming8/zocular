package dev.bhored.zocular.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bhored.zocular.Zocular;
import dev.bhored.zocular.config.Setting;
import dev.bhored.zocular.config.Settings;
import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.util.Motion;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

public final class SettingsScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int HEADING_HEIGHT = 22;
	private static final int CONTROL_WIDTH = 100;
	private static final int FOOTER_BUTTON_WIDTH = 74;

	private static final List<Link> LINKS = List.of(
		new Link("discord", Zocular.id("icon/discord"), URI.create(Zocular.DISCORD_URL)),
		new Link("issues", Zocular.id("icon/bug"), URI.create(Zocular.ISSUES_URL)),
		new Link("donate", Zocular.id("icon/heart"), URI.create(Zocular.DONATE_URL))
	);

	private static int lastPage;

	private final @Nullable Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private final Map<Settings.Entry, Row> rowCache = new HashMap<>();
	private int page;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int sidebarWidth;
	private int contentX;
	private int contentWidth;
	private int listTop;
	private int listBottom;
	private boolean compact;

	private float scroll;
	private float scrollTarget;
	private int contentHeight;
	private boolean draggingScrollbar;
	private @Nullable SliderRow draggingSlider;
	private @Nullable BindingRow listening;
	private @Nullable Row hovered;

	private long pageShownAt;
	private long lastFrame;

	public SettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("zocular.settings.title"));
		this.parent = parent;
		this.page = Mth.clamp(lastPage, 0, Settings.PAGES.size() - 1);
	}

	@Override
	protected void init() {
		layout();
		buildRows();
	}

	@Override
	protected void repositionElements() {
		layout();
		rowCache.clear();
		buildRows();
	}

	private void layout() {
		panelWidth = Math.min(width - 16, 470);
		panelHeight = Math.min(height - 16, 300);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		compact = panelWidth < 380;
		sidebarWidth = compact ? 34 : 120;
		contentX = panelX + sidebarWidth + 12;
		contentWidth = panelWidth - sidebarWidth - 24;
		listTop = panelY + 42;
		listBottom = panelY + panelHeight - 56;
	}

	private void buildRows() {
		rows.clear();
		int y = 0;
		for (Settings.Entry entry : Settings.PAGES.get(page).entries()) {
			if (entry instanceof Setting<?> setting && !setting.isVisible()) {
				continue;
			}
			Row row = rowCache.computeIfAbsent(entry, key -> createRow(key, rows.isEmpty()));
			row.y = y;
			y += row.height();
			rows.add(row);
		}
		contentHeight = y + 4;
		scrollTarget = Mth.clamp(scrollTarget, 0.0F, maxScroll());
		scroll = Mth.clamp(scroll, 0.0F, maxScroll());
	}

	private Row createRow(Settings.Entry entry, boolean first) {
		return switch (entry) {
			case Setting.Toggle toggle -> new ToggleRow(toggle);
			case Setting.Slider slider -> new SliderRow(slider);
			case Setting.Cycle<?> cycle -> new CycleRow(cycle);
			case Settings.Heading heading -> new HeadingRow(heading.text(), first);
			case Settings.Note note -> new NoteRow(note.text());
			case Settings.Binding binding -> new BindingRow(binding.mapping());
		};
	}

	private void switchPage(int index) {
		if (index == page) {
			return;
		}
		page = index;
		lastPage = index;
		rowCache.clear();
		scroll = 0.0F;
		scrollTarget = 0.0F;
		listening = null;
		pageShownAt = System.currentTimeMillis();
		buildRows();
		playClickSound();
	}

	private float maxScroll() {
		return Math.max(0, contentHeight - (listBottom - listTop));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level == null) {
			extractPanorama(graphics, partialTick);
		}
		extractBlurredBackground(graphics);
		graphics.fillGradient(0, 0, width, height, 0x7A08070A, 0xB008070A);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		long now = System.currentTimeMillis();
		float dt = lastFrame == 0L ? 0.0F : Math.min((now - lastFrame) / 1000.0F, 0.1F);
		lastFrame = now;
		scroll += (scrollTarget - scroll) * (float) Motion.follow(0.07, dt);
		if (Math.abs(scrollTarget - scroll) < 0.25F) {
			scroll = scrollTarget;
		}

		Theme.shadow(graphics, panelX, panelY, panelWidth, panelHeight, 0.75F);
		Theme.box(graphics, panelX, panelY, panelWidth, panelHeight, Theme.WINDOW);
		Theme.outline(graphics, panelX, panelY, panelWidth, panelHeight, Theme.BORDER);

		drawSidebar(graphics, mouseX, mouseY);
		drawHeader(graphics, mouseX, mouseY);
		drawList(graphics, mouseX, mouseY, dt);
		drawDescription(graphics);
		drawFooter(graphics, mouseX, mouseY);
	}

	private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Theme.box(graphics, panelX + 3, panelY + 3, sidebarWidth - 3, panelHeight - 6, Theme.SIDEBAR);

		int itemY = panelY + 12;
		if (compact) {
			Theme.logoMark(graphics, panelX + 10, itemY - 2, 18);
			itemY += 24;
		} else {
			Theme.logo(graphics, panelX + 12, itemY, sidebarWidth - 22, 1.0F);
			itemY += 34;
		}

		for (int i = 0; i < Settings.PAGES.size(); i++) {
			Settings.Page entry = Settings.PAGES.get(i);
			int x = panelX + 7;
			int w = sidebarWidth - 11;
			boolean selected = i == page;
			boolean hover = inside(mouseX, mouseY, x, itemY, w, 20);
			if (selected) {
				Theme.box(graphics, x, itemY, w, 20, Theme.SURFACE_ACTIVE);
				graphics.fill(x, itemY + 5, x + 2, itemY + 15, Theme.ACCENT);
			} else if (hover) {
				Theme.box(graphics, x, itemY, w, 20, Theme.SURFACE);
			}

			int iconColor = selected ? Theme.ACCENT : hover ? Theme.TEXT : Theme.TEXT_MUTED;
			Theme.sprite(graphics, entry.icon(), x + (compact ? 6 : 8), itemY + 4, 12, 12, iconColor);
			if (!compact) {
				graphics.text(font, entry.title(), x + 26, itemY + 6, selected || hover ? Theme.TEXT : Theme.TEXT_MUTED, false);
			} else if (hover) {
				graphics.setTooltipForNextFrame(font, entry.title(), mouseX, mouseY);
			}
			itemY += 22;
		}

		if (!linksVisible()) {
			return;
		}
		int linkY = panelY + panelHeight - LINKS.size() * 13 - 8;
		for (Link link : LINKS) {
			boolean hover = inside(mouseX, mouseY, panelX + 10, linkY - 2, sidebarWidth - 20, 12);
			int color = hover ? Theme.TEXT : Theme.TEXT_FAINT;
			Theme.sprite(graphics, link.icon(), panelX + 13, linkY, 9, 9, hover ? Theme.ACCENT : Theme.TEXT_FAINT);
			graphics.text(font, link.label(), panelX + 26, linkY + 1, color, false);
			linkY += 13;
		}
	}

	private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Settings.Page current = Settings.PAGES.get(page);
		graphics.text(font, current.title(), contentX, panelY + 12, Theme.TEXT, true);
		String version = "v" + Zocular.version();
		graphics.text(font, version, closeX() - 8 - font.width(version), panelY + 12, Theme.TEXT_FAINT, false);
		graphics.text(font, trim(current.subtitle(), contentWidth), contentX, panelY + 25, Theme.TEXT_MUTED, false);

		boolean hover = inside(mouseX, mouseY, closeX(), panelY + 8, 14, 14);
		if (hover) {
			Theme.box(graphics, closeX(), panelY + 8, 14, 14, Theme.SURFACE_HOVER);
		}
		Theme.sprite(graphics, Zocular.id("icon/close"), closeX() + 3, panelY + 11, 8, 8, hover ? Theme.TEXT : Theme.TEXT_MUTED);
		graphics.fill(contentX, listTop - 4, contentX + contentWidth, listTop - 3, Theme.BORDER);
	}

	private boolean linksVisible() {
		int pagesEnd = panelY + 46 + Settings.PAGES.size() * 22;
		return !compact && panelY + panelHeight - LINKS.size() * 13 - 12 > pagesEnd;
	}

	private int closeX() {
		return panelX + panelWidth - 22;
	}

	private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float dt) {
		float appear = Motion.easeOut(Math.min(1.0F, (System.currentTimeMillis() - pageShownAt) / 180.0F));
		int offset = Math.round((1.0F - appear) * 6.0F);
		boolean mouseInList = mouseY >= listTop && mouseY < listBottom && mouseX >= contentX && mouseX < contentX + contentWidth;

		hovered = null;
		graphics.enableScissor(contentX - 2, listTop, contentX + contentWidth + 2, listBottom);
		for (Row row : rows) {
			int y = listTop + row.y - Math.round(scroll) + offset;
			if (y + row.height() < listTop || y > listBottom) {
				continue;
			}
			boolean hover = mouseInList && listening == null && mouseY >= y && mouseY < y + row.height() && row.interactive();
			if (hover) {
				hovered = row;
			}
			row.hover += ((hover ? 1.0F : 0.0F) - row.hover) * (float) Motion.follow(0.05, dt);
			row.draw(graphics, contentX, y, rowWidth(), mouseX, mouseY, appear, dt);
		}
		graphics.disableScissor();

		if (maxScroll() > 0) {
			int trackHeight = listBottom - listTop;
			int thumb = Math.max(18, Math.round(trackHeight * trackHeight / (float) contentHeight));
			int thumbY = listTop + Math.round((trackHeight - thumb) * (scroll / maxScroll()));
			int x = contentX + contentWidth - 3;
			boolean hover = draggingScrollbar || inside(mouseX, mouseY, x - 2, listTop, 6, trackHeight);
			graphics.fill(x, listTop, x + 2, listBottom, 0x14FFFFFF);
			graphics.fill(x, thumbY, x + 2, thumbY + thumb, hover ? Theme.ACCENT : Theme.TEXT_FAINT);
		}
	}

	private int rowWidth() {
		return contentWidth - (maxScroll() > 0 ? 8 : 0);
	}

	private void drawDescription(GuiGraphicsExtractor graphics) {
		int y = listBottom + 5;
		graphics.fill(contentX, y - 3, contentX + contentWidth, y - 2, Theme.BORDER);
		Component text;
		int color = Theme.TEXT_MUTED;
		if (listening != null) {
			text = Component.translatable("zocular.settings.press_key");
		} else if (hovered != null && hovered.description() != null) {
			text = hovered.description();
		} else {
			text = Component.translatable("zocular.settings.hint");
			color = Theme.TEXT_FAINT;
		}
		List<FormattedCharSequence> lines = font.split(text, contentWidth);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			graphics.text(font, lines.get(i), contentX, y + 2 + i * 10, color, false);
		}
	}

	private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int y = panelY + panelHeight - 26;
		boolean resetHover = inside(mouseX, mouseY, contentX, y, FOOTER_BUTTON_WIDTH, 18);
		Theme.box(graphics, contentX, y, FOOTER_BUTTON_WIDTH, 18, resetHover ? Theme.SURFACE_ACTIVE : Theme.SURFACE);
		graphics.centeredText(font, Component.translatable("zocular.settings.reset_page"), contentX + FOOTER_BUTTON_WIDTH / 2, y + 5, resetHover ? Theme.TEXT : Theme.TEXT_MUTED);

		int doneX = contentX + contentWidth - FOOTER_BUTTON_WIDTH;
		boolean doneHover = inside(mouseX, mouseY, doneX, y, FOOTER_BUTTON_WIDTH, 18);
		Theme.box(graphics, doneX, y, FOOTER_BUTTON_WIDTH, 18, doneHover ? Theme.ACCENT_BRIGHT : Theme.ACCENT);
		graphics.centeredText(font, Component.translatable("gui.done"), doneX + FOOTER_BUTTON_WIDTH / 2, y + 5, Theme.ON_ACCENT);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();

		if (listening != null) {
			listening.assign(InputConstants.Type.MOUSE.getOrCreate(event.button()));
			listening = null;
			return true;
		}

		int footerY = panelY + panelHeight - 26;
		if (inside(mouseX, mouseY, closeX(), panelY + 8, 14, 14)
				|| inside(mouseX, mouseY, contentX + contentWidth - FOOTER_BUTTON_WIDTH, footerY, FOOTER_BUTTON_WIDTH, 18)) {
			playClickSound();
			onClose();
			return true;
		}
		if (inside(mouseX, mouseY, contentX, footerY, FOOTER_BUTTON_WIDTH, 18)) {
			resetPage();
			return true;
		}

		int itemY = panelY + (compact ? 36 : 46);
		for (int i = 0; i < Settings.PAGES.size(); i++) {
			if (inside(mouseX, mouseY, panelX + 7, itemY, sidebarWidth - 11, 20)) {
				switchPage(i);
				return true;
			}
			itemY += 22;
		}

		if (linksVisible()) {
			int linkY = panelY + panelHeight - LINKS.size() * 13 - 8;
			for (Link link : LINKS) {
				if (inside(mouseX, mouseY, panelX + 10, linkY - 2, sidebarWidth - 20, 12)) {
					playClickSound();
					ConfirmLinkScreen.confirmLinkNow(this, link.uri());
					return true;
				}
				linkY += 13;
			}
		}

		if (maxScroll() > 0 && inside(mouseX, mouseY, contentX + contentWidth - 5, listTop, 6, listBottom - listTop)) {
			draggingScrollbar = true;
			scrollToMouse(mouseY);
			return true;
		}

		if (hovered != null && mouseY >= listTop && mouseY < listBottom) {
			int rowY = listTop + hovered.y - Math.round(scroll);
			if (hovered.click(mouseX, mouseY, contentX, rowY, rowWidth(), event.button())) {
				buildRows();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingScrollbar) {
			scrollToMouse(event.y());
			return true;
		}
		if (draggingSlider != null) {
			draggingSlider.dragTo(event.x(), contentX, rowWidth());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingScrollbar = false;
		draggingSlider = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollTarget = Mth.clamp(scrollTarget - (float) scrollY * 22.0F, 0.0F, maxScroll());
		return true;
	}

	private void scrollToMouse(double mouseY) {
		float progress = (float) ((mouseY - listTop) / (listBottom - listTop));
		scrollTarget = Mth.clamp(progress * maxScroll(), 0.0F, maxScroll());
		scroll = scrollTarget;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listening != null) {
			listening.assign(event.isEscape() ? InputConstants.UNKNOWN : InputConstants.getKey(event));
			listening = null;
			return true;
		}
		if (hovered instanceof SliderRow slider) {
			if (event.key() == InputConstants.KEY_LEFT || event.key() == InputConstants.KEY_RIGHT) {
				slider.setting.nudge(event.key() == InputConstants.KEY_RIGHT ? 1 : -1);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private void resetPage() {
		for (Settings.Entry entry : Settings.PAGES.get(page).entries()) {
			if (entry instanceof Setting<?> setting) {
				setting.reset();
			} else if (entry instanceof Settings.Binding binding) {
				binding.mapping().setKey(binding.mapping().getDefaultKey());
			}
		}
		KeyMapping.resetMapping();
		buildRows();
		playClickSound();
	}

	@Override
	public void onClose() {
		ZocularConfig.save();
		minecraft.options.save();
		minecraft.gui.setScreen(parent);
	}

	private void playClickSound() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private String trim(Component text, int width) {
		String plain = text.getString();
		return font.width(plain) <= width ? plain : font.plainSubstrByWidth(plain, width - font.width("...")) + "...";
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private record Link(String key, Identifier icon, URI uri) {
		Component label() {
			return Component.translatable("zocular.link." + key);
		}
	}

	private abstract class Row {
		int y;
		float hover;

		abstract int height();

		boolean interactive() {
			return true;
		}

		@Nullable Component description() {
			return null;
		}

		abstract void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int mouseX, int mouseY, float alpha, float dt);

		boolean click(double mouseX, double mouseY, int x, int y, int width, int button) {
			return false;
		}

		void background(GuiGraphicsExtractor graphics, int x, int y, int width, float alpha) {
			if (hover > 0.01F) {
				Theme.box(graphics, x, y + 1, width, height() - 2, Theme.fade(Theme.SURFACE_HOVER, hover * alpha));
			}
		}
	}

	private final class HeadingRow extends Row {
		private final Component text;
		private final boolean first;

		HeadingRow(Component text, boolean first) {
			this.text = text;
			this.first = first;
		}

		@Override
		int height() {
			return first ? HEADING_HEIGHT - 6 : HEADING_HEIGHT;
		}

		@Override
		boolean interactive() {
			return false;
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int mouseX, int mouseY, float alpha, float dt) {
			int textY = y + height() - 12;
			String label = text.getString().toUpperCase(Locale.ROOT);
			graphics.text(font, label, x + 2, textY, Theme.fade(Theme.ACCENT, alpha), false);
			int lineX = x + font.width(label) + 8;
			graphics.fill(lineX, textY + 4, x + width, textY + 5, Theme.fade(Theme.BORDER, alpha));
		}
	}

	private final class NoteRow extends Row {
		private final List<FormattedCharSequence> lines;

		NoteRow(Component text) {
			this.lines = font.split(text, Math.max(40, contentWidth - 24));
		}

		@Override
		int height() {
			return lines.size() * 10 + 12;
		}

		@Override
		boolean interactive() {
			return false;
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int mouseX, int mouseY, float alpha, float dt) {
			Theme.box(graphics, x, y + 4, width, height() - 6, Theme.fade(Theme.SURFACE, alpha));
			graphics.fill(x, y + 7, x + 2, y + height() - 5, Theme.fade(Theme.ACCENT, alpha));
			int lineY = y + 9;
			for (FormattedCharSequence line : lines) {
				graphics.text(font, line, x + 10, lineY, Theme.fade(Theme.TEXT_MUTED, alpha), false);
				lineY += 10;
			}
		}
	}

	private abstract class SettingRow<S extends Setting<?>> extends Row {
		final S setting;

		SettingRow(S setting) {
			this.setting = setting;
		}

		@Override
		int height() {
			return ROW_HEIGHT;
		}

		@Override
		Component description() {
			return setting.description();
		}

		int controlX(int x, int width) {
			return x + width - CONTROL_WIDTH - 6;
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int mouseX, int mouseY, float alpha, float dt) {
			background(graphics, x, y, width, alpha);
			if (!setting.isDefault()) {
				graphics.fill(x + 3, y + 10, x + 5, y + 14, Theme.fade(Theme.ACCENT, alpha));
			}
			graphics.text(font, setting.name(), x + 9, y + 8, Theme.fade(Theme.TEXT, alpha), false);

			if (!setting.isDefault() && hover > 0.5F) {
				int resetX = controlX(x, width) - 17;
				boolean resetHover = inside(mouseX, mouseY, resetX, y + 5, 14, 14);
				Theme.sprite(graphics, Zocular.id("icon/reset"), resetX + 2, y + 7, 9, 9,
					Theme.fade(resetHover ? Theme.ACCENT : Theme.TEXT_MUTED, alpha));
			}
			drawControl(graphics, controlX(x, width), y, mouseX, mouseY, alpha, dt);
		}

		abstract void drawControl(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY, float alpha, float dt);

		@Override
		boolean click(double mouseX, double mouseY, int x, int y, int width, int button) {
			int resetX = controlX(x, width) - 17;
			if (!setting.isDefault() && inside(mouseX, mouseY, resetX, y + 5, 14, 14)) {
				setting.reset();
				playClickSound();
				return true;
			}
			return clickControl(mouseX, mouseY, controlX(x, width), y, button);
		}

		abstract boolean clickControl(double mouseX, double mouseY, int x, int y, int button);
	}

	private final class ToggleRow extends SettingRow<Setting.Toggle> {
		private float knob;

		ToggleRow(Setting.Toggle setting) {
			super(setting);
			this.knob = setting.get() ? 1.0F : 0.0F;
		}

		@Override
		void drawControl(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY, float alpha, float dt) {
			float target = setting.get() ? 1.0F : 0.0F;
			knob += (target - knob) * (float) Motion.follow(0.035, dt);
			int trackX = x + CONTROL_WIDTH - 20;
			int trackY = y + 7;
			Theme.sprite(graphics, Theme.TOGGLE, trackX, trackY, 20, 10, Theme.fade(Theme.mix(Theme.TRACK, Theme.ACCENT, knob), alpha));
			int knobX = trackX + 1 + Math.round(knob * 10.0F);
			Theme.sprite(graphics, Theme.KNOB, knobX, trackY + 1, 8, 8, Theme.fade(Theme.mix(Theme.TEXT_MUTED, Theme.TEXT, knob), alpha));

			Component state = Component.translatable(setting.get() ? "options.on" : "options.off");
			graphics.text(font, state, trackX - 6 - font.width(state), y + 8, Theme.fade(setting.get() ? Theme.TEXT : Theme.TEXT_FAINT, alpha), false);
		}

		@Override
		boolean clickControl(double mouseX, double mouseY, int x, int y, int button) {
			setting.flip();
			playClickSound();
			return true;
		}
	}

	private final class SliderRow extends SettingRow<Setting.Slider> {
		private static final int TRACK_WIDTH = 58;

		SliderRow(Setting.Slider setting) {
			super(setting);
		}

		private int trackX(int controlX) {
			return controlX + CONTROL_WIDTH - TRACK_WIDTH - 4;
		}

		@Override
		void drawControl(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY, float alpha, float dt) {
			int trackX = trackX(x);
			int trackY = y + 10;
			float progress = setting.progress();
			boolean active = draggingSlider == this;

			Theme.sprite(graphics, Theme.TRACK_SPRITE, trackX, trackY, TRACK_WIDTH, 4, Theme.fade(Theme.TRACK, alpha));
			int filled = Math.round(TRACK_WIDTH * progress);
			if (filled >= 4) {
				Theme.sprite(graphics, Theme.TRACK_SPRITE, trackX, trackY, filled, 4, Theme.fade(Theme.ACCENT, alpha));
			}
			int knobX = trackX + Math.round((TRACK_WIDTH - 8) * progress);
			Theme.sprite(graphics, Theme.KNOB, knobX, trackY - 2, 8, 8, Theme.fade(active ? Theme.ACCENT_BRIGHT : Theme.TEXT, alpha));

			Component value = setting.display();
			graphics.text(font, value, trackX - 6 - font.width(value), y + 8, Theme.fade(active ? Theme.ACCENT : Theme.TEXT_MUTED, alpha), false);
		}

		@Override
		boolean clickControl(double mouseX, double mouseY, int x, int y, int button) {
			int trackX = trackX(x);
			if (mouseX < trackX - 4) {
				return false;
			}
			draggingSlider = this;
			setting.setProgress((mouseX - trackX - 4) / (TRACK_WIDTH - 8));
			return true;
		}

		void dragTo(double mouseX, int rowX, int rowWidth) {
			int trackX = trackX(controlX(rowX, rowWidth));
			setting.setProgress((mouseX - trackX - 4) / (TRACK_WIDTH - 8));
		}
	}

	private final class CycleRow extends SettingRow<Setting.Cycle<?>> {
		CycleRow(Setting.Cycle<?> setting) {
			super(setting);
		}

		@Override
		void drawControl(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY, float alpha, float dt) {
			boolean hover = inside(mouseX, mouseY, x, y + 4, CONTROL_WIDTH, 16);
			Theme.box(graphics, x, y + 4, CONTROL_WIDTH, 16, Theme.fade(hover ? Theme.SURFACE_ACTIVE : Theme.SURFACE, alpha));
			int arrowColor = Theme.fade(hover ? Theme.ACCENT : Theme.TEXT_FAINT, alpha);
			graphics.text(font, "‹", x + 6, y + 8, arrowColor, false);
			graphics.text(font, "›", x + CONTROL_WIDTH - 9, y + 8, arrowColor, false);
			graphics.centeredText(font, setting.display(), x + CONTROL_WIDTH / 2, y + 8, Theme.fade(Theme.TEXT, alpha));
		}

		@Override
		boolean clickControl(double mouseX, double mouseY, int x, int y, int button) {
			boolean onLeftArrow = mouseX >= x && mouseX < x + CONTROL_WIDTH / 3.0;
			boolean backwards = button == InputConstants.MOUSE_BUTTON_RIGHT || onLeftArrow;
			setting.cycle(backwards ? -1 : 1);
			playClickSound();
			return true;
		}
	}

	private final class BindingRow extends Row {
		private final KeyMapping mapping;

		BindingRow(KeyMapping mapping) {
			this.mapping = mapping;
		}

		@Override
		int height() {
			return ROW_HEIGHT;
		}

		@Override
		Component description() {
			return Component.translatable(mapping.getName() + ".desc");
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int mouseX, int mouseY, float alpha, float dt) {
			background(graphics, x, y, width, alpha);
			if (!mapping.isDefault()) {
				graphics.fill(x + 3, y + 10, x + 5, y + 14, Theme.fade(Theme.ACCENT, alpha));
			}
			graphics.text(font, Component.translatable(mapping.getName()), x + 9, y + 8, Theme.fade(Theme.TEXT, alpha), false);

			int controlX = x + width - CONTROL_WIDTH - 6;
			boolean isListening = listening == this;
			boolean hoverButton = inside(mouseX, mouseY, controlX, y + 4, CONTROL_WIDTH, 16);
			Theme.box(graphics, controlX, y + 4, CONTROL_WIDTH, 16,
				Theme.fade(isListening ? Theme.SURFACE_ACTIVE : hoverButton ? Theme.SURFACE_ACTIVE : Theme.SURFACE, alpha));
			if (isListening) {
				Theme.outline(graphics, controlX, y + 4, CONTROL_WIDTH, 16, Theme.fade(Theme.ACCENT, alpha));
			}

			Component label;
			int color;
			if (isListening) {
				label = Component.literal("> ").append(mapping.getTranslatedKeyMessage()).append(" <");
				color = Theme.ACCENT;
			} else if (mapping.isUnbound()) {
				label = Component.translatable("zocular.settings.unbound");
				color = Theme.TEXT_FAINT;
			} else {
				label = mapping.getTranslatedKeyMessage();
				color = conflicts() ? Theme.WARNING : Theme.TEXT;
			}
			graphics.centeredText(font, label, controlX + CONTROL_WIDTH / 2, y + 8, Theme.fade(color, alpha));

			if (!mapping.isDefault() && hover > 0.5F) {
				int resetX = controlX - 17;
				boolean resetHover = inside(mouseX, mouseY, resetX, y + 5, 14, 14);
				Theme.sprite(graphics, Zocular.id("icon/reset"), resetX + 2, y + 7, 9, 9,
					Theme.fade(resetHover ? Theme.ACCENT : Theme.TEXT_MUTED, alpha));
			}
		}

		private boolean conflicts() {
			// Same rule as the vanilla controls screen: two bindings that both still use their default key are
			// intended to share it (debug combos such as F3 + I, for example).
			for (KeyMapping other : minecraft.options.keyMappings) {
				if (other != mapping && mapping.same(other) && (!other.isDefault() || !mapping.isDefault())) {
					return true;
				}
			}
			return false;
		}

		@Override
		boolean click(double mouseX, double mouseY, int x, int y, int width, int button) {
			int controlX = x + width - CONTROL_WIDTH - 6;
			if (!mapping.isDefault() && inside(mouseX, mouseY, controlX - 17, y + 5, 14, 14)) {
				assign(mapping.getDefaultKey());
				playClickSound();
				return true;
			}
			if (inside(mouseX, mouseY, controlX, y + 4, CONTROL_WIDTH, 16)) {
				listening = this;
				playClickSound();
				return true;
			}
			return false;
		}

		void assign(InputConstants.Key key) {
			mapping.setKey(key);
			KeyMapping.resetMapping();
		}
	}
}
