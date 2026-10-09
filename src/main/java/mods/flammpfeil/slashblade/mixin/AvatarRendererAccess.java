package mods.flammpfeil.slashblade.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuse the actual avatar transforms, including swimming, flight and crouch. */
@Mixin(value=AvatarRenderer.class,remap=false)
public interface AvatarRendererAccess {
    @Invoker("setupRotations") void slashblade$setupRotations(AvatarRenderState state,PoseStack stack,float bodyRot,float scale);
    @Invoker("scale") void slashblade$scale(AvatarRenderState state,PoseStack stack);
}
