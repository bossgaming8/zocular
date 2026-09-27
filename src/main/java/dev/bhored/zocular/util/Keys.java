package dev.bhored.zocular.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class Keys {
	private Keys() {
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
