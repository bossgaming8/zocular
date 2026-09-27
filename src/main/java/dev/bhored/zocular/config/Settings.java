package dev.bhored.zocular.config;

import dev.bhored.zocular.Keybinds;
import dev.bhored.zocular.Zocular;
import dev.bhored.zocular.compat.IrisShaders;
import dev.bhored.zocular.gui.HudEditorScreen;
import dev.bhored.zocular.config.ZocularConfig.Labeled;
import dev.bhored.zocular.config.ZocularConfig.PanelMode;
import dev.bhored.zocular.config.ZocularConfig.Reframe;
import dev.bhored.zocular.config.ZocularConfig.ShaderMode;
import dev.bhored.zocular.config.ZocularConfig.ShotStyle;
import dev.bhored.zocular.config.ZocularConfig.Shoulder;
import dev.bhored.zocular.config.ZocularConfig.Vignette;
import dev.bhored.zocular.config.ZocularConfig.ZoomMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Everything the settings screen shows, grouped into pages. The order here is the order on screen.
 */
public final class Settings {
	public sealed interface Entry permits Setting, Heading, Note, Binding, Action {
	}

	public record Heading(String key) implements Entry {
		public Component text() {
			return Component.translatable("zocular.heading." + key);
		}
	}

	public record Note(String key) implements Entry {
		public Component text() {
			return Component.translatable("zocular.note." + key);
		}
	}

	public record Binding(KeyMapping mapping) implements Entry {
	}

	/** A button that opens another screen, given the settings screen to return to. */
	public record Action(String key, Function<Screen, Screen> screen) implements Entry {
		public Component name() {
			return Component.translatable("zocular.action." + key);
		}

		public Component description() {
			return Component.translatable("zocular.action." + key + ".desc");
		}
	}

	public record Page(String id, Identifier icon, List<Entry> entries) {
		public Component title() {
			return Component.translatable("zocular.page." + id);
		}

		public Component subtitle() {
			return Component.translatable("zocular.page." + id + ".subtitle");
		}
	}

	public static final List<Page> PAGES = List.of(zoom(), cinematic(), freecam(), path(), camera(), hud(), controls());

	private Settings() {
	}

	static void sanitize() {
		for (Page page : PAGES) {
			for (Entry entry : page.entries()) {
				if (entry instanceof Setting<?> setting) {
					setting.sanitize();
				}
			}
		}
	}

	private static Page zoom() {
		return new Page("zoom", Zocular.id("icon/zoom"), List.of(
			new Heading("zoom.behavior"),
			toggle("zoom.enabled", c -> c.zoom.enabled, (c, v) -> c.zoom.enabled = v),
			cycle("zoom.mode", ZoomMode.values(), c -> c.zoom.mode, (c, v) -> c.zoom.mode = v),
			slider("zoom.start_level", 1.5F, 16.0F, 0.5F, format("times", "%.1f"), c -> c.zoom.startLevel, (c, v) -> c.zoom.startLevel = v),
			slider("zoom.max_level", 8.0F, 100.0F, 1.0F, format("times", "%.0f"), c -> c.zoom.maxLevel, (c, v) -> c.zoom.maxLevel = v),
			toggle("zoom.scroll", c -> c.zoom.scrollToZoom, (c, v) -> c.zoom.scrollToZoom = v),
			toggle("zoom.remember_level", c -> c.zoom.rememberLevel, (c, v) -> c.zoom.rememberLevel = v),
			new Heading("zoom.feel"),
			slider("zoom.animation", 0.0F, 600.0F, 10.0F, millis(), c -> c.zoom.animationMs, (c, v) -> c.zoom.animationMs = v),
			toggle("zoom.match_mouse_speed", c -> c.zoom.matchMouseSpeed, (c, v) -> c.zoom.matchMouseSpeed = v),
			slider("zoom.look_smoothing", 0.0F, 1.0F, 0.05F, percent(true), c -> c.zoom.lookSmoothing, (c, v) -> c.zoom.lookSmoothing = v),
			toggle("zoom.hide_hand", c -> c.zoom.hideHand, (c, v) -> c.zoom.hideHand = v)
		));
	}

