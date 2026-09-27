package dev.bhored.zocular.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.bhored.zocular.Zocular;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

public final class ZocularConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("zocular.json");

	public static final ZocularConfig DEFAULTS = new ZocularConfig();
	private static ZocularConfig current = new ZocularConfig();

	public Zoom zoom = new Zoom();
	public Cinematic cinematic = new Cinematic();
	public Freecam freecam = new Freecam();
	public CameraPath path = new CameraPath();
	public ThirdPerson thirdPerson = new ThirdPerson();
	public Roll roll = new Roll();
	public Hud hud = new Hud();

	public static ZocularConfig get() {
		return current;
	}

	public static void load() {
		if (Files.isRegularFile(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				ZocularConfig loaded = GSON.fromJson(reader, ZocularConfig.class);
				if (loaded != null) {
					current = loaded;
				}
			} catch (IOException | JsonParseException e) {
				Zocular.LOGGER.warn("Could not read {}, falling back to defaults", FILE, e);
			}
		}

		current.fillMissingSections();
		Settings.sanitize();
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temp)) {
				GSON.toJson(current, writer);
			}
			Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Zocular.LOGGER.warn("Could not save {}", FILE, e);
		}
	}

	private void fillMissingSections() {
		if (zoom == null) zoom = new Zoom();
		if (cinematic == null) cinematic = new Cinematic();
		if (freecam == null) freecam = new Freecam();
		if (path == null) path = new CameraPath();
		if (thirdPerson == null) thirdPerson = new ThirdPerson();
		if (roll == null) roll = new Roll();
		if (hud == null) hud = new Hud();
	}

	public static final class Zoom {
		public boolean enabled = true;
		public ZoomMode mode = ZoomMode.HOLD;
		public float startLevel = 4.0F;
		public float maxLevel = 50.0F;
		public boolean scrollToZoom = true;
		public boolean rememberLevel = false;
		public float animationMs = 200.0F;
		public boolean matchMouseSpeed = true;
		public float lookSmoothing = 0.3F;
		public boolean hideHand = false;
	}

	public static final class Cinematic {
		public ShotStyle style = ShotStyle.TRIPOD;
		public float distance = 9.0F;
		public float height = 2.5F;
		public float orbitSpeed = 8.0F;
		public float followSmoothing = 0.5F;
		public float reframeAngle = 26.0F;
		public Reframe reframe = Reframe.GLIDE;
		public float transitionMs = 1200.0F;
		public float letterbox = 0.12F;
		public Vignette vignette = Vignette.SUBTLE;
		public boolean hideHud = true;
		public float lookSensitivity = 0.6F;
		public ShaderMode shaders = ShaderMode.UNCHANGED;
	}

	public static final class Freecam {
		public float speed = 10.0F;
		public float boost = 3.0F;
		public float smoothing = 0.4F;
		public boolean flyWhereLooking = false;
		public boolean noclip = false;
		public boolean exitWhenHurt = true;
		public boolean showNameTag = true;
	}

	public static final class CameraPath {
		public float duration = 12.0F;
		public boolean ease = true;
		public boolean loop = false;
		public boolean constantSpeed = true;
		public boolean hideHud = true;
		public boolean letterbox = true;
	}

	public static final class ThirdPerson {
		public float distance = 1.0F;
		public Shoulder shoulder = Shoulder.CENTER;
		public float shoulderOffset = 0.75F;
	}

	public static final class Roll {
		public float speed = 45.0F;
		public float max = 45.0F;
	}

	public static final class Hud {
		public boolean zoomIndicator = true;
		public boolean freecamPanel = true;
		public boolean hints = true;
		public boolean notices = true;
	}

	public interface Labeled {
		String name();

		default Component label() {
			String type = getClass().getSimpleName().toLowerCase(Locale.ROOT);
			return Component.translatable("zocular.value." + type + "." + name().toLowerCase(Locale.ROOT));
		}
	}

	public enum ZoomMode implements Labeled {
		HOLD, TOGGLE
	}

	public enum ShotStyle implements Labeled {
		TRIPOD, CHASE, ORBIT
	}

	public enum Reframe implements Labeled {
		CUT, GLIDE
	}

	public enum ShaderMode implements Labeled {
		UNCHANGED, ON, OFF
	}

	public enum Shoulder implements Labeled {
		CENTER, RIGHT, LEFT;

		public Shoulder next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public enum Vignette implements Labeled {
		OFF(0.0F), SUBTLE(0.45F), MEDIUM(0.7F), STRONG(0.95F);

		public final float strength;

		Vignette(float strength) {
			this.strength = strength;
		}
	}
}
