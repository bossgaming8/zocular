package dev.bhored.zocular.mixin;

import dev.bhored.zocular.camera.CameraControl;
import dev.bhored.zocular.util.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
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
		CameraControl control = CameraControl.get();
		if (control.blocksInteraction()) {
			cir.setReturnValue(false);
			return;
		}
		Minecraft minecraft = (Minecraft) (Object) this;
		// League mode: clicking something out of reach walks you over to it first.
		if (control.leagueActive() && minecraft.player != null
				&& (minecraft.hitResult == null || minecraft.hitResult.getType() == HitResult.Type.MISS)) {
			control.overhead().approachCursor(minecraft.player.blockInteractionRange());
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
		CameraControl control = CameraControl.get();
		if (control.blocksInteraction()) {
			ci.cancel();
			return;
		}
		// League mode: right-click walks there; Shift + right-click places, opens and uses as normal.
		Minecraft minecraft = (Minecraft) (Object) this;
		if (control.leagueActive() && !Keys.isHeld(minecraft.options.keyShift)) {
			control.overhead().walkToCursor();
			ci.cancel();
		}
	}

	@Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
	private void zocular$blockPick(CallbackInfo ci) {
		CameraControl control = CameraControl.get();
		if (control.blocksInteraction()) {
			ci.cancel();
		} else if (control.leagueActive()) {
			// League mode: the middle button brings the camera back to you.
			control.overhead().recenter();
			ci.cancel();
		}
	}
}
