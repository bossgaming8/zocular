package dev.bhored.zocular.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.bhored.zocular.Zoom;
import dev.bhored.zocular.camera.CameraControl;
import dev.bhored.zocular.camera.Pose;
import dev.bhored.zocular.config.ZocularConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private Level level;
	@Shadow
	private boolean detached;
	@Shadow
	private boolean isPanoramicMode;
	@Shadow
	private int matrixPropertiesDirty;
	@Shadow
	@Final
	private Quaternionf rotation;
	@Shadow
	@Final
	private Vector3f forwards;
	@Shadow
	@Final
	private Vector3f up;
	@Shadow
	@Final
	private Vector3f left;
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	public abstract Vec3 position();

	@Inject(method = "update", at = @At("HEAD"))
	private void zocular$beforeUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
		if (isMainCamera()) {
			CameraControl.get().beforeCameraUpdate(minecraft);
		}
	}

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void zocular$placeCamera(float partialTicks, CallbackInfo ci) {
		if (!isMainCamera()) {
			return;
		}
		Pose pose = CameraControl.get().frame(minecraft, (Camera) (Object) this, partialTicks);
		if (pose != null) {
			apply(pose);
		} else if (detached && minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
			shoulderOffset();
		}
	}

	@ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
	private float zocular$thirdPersonDistance(float distance) {
		return distance * ZocularConfig.get().thirdPerson.distance;
	}

	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float zocular$zoom(float fov) {
		return isPanoramicMode ? fov : fov * Zoom.fovScale();
	}

	private boolean isMainCamera() {
		return minecraft.gameRenderer != null && minecraft.gameRenderer.mainCamera() == (Object) this;
	}

	private void apply(Pose pose) {
		setRotation(pose.yaw(), pose.pitch());
		if (pose.roll() != 0.0F) {
			rotation.rotateZ(-pose.roll() * Mth.DEG_TO_RAD);
			forwards.set(0.0F, 0.0F, -1.0F).rotate(rotation);
			up.set(0.0F, 1.0F, 0.0F).rotate(rotation);
			left.set(-1.0F, 0.0F, 0.0F).rotate(rotation);
			matrixPropertiesDirty |= 3;
		}
		setPosition(pose.position());
		detached = !CameraControl.get().isInsideOwner(pose.position());
	}

	private void shoulderOffset() {
		ZocularConfig.ThirdPerson config = ZocularConfig.get().thirdPerson;
		float side = switch (config.shoulder) {
			case CENTER -> 0.0F;
			case RIGHT -> -1.0F;
			case LEFT -> 1.0F;
		};
		if (side == 0.0F || level == null) {
			return;
		}
		Vec3 from = position();
		Vec3 offset = new Vec3(left).scale(side * config.shoulderOffset);
		Vec3 to = from.add(offset);
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, minecraft.player));
		if (hit.getType() != HitResult.Type.MISS) {
			to = hit.getLocation().subtract(offset.normalize().scale(0.2));
		}
		setPosition(to);
	}
}
