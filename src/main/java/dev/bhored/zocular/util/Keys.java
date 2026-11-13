package dev.bhored.zocular.util;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class Keys {
	private static final Map<KeyMapping, Boolean> WAS_DOWN = new IdentityHashMap<>();

	private Keys() {
	}

	/**
	 * True once for each real press of the key. Minecraft counts the repeat signals a held key sends as extra
	 * clicks, so a toggle that reads clicks would flip on and off for as long as the key is held. This drains the
	 * queued clicks but only reports one when the key was up at the previous check.
	 */
	public static boolean pressed(KeyMapping mapping) {
		boolean clicked = false;
		while (mapping.consumeClick()) {
			clicked = true;
		}
		boolean down = mapping.isDown();
		Boolean wasDown = WAS_DOWN.put(mapping, down);
		return clicked && (wasDown == null || !wasDown);
	}

	/**
	 * Whether the key is physically held. Unlike {@link KeyMapping#isDown()} this ignores the toggle-sprint and
	 * toggle-crouch options, which matters when those keys steer the free camera instead of the player.
	 */
	public static boolean isHeld(KeyMapping mapping) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
		if (key.getType() == InputConstants.Type.KEYBOARD) {
			return key.getValue() > 0 && InputConstants.isKeyDown(key.getValue());
		}
		return mapping.isDown();
	}
}
