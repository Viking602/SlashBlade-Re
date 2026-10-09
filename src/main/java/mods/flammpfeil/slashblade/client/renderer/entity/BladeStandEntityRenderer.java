package mods.flammpfeil.slashblade.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mods.flammpfeil.slashblade.client.renderer.SlashBladeTEISR;
import mods.flammpfeil.slashblade.client.renderer.util.GeometryBuffer;
import mods.flammpfeil.slashblade.entity.BladeStandEntity;
import mods.flammpfeil.slashblade.init.SBItems;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class BladeStandEntityRenderer extends EntityRenderer<BladeStandEntity, BladeStandEntityRenderer.State> {
    public static final class State extends EntityRenderState {
        final ItemStackRenderState stand = new ItemStackRenderState();
        GeometryBuffer blade;
        Matrix4f standTransform;
    }
    private final ItemModelResolver resolver;
    private final SlashBladeTEISR bladeRenderer = new SlashBladeTEISR();
    public BladeStandEntityRenderer(EntityRendererProvider.Context context) { super(context); resolver = context.getItemModelResolver(); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(BladeStandEntity entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        Item type = entity.currentType == null || entity.currentType == Items.AIR ? Items.ITEM_FRAME : entity.currentType;
        resolver.updateForNonLiving(state.stand, new ItemStack(type), ItemDisplayContext.FIXED, entity);
        PoseStack pose = new PoseStack();
        Vec3 offset = Vec3.upFromBottomCenterOf(entity.blockPosition(), .75).subtract(entity.position());
        pose.translate(offset.x, offset.y, offset.z);
        pose.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
        pose.mulPose(Axis.YP.rotationDegrees(180 - entity.getYRot()));
        pose.mulPose(Axis.ZP.rotationDegrees(entity.getRotation() * 45));
        pose.scale(2,2,2);
        if (type == SBItems.bladestand_1 || type == SBItems.bladestand_2 || type == SBItems.bladestand_v || type == SBItems.bladestand_s) {
            pose.mulPose(Axis.XP.rotationDegrees(-90));
        } else if (type == SBItems.bladestand_1w || type == SBItems.bladestand_2w) {
            pose.mulPose(Axis.YP.rotationDegrees(180)); pose.translate(0,0,-.15);
        }
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(90)); pose.scale(.5f,.5f,.5f); pose.translate(0,0,.44);
        state.standTransform = new Matrix4f(pose.last().pose());
        pose.popPose();
        if (type == SBItems.bladestand_1w || type == SBItems.bladestand_2w) pose.translate(0,0,-.19);
        pose.mulPose(Axis.YP.rotationDegrees(-180));
        state.blade = new GeometryBuffer();
        if (!entity.getItem().isEmpty()) bladeRenderer.renderStand(entity.getItem().copy(), pose, state.blade, state.lightCoords, entity);
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
        pose.pushPose(); pose.mulPose(state.standTransform);
        state.stand.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
        state.blade.submit(pose, collector);
    }
}
