package dev.bhored.zocular.mixin;

import dev.bhored.zocular.camera.CameraControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void zocular$freezePlayer(CallbackInfo ci) {
		// The movement keys are flying the free camera, so the player stands still.
		CameraControl control = CameraControl.get();
		if (control.freezesPlayer()) {
			keyPresses = Input.EMPTY;
			moveVector = Vec2.ZERO;
		} else if (control.mode() == CameraControl.Mode.OVERHEAD && Minecraft.getInstance().player != null) {
			// The player faces the cursor, so turn the keys to match the screen: forward is always up.
			float turn = control.overheadMoveTurn(Minecraft.getInstance().player);
			float cos = Mth.cos(turn);
			float sin = Mth.sin(turn);
			moveVector = new Vec2(moveVector.x * cos - moveVector.y * sin, moveVector.y * cos + moveVector.x * sin);
		}
	}
}
