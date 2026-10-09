package mods.flammpfeil.slashblade.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.entity.PlacePreviewEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

/** Preview uses the current block model pipeline, including mod-provided block models. */
public final class PlacePreviewEntityRenderer extends EntityRenderer<PlacePreviewEntity, PlacePreviewEntityRenderer.State> {
    public static final class State extends EntityRenderState {
        final BlockModelRenderState block = new BlockModelRenderState();
        Vec3 offset = Vec3.ZERO;
    }
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    private final BlockModelResolver resolver;
    public PlacePreviewEntityRenderer(EntityRendererProvider.Context context) { super(context); resolver=context.getBlockModelResolver(); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(PlacePreviewEntity entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        resolver.update(state.block, entity.getBlockState(), DISPLAY);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            var axes=EnumSet.of(Direction.Axis.X,Direction.Axis.Y,Direction.Axis.Z);
            state.offset = entity.position().subtract(player.position()).scale(-1).align(axes)
                .add(player.getLookAngle().add(0,.5,0).scale(3).align(axes)).add(.5,1.5,0);
        }
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
        pose.pushPose(); pose.translate(state.offset.x,state.offset.y,state.offset.z);
        state.block.submit(pose, collector, 15728864, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
    }
}
