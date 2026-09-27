package dev.bhored.zocular.mixin;

import dev.bhored.zocular.camera.CameraControl;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void zocular$cameraControls(CallbackInfo ci) {
		CameraControl.get().handleMouseActions((Minecraft) (Object) this);
	}

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void zocular$blockAttack(CallbackInfoReturnable<Boolean> cir) {
		if (CameraControl.get().blocksInteraction()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void zocular$blockBreaking(boolean down, CallbackInfo ci) {
		if (CameraControl.get().blocksInteraction()) {
			ci.cancel();
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void zocular$blockUse(CallbackInfo ci) {
		if (CameraControl.get().blocksInteraction()) {
			ci.cancel();
		}
	}

	@Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
	private void zocular$blockPick(CallbackInfo ci) {
		if (CameraControl.get().blocksInteraction()) {
			ci.cancel();
		}
	}
}
