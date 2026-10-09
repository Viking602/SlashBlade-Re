package mods.flammpfeil.slashblade.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.layers.LivingBladeLayer;
import mods.flammpfeil.slashblade.mixin.AvatarRendererAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Both views share joints and attachments; first person frames the whole rig together. */
public final class BladeAvatarPose {
    public record Frame(AvatarRenderState state,PlayerModel model,Matrix4f modelToEntity) {}

    @SuppressWarnings("unchecked")
    public static Frame extract(AbstractClientPlayer player,float partialTick) {
        var mc=Minecraft.getInstance();
        var renderer=(AvatarRenderer<AbstractClientPlayer>)mc.getEntityRenderDispatcher().getPlayerRenderers().get(player.getSkin().model());
        // Includes the same registered modifiers, locomotion, native look and blade
        // clock as third person. The dedicated model avoids mutating queued bodies.
        var state=renderer.createRenderState(player,partialTick);
        var model=BladeRig.firstPersonModel(state.skin.model()==PlayerModelType.SLIM);
        model.setupAnim(state);
        return new Frame(state,model,modelToEntity(renderer,state));
    }

    public static Matrix4f modelToEntity(AvatarRenderer<?> renderer,AvatarRenderState state) {
        var pose=new PoseStack();
        var offset=renderer.getRenderOffset(state);pose.translate(offset.x,offset.y,offset.z);
        var rotation=state.getRenderData(LivingBladeLayer.ROTATION);
        if(rotation!=null) pose.mulPose(rotation);
        pose.scale(state.scale,state.scale,state.scale);
        var access=(AvatarRendererAccess)renderer;
        access.slashblade$setupRotations(state,pose,state.bodyRot,state.scale);
        pose.scale(-1,-1,1);access.slashblade$scale(state,pose);
        pose.translate(0,-1.501F,0);
        return new Matrix4f(pose.last().pose());
    }

    public static Matrix4f cameraSpace(Frame frame) {
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera();
        var position=camera.position();var state=frame.state();
        return new Matrix4f().rotation(new Quaternionf(camera.rotation()).conjugate())
                .translate((float)(state.x-position.x),(float)(state.y-position.y),(float)(state.z-position.z))
                .mul(frame.modelToEntity());
    }
    /** One rigid presentation transform for skin, hands, blade and saya. A bounded
     * observation pitch keeps the waist-held hilt in view and the shoulders behind
     * the lens. It does not change animation time, IK, size or gameplay aim. */
    public static Matrix4f firstPersonSpace(Frame frame, float partialTick) {
        var mc=Minecraft.getInstance();
        var player=mc.player;
        var camera=mc.gameRenderer.getMainCamera();
        var state=frame.state();
        float radians=(float)Math.PI/180;
        float yaw=player.getViewYRot(partialTick)*radians;
        float pitch=player.getViewXRot(partialTick);
        var nativeView=new Quaternionf().rotationYXZ((float)Math.PI-yaw,-pitch*radians,0);
        float bodyYaw=state.bodyRot*radians;
        var framedView=new Quaternionf().rotationYXZ((float)Math.PI-bodyYaw,-(55+pitch*.04F)*radians,0);
        var position=camera.position();
        float forward=.20F*state.scale;
        return new Matrix4f().rotation(new Quaternionf(camera.rotation()).conjugate()).rotate(nativeView)
                .translate(0,-.08F*state.scale,-.45F*state.scale)
                .rotate(framedView.conjugate())
                .translate((float)(state.x-position.x)+ (float)Math.sin(bodyYaw)*forward,
                        (float)(state.y-position.y), (float)(state.z-position.z)-(float)Math.cos(bodyYaw)*forward)
                .mul(frame.modelToEntity());
    }
    private BladeAvatarPose() {}
}
