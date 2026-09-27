package dev.bhored.zocular.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.bhored.zocular.Zoom;
import dev.bhored.zocular.camera.CameraControl;
import dev.bhored.zocular.util.LookSmoothing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Unique
	private final LookSmoothing zocular$smoothing = new LookSmoothing();

	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void zocular$turn(LocalPlayer player, double dx, double dy, Operation<Void> original, @Local(argsOnly = true) double frameTime) {
		CameraControl control = CameraControl.get();
		double scale = Zoom.sensitivityScale() * control.lookSensitivity();
		double[] turn = zocular$smoothing.apply(dx * scale, dy * scale, frameTime, Zoom.lookSmoothing());

		if (control.capturesLook()) {
			control.turn(turn[0], turn[1]);
		} else {
			original.call(player, turn[0], turn[1]);
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void zocular$scroll(long handle, double xOffset, double yOffset, CallbackInfo ci) {
		if (handle != minecraft.getWindow().handle() || minecraft.player == null
				|| minecraft.gui.screen() != null || minecraft.gui.overlay() != null) {
			return;
		}
		double amount = minecraft.options.discreteMouseScroll().get() ? Math.signum(yOffset) : yOffset;
		amount *= minecraft.options.mouseWheelSensitivity().get();
		if (amount == 0.0) {
			return;
		}

		CameraControl control = CameraControl.get();
		if (control.mode() == CameraControl.Mode.FREECAM) {
			if (!control.path().isPlaying()) {
				control.adjustFreecamSpeed(amount);
			}
			ci.cancel();
		} else if (Zoom.scroll(amount)) {
			ci.cancel();
		}
	}
}
