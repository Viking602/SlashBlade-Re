package mods.flammpfeil.slashblade.verification;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.flammpfeil.slashblade.client.animation.*;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Regressions for the actual skinned surface and the grasp/release boundaries. */
public final class ArticulatedRigClientProbe {
    public static Map<String, Object> verify() {
        verifySkinVolume();
        int samples = 0, vertices = 0, chestClearanceSamples=0, edgeUpSamples=0;
        float maxLengthError = 0, maxSurfaceGripError = 0, maxTransferStep = 0, maxDockGap = 0, maxDockTipGap=0;
        String maxDockCase="";
        var idle = BladeMotionState.Sample.direct(new BladeAnimationTimeline(Extra.STANDBY_EX, 0));
        var attack = new BladeAnimationTimeline(Extra.EX_COMBO_A1, 8);
        for (boolean view : new boolean[]{false, true}) for (boolean slim : new boolean[]{false, true}) for (var main : HumanoidArm.values()) {
            var model = new PlayerModel(Minecraft.getInstance().getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
            var state = new AvatarRenderState(); state.mainArm = main;
            state.setRenderData(PlayerBladeAnimation.VIEWMODEL, view);
            state.skin = DefaultPlayerSkin.get(new UUID(0, slim ? 0 : 9));
            for (boolean sheathing : new boolean[]{false,true}) for (int step = 0; step <= 1000; step++) {
                float phase = step / 1000F;
                var motion=sheathing ? new BladeMotionState.Sample(idle.current(),BladeMotionState.Sample.direct(
                        new BladeAnimationTimeline(Extra.EX_COMBO_A1,Extra.EX_COMBO_A1.getEndFrame())),phase)
                        : new BladeMotionState.Sample(attack,idle,phase);
                state.setRenderData(PlayerBladeAnimation.POSE, PlayerBladeAnimation.sample(motion));
                model.setupAnim(state);
                var pose=state.getRenderData(PlayerBladeAnimation.POSE);
                var score = pose==null ? KatanaChoreography.idle() : pose.score();
                var weapons = BladeRig.attachments(model, state, false);
                if(!sheathing && phase<=KatanaChoreography.DRAW_CLEAR || sheathing && phase>=.34F) {
                    // Inspect the actual submitted binding, not a keyframe roll constant.
                    // +Y is the built-in blade's cutting edge; model -Y is upwards.
                    require(weapons[0].transformDirection(new Vector3f(0,1,0)).y<-.9F,
                            "cutting edge points down during draw/noto: phase="+phase+" hand="+main+" view="+view);
                    edgeUpSamples++;
                }
                if(!view && (!sheathing && phase<=KatanaChoreography.DRAW_CLEAR || sheathing && phase>=.34F)) {
                    Matrix4f inChest=PlayerBladeAnimation.partMatrix(model.root()).mul(PlayerBladeAnimation.partMatrix(model.body))
                            .invert().mul(weapons[0]);
                    for(int point=0;point<=32;point++) {
                        Vector3f p=inChest.transformPosition(new Vector3f((-35.4F-(291-35.4F)*point/32)*BladeRig.MODEL_SCALE,0,0));
                        boolean inside=Math.abs(p.x)<3.9F/16 && Math.abs(p.z)<1.9F/16 && p.y>.5F/16 && p.y<7F/16;
                        require(!inside,"occupied blade shaft passes through the chest: phase="+phase+" sheathing="+sheathing+" hand="+main);
                        chestClearanceSamples++;
                    }
                }
                for (var side : HumanoidArm.values()) {
                    ModelPart arm = side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                    float center = (slim ? .5F : 1) * (side == HumanoidArm.RIGHT ? -1 : 1);
                    var binding = LimbSkinning.get(arm);
                    if (binding == null) continue;
                    Matrix4f base = PlayerBladeAnimation.partMatrix(arm);
                    Vector3f shoulder = base.getTranslation(new Vector3f());
                    Vector3f elbow = base.transformPosition(new Vector3f(0, .25F, 0));
                    Vector3f wrist = new Matrix4f(base).mul(binding.hand()).getTranslation(new Vector3f());
                    float lengthError = Math.max(Math.abs(shoulder.distance(elbow) - .25F), Math.abs(elbow.distance(wrist) - .3125F));
                    maxLengthError = Math.max(maxLengthError, lengthError);
                    require(lengthError < .00001F, "IK stretched an arm");
                    Matrix4f whole = PlayerBladeAnimation.partMatrix(model.root()).mul(base);
                    Vector3f surface = whole.transformPosition(LimbSkinning.deform(arm, new Vector3f(center/16, 9F/16, 0)));
                    Vector3f socket = BladeRig.hand(model, side, slim).getTranslation(new Vector3f());
                    float surfaceError = surface.distance(socket);
                    maxSurfaceGripError = Math.max(maxSurfaceGripError, surfaceError);
                    require(surfaceError < .00001F, "socket detached from the skinned palm");
                    int index = side == main ? 0 : 1;
                    float contact = index == 0 ? score.mainContact() : score.support()==0 ? score.offContact() : score.support()==1 ? 1 : 0;
                    if (contact >= 1) {
                        Vector3f grip = index==1 && score.support()==1
                                ? new Matrix4f(weapons[0]).transformPosition(new Vector3f(KatanaChoreography.SUPPORT_GRIP*BladeRig.MODEL_SCALE,0,0))
                                : new Matrix4f(weapons[index]).transformPosition(new Vector3f((index == 0 ? BladeRig.PRIMARY_GRIP_X : BladeRig.SHEATH_GRIP_X)*BladeRig.MODEL_SCALE, 0, 0));
                        require(grip.distance(surface) < .0001F, "held weapon detached during draw/sheath");
                    }
                }
                if (!sheathing && phase >= KatanaChoreography.GRASP_END && phase <= KatanaChoreography.DRAW_CLEAR
                        || sheathing && phase>=KatanaChoreography.INSERT_START && phase<=KatanaChoreography.INSERT_END) {
                    float pull=sheathing ? 1-KatanaChoreography.ease(KatanaChoreography.INSERT_START,KatanaChoreography.INSERT_END,phase)
                            : KatanaChoreography.ease(KatanaChoreography.GRASP_END,KatanaChoreography.DRAW_CLEAR,phase);
                    Matrix4f expected = new Matrix4f(weapons[1]).translate(KatanaChoreography.DRAW_DISTANCE/16*pull, 0, 0);
                    float gap = expected.getTranslation(new Vector3f()).distance(weapons[0].getTranslation(new Vector3f()));
                    if(gap>maxDockGap) { maxDockGap=gap; maxDockCase="phase="+phase+", hand="+main+", view="+view+", slim="+slim+", sheathing="+sheathing; }
                    require(gap < .0002F, "blade failed to track the sheath opening: " + gap + " phase=" + phase + " hand=" + main+" view="+view+" sheathing="+sheathing);
                    float tipGap=expected.transformPosition(new Vector3f(-291*BladeRig.MODEL_SCALE,0,0)).distance(
                            new Matrix4f(weapons[0]).transformPosition(new Vector3f(-291*BladeRig.MODEL_SCALE,0,0)));
                    maxDockTipGap=Math.max(maxDockTipGap,tipGap);
                    require(tipGap<.001F,"blade tip rotates out of the saya axis: "+tipGap);
                }
                if (step % 200 == 0) {
                    var capture = new SurfaceCapture();
                    model.rightArm.render(new PoseStack(), capture, 15728880, 0);
                    vertices += capture.vertices;
                    require(capture.vertices > 24, "limb surface was not subdivided/skinned");
                }
                samples++;
            }
            for (float boundary : new float[]{.18F, KatanaChoreography.GRASP_END, KatanaChoreography.DRAW_CLEAR}) {
                Matrix4f[][] ends = new Matrix4f[2][];
                for (int i = 0; i < 2; i++) {
                    float phase = boundary + (i == 0 ? -.00001F : .00001F);
                    state.setRenderData(PlayerBladeAnimation.POSE, PlayerBladeAnimation.sample(new BladeMotionState.Sample(attack, idle, phase)));
                    model.setupAnim(state); ends[i] = BladeRig.attachments(model, state, false);
                }
                for (int i = 0; i < 2; i++) {
                    float jump = ends[0][i].getTranslation(new Vector3f()).distance(ends[1][i].getTranslation(new Vector3f()));
                    maxTransferStep = Math.max(maxTransferStep, jump);
                    require(jump < .002F, "grasp/release snaps weapon: " + jump + " phase=" + boundary + " hand=" + main);
                }
            }
            state.setRenderData(PlayerBladeAnimation.POSE, null); model.setupAnim(state);
            require(LimbSkinning.get(model.rightArm) == null && LimbSkinning.get(model.leftSleeve) == null, "skinning leaked into an ordinary item pose");
            for (boolean crouch : new boolean[]{false, true}) {
                state.isCrouching = crouch;
                state.setRenderData(PlayerBladeAnimation.POSE, null); model.setupAnim(state);
                var parts = new ModelPart[]{model.body, model.head, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg};
                Matrix4f[] baseline = Arrays.stream(parts).map(PlayerBladeAnimation::partMatrix).toArray(Matrix4f[]::new);
                state.setRenderData(PlayerBladeAnimation.POSE, PlayerBladeAnimation.sample(new BladeMotionState.Sample(attack, idle, .000001F)));
                model.setupAnim(state);
                for (int i = 0; i < parts.length; i++) require(baseline[i].equals(PlayerBladeAnimation.partMatrix(parts[i]), .0001F), "idle handoff jumps a native pose, crouch=" + crouch + " part=" + i);
            }
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "passed"); report.put("drawSheathSamples", samples); report.put("skinnedVertices", vertices);
        report.put("maxBoneLengthErrorBlocks", maxLengthError); report.put("maxSkinnedPalmSocketErrorBlocks", maxSurfaceGripError);
        report.put("maxGraspReleaseStepBlocks", maxTransferStep); report.put("maxBladeSheathAlignmentErrorBlocks", maxDockGap);
        report.put("maximumAlignmentCase",maxDockCase);
        report.put("maxBladeTipAxisErrorBlocks",maxDockTipGap);
        report.put("guidedBladeChestClearanceSamples",chestClearanceSamples);
        report.put("actualEdgeUpBindingSamples",edgeUpSamples);
        report.put("skinVolume", "170 degree bend and wrist twist: cross-section radius preserved, shortest quaternion hemisphere");
        return report;
    }
    private static void verifySkinVolume() {
        var low = new Matrix4f().translation(0,.25F,0).rotateX((float)Math.toRadians(-170)).translate(0,-.25F,0);
        var tip = new Matrix4f(low).translate(0,.5F,0).rotateY((float)Math.toRadians(150)).translate(0,-.5F,0);
        var skin = new LimbSkinning.Binding(4,8,low,tip,new Matrix4f(),new Matrix4f(),new Matrix4f());
        require(skin.lowSkin().q().dot(skin.palmSkin().q()) >= 0, "wrist skinning takes the long rotation arc");
        for (float y : new float[]{.25F,7F/16}) {
            var center = skin.position(new Vector3f(0,y,0));
            var edge = skin.position(new Vector3f(.125F,y,0));
            require(Math.abs(center.distance(edge)-.125F) < .00001F, "skinning collapses a bent limb's volume");
        }
    }
    private static final class SurfaceCapture implements VertexConsumer {
        int vertices;
        @Override public VertexConsumer addVertex(float x, float y, float z) { require(Float.isFinite(x+y+z), "invalid skinned vertex"); vertices++; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { require(Math.abs(x*x+y*y+z*z-1) < .001, "invalid skinned normal"); return this; }
        @Override public VertexConsumer setUv(float u, float v) { require(Float.isFinite(u+v), "invalid skin UV"); return this; }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
        @Override public VertexConsumer setColor(int argb) { return this; }
        @Override public VertexConsumer setUv1(int u,int v) { return this; }
        @Override public VertexConsumer setUv2(int u,int v) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }
    private ArticulatedRigClientProbe() {}
}
