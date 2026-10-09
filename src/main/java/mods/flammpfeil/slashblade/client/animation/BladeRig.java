package mods.flammpfeil.slashblade.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.client.renderer.model.*;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.client.renderer.util.GeometryBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Weapon meshes stay in bind space until the final, already constrained hands are known. */
public final class BladeRig {
    // Fit the katana to the fixed eight-pixel shoulder/wrist reach. The previous
    // oversized blade required an extreme torso twist to clear its own saya.
    public static final float MODEL_SCALE = 1F / 320;
    public static final float SHEATH_GRIP_X = -45;
    private static PlayerModel firstPersonStandard, firstPersonSlim;
    public record Meshes(GeometryBuffer blade, GeometryBuffer sheath, boolean noScabbard) {}

    public static void clear() {
        firstPersonStandard = firstPersonSlim = null;
    }
    public static Meshes capture(ItemStack item, ISlashBladeState state, int light) {
        var blade = new GeometryBuffer(); var sheath = new GeometryBuffer();
        var obj = BladeModelManager.getInstance().getModel(state.getModel().orElse(null));
        var texture = state.getTexture().orElse(BladeModelManager.resourceDefaultTexture);
        var identity = new PoseStack();
        String part = state.isBroken() ? "blade_damaged" : "blade";
        BladeRenderState.renderOverrided(item, obj, part, texture, identity, blade, light);
        BladeRenderState.renderOverridedLuminous(item, obj, part + "_luminous", texture, identity, blade, light);
        if (!state.isNoScabbard()) {
            BladeRenderState.renderOverrided(item, obj, "sheath", texture, identity, sheath, light);
            BladeRenderState.renderOverridedLuminous(item, obj, "sheath_luminous", texture, identity, sheath, light);
        }
        return new Meshes(blade, sheath, state.isNoScabbard());
    }
    public static Matrix4f hand(HumanoidModel<?> model, HumanoidArm arm, boolean slim) {
        var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float center = (slim ? .5F : 1) * (arm == HumanoidArm.RIGHT ? -1 : 1);
        return PlayerBladeAnimation.partMatrix(model.root()).mul(PlayerBladeAnimation.partMatrix(part))
                .mul(LimbSkinning.hand(part, center));
    }
    private static boolean slim(HumanoidRenderState state) {
        return state instanceof AvatarRenderState a && a.skin.model() == PlayerModelType.SLIM;
    }
    private static float center(HumanoidArm arm, boolean slim) { return (slim ? .5F : 1) * (arm == HumanoidArm.RIGHT ? -1 : 1); }
    private static ModelPart arm(HumanoidModel<?> model, HumanoidRenderState state, int index) {
        HumanoidArm side = index == 0 ? state.mainArm : state.mainArm.getOpposite();
        return side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
    }
    public static KatanaChoreography.Frame score(HumanoidRenderState state) {
        var pose = state.getRenderData(PlayerBladeAnimation.POSE);
        return pose == null ? KatanaChoreography.idle() : pose.score();
    }
    private static Matrix4f authored(HumanoidModel<?> model, HumanoidRenderState state, Matrix4f local) {
        if (state.mainArm == HumanoidArm.LEFT) local = new Matrix4f().scaling(-1,1,1).mul(local).scale(1,1,-1);
        // Remove only the authored upper-spine turn about the waist. The belt must
        // follow the pelvis and native crouch/swim transforms, never orbit with the chest.
        Quaternionf spine=score(state).spineRotation();
        if(state.mainArm==HumanoidArm.LEFT) spine.set(spine.x,-spine.y,-spine.z,spine.w);
        Vector3f hip=new Vector3f(model.leftLeg.x+model.rightLeg.x,model.leftLeg.y+model.rightLeg.y,
                model.leftLeg.z+model.rightLeg.z).div(32);
        Quaternionf nativeSpine=PlayerBladeAnimation.vanillaRotation(model.body).mul(spine.conjugate());
        return new Matrix4f().translation(hip).rotate(nativeSpine).translate(0,-12F/16,0).mul(local);
    }
    private static Matrix4f palmToBlade(Matrix4f palm, HumanoidRenderState state) {
        if (state.mainArm == HumanoidArm.LEFT) palm.rotateY((float)Math.PI);
        return palm;
    }
    private static Matrix4f wristGoal(Matrix4f weapon, float offset, HumanoidRenderState state) {
        Matrix4f goal = new Matrix4f(weapon).translate(offset*MODEL_SCALE,0,0);
        if (state.mainArm == HumanoidArm.LEFT) goal.rotateY((float)Math.PI);
        return goal;
    }
    private static Matrix4f localSocket(HumanoidModel<?> model, HumanoidRenderState state, int index) {
        HumanoidArm side = index == 0 ? state.mainArm : state.mainArm.getOpposite();
        Matrix4f result = palmToBlade(ArticulatedRig.localHand(arm(model,state,index),center(side,slim(state))),state);
        if (index == 1) result.translate(-SHEATH_GRIP_X*MODEL_SCALE,0,0);
        return result;
    }
    public static void constrain(HumanoidModel<?> model, HumanoidRenderState state) {
        var f=score(state);
        if (f.active()<=0) return;
        Matrix4f blade=authored(model,state,f.blade()), saya=authored(model,state,f.sheath());
        boolean bare=Boolean.TRUE.equals(state.getRenderData(PlayerBladeAnimation.NO_SCABBARD));
        Vector3f initial=blade.getTranslation(new Vector3f());
        // Project the common two-hand hilt into the intersection of both reach spheres.
        // The second hand must not silently miss the hilt when the primary wrist is clamped.
        if (f.support() > 0) for (int pass=0;pass<32;pass++) {
          float correction=0;
          for (int index=0;index<2;index++) {
            ModelPart part=arm(model,state,index);
            HumanoidArm side=index==0 ? state.mainArm : state.mainArm.getOpposite();
            Matrix4f wrist=wristGoal(blade,index==0 ? 0 : KatanaChoreography.SUPPORT_GRIP,state);
            var q=wrist.getUnnormalizedRotation(new Quaternionf()).normalize();
            Vector3f goal=wrist.getTranslation(new Vector3f()).sub(q.transform(new Vector3f(center(side,slim(state))/16,0,0)));
            Vector3f shoulder=new Vector3f(part.x,part.y,part.z).div(16);
            Vector3f reach=goal.sub(shoulder,new Vector3f());
            float distance=reach.length();
            correction=Math.max(correction,distance-.485F);
            if(distance>.485F) blade.setTranslation(blade.getTranslation(new Vector3f()).fma((.485F-distance)/distance,reach));
          }
          if(correction<.000001F) break;
        }
        blade.setTranslation(initial.lerp(blade.getTranslation(new Vector3f()),f.support()));
        Matrix4f right=wristGoal(blade,0,state);
        Matrix4f left=rigidBlend(wristGoal(saya,SHEATH_GRIP_X,state),wristGoal(blade,KatanaChoreography.SUPPORT_GRIP,state),f.support());
        // An arced regrip travels in front of the jacket rather than through the abdomen.
        float regrip=(float)Math.sin(Math.PI*f.support());
        Vector3f arc=PlayerBladeAnimation.partMatrix(model.body).transformDirection(new Vector3f(0,-.07F*regrip,-.10F*regrip));
        left.setTranslation(left.getTranslation(new Vector3f()).add(arc));
        for(int i=0;i<2;i++) {
            ModelPart part=arm(model,state,i);
            HumanoidArm side=i==0 ? state.mainArm : state.mainArm.getOpposite();
            float contact=i==0 ? f.mainContact() : bare ? f.support() : Math.max(f.offContact(),f.support());
            Matrix4f natural=ArticulatedRig.localHand(part,center(side,slim(state)));
            Matrix4f goal=rigidBlend(natural,i==0 ? right : left,contact);
            Vector3f pole=PlayerBladeAnimation.partMatrix(model.body).transformDirection(new Vector3f(side==HumanoidArm.RIGHT ? -.45F : .45F, .8F,.15F));
            ArticulatedRig.solveArm(part,center(side,slim(state)),goal,pole);
        }
    }
    /** Weapon poses are derived from final skinned palms after IK, never animated world offsets. */
    public static Matrix4f[] attachments(HumanoidModel<?> model, AvatarRenderState state, boolean noScabbard) {
        var f=score(state);
        Matrix4f root=PlayerBladeAnimation.partMatrix(model.root());
        Matrix4f blade=f.mainContact()>=1 || noScabbard ? localSocket(model,state,0) : authored(model,state,f.blade());
        Matrix4f saya=f.offContact()>=1 && f.support()==0 ? localSocket(model,state,1) : authored(model,state,f.sheath());
        return new Matrix4f[]{new Matrix4f(root).mul(blade),new Matrix4f(root).mul(saya)};
    }
    public static Matrix4f rigidBlend(Matrix4f a, Matrix4f b, float alpha) {
        if (alpha >= 1) return b;
        if (alpha <= 0) return a;
        Vector3f p = a.getTranslation(new Vector3f()).lerp(b.getTranslation(new Vector3f()), alpha);
        Quaternionf q = a.getUnnormalizedRotation(new Quaternionf()).normalize()
                .slerp(b.getUnnormalizedRotation(new Quaternionf()).normalize(), alpha);
        return new Matrix4f().translationRotate(p, q);
    }
    public static void submit(Meshes meshes, HumanoidModel<?> model, AvatarRenderState state, PoseStack input, SubmitNodeCollector collector) {
        Matrix4f[] bindings = attachments(model, state, meshes.noScabbard());
        GeometryBuffer visible=visibleBlade(meshes,score(state));
        for (int i = 0; i < 2; i++) {
            input.pushPose();
            input.mulPose(bindings[i]); input.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
            (i == 0 ? visible : meshes.sheath()).submit(input, collector);
            input.popPose();
        }
    }
    public static GeometryBuffer visibleBlade(Meshes meshes,KatanaChoreography.Frame score) {
        if(meshes.noScabbard() || !KatanaChoreography.containsBlade(score)) return meshes.blade();
        float mouth=meshes.sheath().maxX();
        if(!Float.isFinite(mouth)) return meshes.blade();
        // Authored occupancy, not IK roundoff, decides whether the curved blade is enclosed.
        // The mouth position comes from the selected OBJ, including custom sheath lengths.
        float depth=new Matrix4f(score.sheath()).invert().mul(score.blade()).m30()/MODEL_SCALE;
        return meshes.blade().outsideMouth(mouth-depth);
    }
    public static PlayerModel firstPersonModel(boolean slim) {
        var mc = Minecraft.getInstance();
        if (slim) {
            if (firstPersonSlim == null) firstPersonSlim = new PlayerModel(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            return firstPersonSlim;
        }
        if (firstPersonStandard == null) firstPersonStandard = new PlayerModel(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        return firstPersonStandard;
    }
    private BladeRig() {}
}