	private static Page cinematic() {
		return new Page("cinematic", Zocular.id("icon/cinematic"), List.of(
			new Heading("cinematic.shot"),
			cycle("cinematic.style", ShotStyle.values(), c -> c.cinematic.style, (c, v) -> c.cinematic.style = v),
			slider("cinematic.shot_length", 3.0F, 30.0F, 1.0F, format("seconds", "%.0f"), c -> c.cinematic.shotLength, (c, v) -> c.cinematic.shotLength = v)
				.visibleWhen(() -> ZocularConfig.get().cinematic.style == ShotStyle.AUTO),
			cycle("cinematic.reframe", Reframe.values(), c -> c.cinematic.reframe, (c, v) -> c.cinematic.reframe = v)
				.visibleWhen(() -> ZocularConfig.get().cinematic.style == ShotStyle.AUTO || ZocularConfig.get().cinematic.style == ShotStyle.TRIPOD),
			slider("cinematic.distance", 3.0F, 24.0F, 0.5F, format("blocks", "%.1f"), c -> c.cinematic.distance, (c, v) -> c.cinematic.distance = v),
			slider("cinematic.height", -2.0F, 8.0F, 0.25F, format("blocks", "%.2f"), c -> c.cinematic.height, (c, v) -> c.cinematic.height = v),
			slider("cinematic.fov", 30.0F, 90.0F, 1.0F, format("degrees", "%.0f"), c -> c.cinematic.fov, (c, v) -> c.cinematic.fov = v),
			slider("cinematic.orbit_speed", 1.0F, 45.0F, 1.0F, format("degrees_per_second", "%.0f"), c -> c.cinematic.orbitSpeed, (c, v) -> c.cinematic.orbitSpeed = v)
				.visibleWhen(() -> ZocularConfig.get().cinematic.style == ShotStyle.ORBIT),
			slider("cinematic.follow_smoothing", 0.0F, 1.0F, 0.05F, percent(false), c -> c.cinematic.followSmoothing, (c, v) -> c.cinematic.followSmoothing = v)
				.visibleWhen(() -> ZocularConfig.get().cinematic.style != ShotStyle.TRIPOD),
			slider("cinematic.reframe_angle", 10.0F, 60.0F, 1.0F, format("degrees", "%.0f"), c -> c.cinematic.reframeAngle, (c, v) -> c.cinematic.reframeAngle = v)
				.visibleWhen(() -> ZocularConfig.get().cinematic.style == ShotStyle.TRIPOD),
			new Heading("cinematic.look"),
			slider("cinematic.transition", 0.0F, 3000.0F, 50.0F, millis(), c -> c.cinematic.transitionMs, (c, v) -> c.cinematic.transitionMs = v),
			slider("cinematic.letterbox", 0.0F, 0.25F, 0.01F, percent(true), c -> c.cinematic.letterbox, (c, v) -> c.cinematic.letterbox = v),
			cycle("cinematic.vignette", Vignette.values(), c -> c.cinematic.vignette, (c, v) -> c.cinematic.vignette = v),
			toggle("cinematic.hide_hud", c -> c.cinematic.hideHud, (c, v) -> c.cinematic.hideHud = v),
			slider("cinematic.look_sensitivity", 0.1F, 1.0F, 0.05F, percent(false), c -> c.cinematic.lookSensitivity, (c, v) -> c.cinematic.lookSensitivity = v),
			cycle("cinematic.shaders", ShaderMode.values(), c -> c.cinematic.shaders, (c, v) -> c.cinematic.shaders = v)
				.visibleWhen(IrisShaders::isInstalled)
		));
	}

	private static Page freecam() {
		return new Page("freecam", Zocular.id("icon/freecam"), List.of(
			new Heading("freecam.movement"),
			slider("freecam.speed", 1.0F, 100.0F, 0.5F, format("speed", "%.1f"), c -> c.freecam.speed, (c, v) -> c.freecam.speed = v),
			slider("freecam.boost", 1.5F, 10.0F, 0.5F, format("times", "%.1f"), c -> c.freecam.boost, (c, v) -> c.freecam.boost = v),
			slider("freecam.smoothing", 0.0F, 1.0F, 0.05F, percent(true), c -> c.freecam.smoothing, (c, v) -> c.freecam.smoothing = v),
			toggle("freecam.fly_where_looking", c -> c.freecam.flyWhereLooking, (c, v) -> c.freecam.flyWhereLooking = v),
			toggle("freecam.noclip", c -> c.freecam.noclip, (c, v) -> c.freecam.noclip = v),
			new Heading("freecam.safety"),
			toggle("freecam.exit_when_hurt", c -> c.freecam.exitWhenHurt, (c, v) -> c.freecam.exitWhenHurt = v),
			toggle("freecam.show_name_tag", c -> c.freecam.showNameTag, (c, v) -> c.freecam.showNameTag = v)
		));
	}

