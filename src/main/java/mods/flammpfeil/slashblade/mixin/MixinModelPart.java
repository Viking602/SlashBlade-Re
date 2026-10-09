package mods.flammpfeil.slashblade.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.flammpfeil.slashblade.client.animation.LimbSkinning;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.Map;

@Mixin(value = ModelPart.class, remap = false)
public class MixinModelPart implements LimbSkinning.Children {
    @Shadow @Final private List<ModelPart.Cube> cubes;
    @Shadow @Final private Map<String, ModelPart> children;
    @Override public Map<String, ModelPart> slashblade$children() { return children; }
    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void slashblade$skinLimb(PoseStack.Pose pose, VertexConsumer output, int light, int overlay, int color, CallbackInfo ci) {
        if (LimbSkinning.render((ModelPart)(Object)this, cubes, pose, output, light, overlay, color)) ci.cancel();
    }
}
