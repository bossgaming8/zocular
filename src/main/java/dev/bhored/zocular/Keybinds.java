package dev.bhored.zocular;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bhored.zocular.config.ZocularConfig;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class Keybinds {
	private static final int UNBOUND = InputConstants.UNKNOWN.getValue();
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Zocular.id("controls"));

	public static final KeyMapping ZOOM = create("zoom", InputConstants.KEY_C);
	public static final KeyMapping ZOOM_IN = create("zoom_in", UNBOUND);
	public static final KeyMapping ZOOM_OUT = create("zoom_out", UNBOUND);
	public static final KeyMapping CINEMATIC = create("cinematic", InputConstants.KEY_I);
	public static final KeyMapping NEXT_SHOT = create("next_shot", InputConstants.KEY_R);
	public static final KeyMapping FREECAM = create("freecam", InputConstants.KEY_F4);
	public static final KeyMapping FREECAM_PANEL = create("freecam_panel", InputConstants.KEY_H);
	public static final KeyMapping ROLL_LEFT = create("roll_left", InputConstants.KEY_LBRACKET);
	public static final KeyMapping ROLL_RIGHT = create("roll_right", InputConstants.KEY_RBRACKET);
	public static final KeyMapping ROLL_RESET = create("roll_reset", InputConstants.KEY_BACKSLASH);
	public static final KeyMapping SHOULDER = create("shoulder", UNBOUND);
	public static final KeyMapping SETTINGS = create("settings", UNBOUND);

	public static final List<KeyMapping> ALL = List.of(
		ZOOM, ZOOM_IN, ZOOM_OUT, CINEMATIC, NEXT_SHOT, FREECAM, FREECAM_PANEL, ROLL_LEFT, ROLL_RIGHT, ROLL_RESET, SHOULDER, SETTINGS
	);

	private Keybinds() {
	}

	static void register() {
		ALL.forEach(KeyMappingHelper::registerKeyMapping);
	}

	/**
	 * 2.0 shipped with zoom on Z and freecam on K. Anyone still on those is moved to the current defaults once;
	 * keys people chose themselves are left alone.
	 */
	static void migrateOldDefaults(Minecraft minecraft) {
		ZocularConfig config = ZocularConfig.get();
		if (config.keyLayout != null && config.keyLayout >= ZocularConfig.KEY_LAYOUT) {
			return;
		}
		boolean moved = move(ZOOM, InputConstants.KEY_Z, InputConstants.KEY_C);
		moved |= move(FREECAM, InputConstants.KEY_K, InputConstants.KEY_F4);
		if (moved) {
			KeyMapping.resetMapping();
			minecraft.options.save();
		}
		config.keyLayout = ZocularConfig.KEY_LAYOUT;
		ZocularConfig.save();
	}

	private static boolean move(KeyMapping mapping, int from, int to) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
		if (key.getType() != InputConstants.Type.KEYBOARD || key.getValue() != from) {
			return false;
		}
		mapping.setKey(InputConstants.Type.KEYBOARD.getOrCreate(to));
		return true;
	}

	private static KeyMapping create(String name, int key) {
		return new KeyMapping("key.zocular." + name, key, CATEGORY);
	}
}