	private static Page path() {
		return new Page("path", Zocular.id("icon/path"), List.of(
			new Heading("path.playback"),
			slider("path.duration", 2.0F, 120.0F, 1.0F, format("seconds", "%.0f"), c -> c.path.duration, (c, v) -> c.path.duration = v),
			toggle("path.constant_speed", c -> c.path.constantSpeed, (c, v) -> c.path.constantSpeed = v),
			toggle("path.ease", c -> c.path.ease, (c, v) -> c.path.ease = v),
			toggle("path.loop", c -> c.path.loop, (c, v) -> c.path.loop = v),
			new Heading("path.presentation"),
			toggle("path.hide_hud", c -> c.path.hideHud, (c, v) -> c.path.hideHud = v),
			toggle("path.letterbox", c -> c.path.letterbox, (c, v) -> c.path.letterbox = v),
			new Note("path.how_to")
		));
	}

	private static Page camera() {
		return new Page("camera", Zocular.id("icon/camera"), List.of(
			new Heading("camera.third_person"),
			slider("third_person.distance", 0.25F, 4.0F, 0.05F, percent(false), c -> c.thirdPerson.distance, (c, v) -> c.thirdPerson.distance = v),
			cycle("third_person.shoulder", Shoulder.values(), c -> c.thirdPerson.shoulder, (c, v) -> c.thirdPerson.shoulder = v),
			slider("third_person.shoulder_offset", 0.25F, 1.5F, 0.05F, format("blocks", "%.2f"), c -> c.thirdPerson.shoulderOffset, (c, v) -> c.thirdPerson.shoulderOffset = v),
			new Heading("camera.roll"),
			slider("roll.speed", 5.0F, 180.0F, 5.0F, format("degrees_per_second", "%.0f"), c -> c.roll.speed, (c, v) -> c.roll.speed = v),
			slider("roll.max", 5.0F, 180.0F, 5.0F, format("degrees", "%.0f"), c -> c.roll.max, (c, v) -> c.roll.max = v),
			new Note("camera.roll")
		));
	}

	private static Page hud() {
		return new Page("hud", Zocular.id("icon/interface"), List.of(
			new Heading("hud.overlays"),
			toggle("hud.zoom_indicator", c -> c.hud.zoomIndicator, (c, v) -> c.hud.zoomIndicator = v),
			cycle("hud.freecam_panel", PanelMode.values(), c -> c.hud.freecamPanel, (c, v) -> c.hud.freecamPanel = v),
			toggle("hud.path_timeline", c -> c.hud.pathTimeline, (c, v) -> c.hud.pathTimeline = v),
			toggle("hud.notices", c -> c.hud.notices, (c, v) -> c.hud.notices = v),
			new Heading("hud.layout"),
			new Action("hud_editor", HudEditorScreen::new),
			new Note("hud.layout")
		));
	}

	private static Page controls() {
		List<Entry> entries = new ArrayList<>();
		entries.add(new Heading("controls.keys"));
		for (KeyMapping mapping : Keybinds.ALL) {
			entries.add(new Binding(mapping));
		}
		entries.add(new Heading("controls.freecam"));
		entries.add(new Note("controls.freecam"));
		return new Page("controls", Zocular.id("icon/controls"), List.copyOf(entries));
	}

	private static Setting.Toggle toggle(String key, Function<ZocularConfig, Boolean> getter, BiConsumer<ZocularConfig, Boolean> setter) {
		return new Setting.Toggle(key, getter, setter);
	}

	private static Setting.Slider slider(String key, float min, float max, float step, Function<Float, Component> format,
			Function<ZocularConfig, Float> getter, BiConsumer<ZocularConfig, Float> setter) {
		return new Setting.Slider(key, min, max, step, format, getter, setter);
	}

	private static <E extends Enum<E> & Labeled> Setting.Cycle<E> cycle(String key, E[] values,
			Function<ZocularConfig, E> getter, BiConsumer<ZocularConfig, E> setter) {
		return new Setting.Cycle<>(key, values, getter, setter);
	}

	private static Function<Float, Component> format(String unit, String pattern) {
		return value -> Component.translatable("zocular.unit." + unit, String.format(Locale.ROOT, pattern, value));
	}

	private static Function<Float, Component> millis() {
		return value -> value <= 0.0F
			? Component.translatable("zocular.value.instant")
			: Component.translatable("zocular.unit.ms", String.format(Locale.ROOT, "%.0f", value));
	}

	private static Function<Float, Component> percent(boolean zeroIsOff) {
		return value -> zeroIsOff && value <= 0.0F
			? Component.translatable("zocular.value.off")
			: Component.translatable("zocular.unit.percent", Math.round(value * 100.0F));
	}
}
