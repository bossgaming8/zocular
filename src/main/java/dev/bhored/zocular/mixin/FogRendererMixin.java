package dev.bhored.zocular.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.bhored.zocular.camera.FarView;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	/**
	 * Fog is measured from the camera, so a camera pulled far back would see the world it is looking at disappear
	 * into it. Push the fog out by however far the camera has moved, so the world around the player looks the same.
	 */
	@ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
	private FogData zocular$farViewFog(FogData fog) {
		float extra = FarView.extraDistance();
		if (extra > 0.0F) {
			fog.renderDistanceStart += extra;
			fog.renderDistanceEnd += extra;
			fog.environmentalStart += extra;
			fog.environmentalEnd += extra;
			fog.skyEnd += extra;
			fog.cloudEnd += extra;
		}
		return fog;
	}
}
