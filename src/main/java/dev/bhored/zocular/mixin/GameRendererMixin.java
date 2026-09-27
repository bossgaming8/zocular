package dev.bhored.zocular.mixin;

import dev.bhored.zocular.Zoom;
import dev.bhored.zocular.camera.CameraControl;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void zocular$hideHand(CallbackInfo ci) {
		if (Zoom.hidesHand()) {
			ci.cancel();
		}
	}

	// Vanilla bobs and tilts the whole view with the player's steps and hits, even when the camera is somewhere
	// else entirely. On a detached camera that reads as shaking, so it is skipped while Zocular places the camera.
	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void zocular$steadyWalk(CallbackInfo ci) {
		if (CameraControl.get().isActive()) {
			ci.cancel();
		}
	}

	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void zocular$steadyHurt(CallbackInfo ci) {
		if (CameraControl.get().isActive()) {
			ci.cancel();
		}
	}
}
