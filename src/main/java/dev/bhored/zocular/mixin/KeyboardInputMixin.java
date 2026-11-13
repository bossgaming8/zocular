package dev.bhored.zocular.mixin;

import dev.bhored.zocular.camera.CameraControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
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
			LocalPlayer player = Minecraft.getInstance().player;
			if (control.leagueActive()) {
				// Shift is the build modifier in League mode, so it doesn't crouch.
				Input keys = keyPresses;
				boolean manual = moveVector.lengthSquared() > 1.0E-4F;
				if (manual) {
					control.overhead().clearWalk();
				} else {
					Vec2 walk = control.overhead().walkInput(player);
					if (walk != null) {
						// Hop up single blocks on the way, like League's pathing would go round them.
						boolean hop = keys.jump() || player.horizontalCollision && player.onGround();
						keyPresses = new Input(true, false, false, false, hop, false, false);
						moveVector = walk;
						return;
					}
				}
				keyPresses = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), false, keys.sprint());
			}
			// The player faces the cursor, so turn the keys to match the screen: forward is always up.
			float turn = control.overheadMoveTurn(player);
			float cos = Mth.cos(turn);
			float sin = Mth.sin(turn);
			moveVector = new Vec2(moveVector.x * cos - moveVector.y * sin, moveVector.y * cos + moveVector.x * sin);
		}
	}
}
