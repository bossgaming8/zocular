package dev.bhored.zocular;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

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

	private static KeyMapping create(String name, int key) {
		return new KeyMapping("key.zocular." + name, key, CATEGORY);
	}
}
