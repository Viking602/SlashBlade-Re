package mods.flammpfeil.slashblade.mixin;

import mods.flammpfeil.slashblade.client.animation.PlayerBladeAnimation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mod-owned client hook: every body/armor model gets the same final constrained pose. */
@Mixin(value = HumanoidModel.class, remap = false)
public class MixinHumanoidModel {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("RETURN"))
    private void slashblade$applyFinalRig(HumanoidRenderState state, CallbackInfo callback) {
        PlayerBladeAnimation.apply((HumanoidModel<?>)(Object)this, state);
    }
}
