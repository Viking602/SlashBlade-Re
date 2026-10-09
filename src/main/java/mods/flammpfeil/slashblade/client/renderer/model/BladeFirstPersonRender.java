package mods.flammpfeil.slashblade.client.renderer.model;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade;
import mods.flammpfeil.slashblade.client.renderer.util.MSAutoCloser;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import com.mojang.math.Axis;
import mods.flammpfeil.slashblade.client.renderer.util.GeometryBuffer;
import mods.flammpfeil.slashblade.client.animation.BladeAvatarPose;
import mods.flammpfeil.slashblade.client.animation.PlayerBladeAnimation;
import org.joml.Matrix4f;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Created by Furia on 2016/02/07.
 */
public class BladeFirstPersonRender {
    private final LayerMainBlade layer = new LayerMainBlade();
    private BladeFirstPersonRender() {}
    private static final class SingletonHolder {
        private static final BladeFirstPersonRender instance = new BladeFirstPersonRender();
    }
    public static BladeFirstPersonRender getInstance(){
        return SingletonHolder.instance;
    }

    public void onRenderHand(RenderHandEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getItemStack().getItem() instanceof ItemSlashBlade)) return;
        var blade = mods.flammpfeil.slashblade.compat.SBData.get(event.getItemStack(), ItemSlashBlade.BLADESTATE).orElse(null);
        if (blade == null) return;
        var motion = mods.flammpfeil.slashblade.client.animation.BladeMotionState.sample(mc.player, blade, event.getPartialTick());
        if (mods.flammpfeil.slashblade.client.animation.PlayerBladeAnimation.available()
                && mods.flammpfeil.slashblade.init.DefaultResources.ExMotionLocation.equals(motion.current().combo().getMotionLoc())) {
            if (mc.player.isSleeping() || mc.player.isSpectator()) return;
            var avatar = BladeAvatarPose.extract(mc.player,event.getPartialTick());
            var state = avatar.state();
            var model = avatar.model();
            var meshes = state.getRenderData(mods.flammpfeil.slashblade.client.renderer.layers.LivingBladeLayer.RIG);
            if (meshes == null) return;
            var input = event.getPoseStack();
            input.pushPose();
            // Frame the complete shared rig once. Per-part offsets would separate
            // the palm, grip and saya during transitions.
            input.mulPose(BladeAvatarPose.firstPersonSpace(avatar,event.getPartialTick()));
            if (!mc.player.isInvisible()) {
                var arms = new GeometryBuffer();
                var skin = net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(state.skin.body().texturePath());
                var local = new PoseStack();
                local.mulPose(PlayerBladeAnimation.partMatrix(model.root()));
                model.leftSleeve.visible = mc.player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE);
                model.rightSleeve.visible = mc.player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE);
                model.rightArm.render(local, arms.getBuffer(skin), event.getPackedLight(), net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                model.leftArm.render(local, arms.getBuffer(skin), event.getPackedLight(), net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                arms.submit(input, event.getSubmitNodeCollector());
            }
            mods.flammpfeil.slashblade.client.animation.BladeRig.submit(meshes, model, state, input, event.getSubmitNodeCollector());
            input.popPose();
            event.setCanceled(true);
            return;
        }
        GeometryBuffer geometry = new GeometryBuffer();
        render(new PoseStack(), geometry, event.getPackedLight(), event.getPartialTick());
        // 26.1 supplies the inverse camera view here, before vanilla item/equip/swing
        // transforms. Keep it, along with view bobbing and the normal camera lag.
        geometry.submit(event.getPoseStack(), event.getSubmitNodeCollector());
        event.setCanceled(true);
    }

    public void render(PoseStack matrixStack, MultiBufferSource bufferIn, int combinedLightIn){
        render(matrixStack, bufferIn, combinedLightIn, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }

    public void render(PoseStack matrixStack, MultiBufferSource bufferIn, int combinedLightIn, float partialTicks){
        if(layer == null)
            return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean flag = mc.getCameraEntity() instanceof LivingEntity && ((LivingEntity) mc.getCameraEntity()).isSleeping();
        if (!(mc.options.getCameraType() == CameraType.FIRST_PERSON && !flag && !mc.options.hideGui && !mc.player.isSpectator())) {
            return;
        }
        LocalPlayer player = mc.player;
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof ItemSlashBlade)) return;

        try(MSAutoCloser msac = MSAutoCloser.pushMatrix(matrixStack)){
            matrixStack.translate(0.0f, 0.0f, -0.5f);
            matrixStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
            matrixStack.scale(1.2F, 1.0F, 1.0F);

            layer.render(matrixStack, bufferIn, combinedLightIn, mc.player, 0, 0, partialTicks, 0, 0, 0);
        }
    }
}
