package mods.flammpfeil.slashblade.verification;

import jp.nyatla.nymmd.types.VmdBezier;
import mods.flammpfeil.slashblade.client.animation.*;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.init.DefaultResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Measures anatomical separation and actual socket contact, not just finite Euler angles. */
public final class BladeRigClientProbe {
    private static double maxShoulderError, maxGripError;
    public static void verifyModel(HumanoidModel<?> model, AvatarRenderState state) {
        var pose = state.getRenderData(PlayerBladeAnimation.POSE);
        require(pose != null, "missing rig pose");
        Matrix4f root = PlayerBladeAnimation.partMatrix(model.root());
        Matrix4f expectedRoot = pose.rootMatrix(state.mainArm == HumanoidArm.LEFT);
        if (PlayerBladeAnimation.grounded(state, pose)) {
            if (pose.contactWeight() > .99999F)
                require(Math.abs(PlayerBladeAnimation.lowestSole(model) - 1.5F) < .00001F, "grounded stance floats or penetrates the floor");
            expectedRoot.m31(root.m31());
        }
        require(root.equals(expectedRoot, .0001F), "final root overwritten or accumulated");
        Matrix4f torso = PlayerBladeAnimation.partMatrix(model.body);
        Vector3f pelvis = torso.transformPosition(new Vector3f(0, 12F / 16, 0));
        Vector3f hips = new Vector3f(model.leftLeg.x + model.rightLeg.x, model.leftLeg.y + model.rightLeg.y,
                model.leftLeg.z + model.rightLeg.z).div(32);
        if (pose.weight() >= 1) require(pelvis.distance(hips) < .00001, "torso detached from pelvis");
        for (var arm : HumanoidArm.values()) {
            var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            var expected = torso.transformPosition(new Vector3f(arm == HumanoidArm.RIGHT ? -5F / 16 : 5F / 16, 2F / 16, 0));
            double error = expected.distance(new Vector3f(part.x, part.y, part.z).div(16));
            if (pose.weight() >= 1) {
                maxShoulderError = Math.max(maxShoulderError, error);
                require(error < .00001, "shoulder detached from torso: " + error);
            }
        }
        Matrix4f[] sockets = BladeRig.attachments(model, state, false);
        for (int i = 0; i < sockets.length; i++) {
            Matrix4f socket = sockets[i];
            require(socket.isFinite() && Math.abs(socket.determinant3x3() - 1) < .0001, "non-rigid weapon binding");
            if (pose.weight() < .99999F) continue; // Explicit hip/hand transfer, not a combat joint.
            var f=pose.score();
            if (i==0 && f.mainContact()<1 || i==1 && !(f.support()==1 || f.support()==0 && f.offContact()==1)) continue;
            var arm = i == 0 ? state.mainArm : state.mainArm.getOpposite();
            var wrist = BladeRig.hand(model, arm, state.skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM)
                    .getTranslation(new Vector3f());
            var contact = i==1 && f.support()==1
                    ? new Matrix4f(sockets[0]).transformPosition(new Vector3f(KatanaChoreography.SUPPORT_GRIP*BladeRig.MODEL_SCALE,0,0))
                    : new Matrix4f(socket).transformPosition(new Vector3f((i == 0 ? BladeRig.PRIMARY_GRIP_X : BladeRig.SHEATH_GRIP_X) * BladeRig.MODEL_SCALE, 0, 0));
            double error = wrist.distance(contact);
            maxGripError = Math.max(maxGripError, error);
            require(error < .0001, "weapon grip left the final hand: " + error + " index="+i+" main="+state.mainArm+" score="+f);
        }
    }
    public static Map<String, Object> verify() {
        maxShoulderError = maxGripError = 0;
        verifyCurves();
        int cases = 0, transitions = 0;
        var mc = Minecraft.getInstance();
        for (boolean slim : new boolean[]{false, true}) {
            var model = new PlayerModel(mc.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
            for (var main : HumanoidArm.values()) {
                var state = new AvatarRenderState(); state.mainArm = main;
                state.setRenderData(PlayerBladeAnimation.GROUNDED, true);
                state.skin = DefaultPlayerSkin.get(new UUID(0, slim ? 0 : 9));
                state.ageInTicks = 117.25F;
                state.xRot = 35; state.yRot = 47;
                for (var combo : ComboState.NONE.getRegistry().values()) {
                    if (!(DefaultResources.ExMotionLocation.equals(combo.getMotionLoc()) || DefaultResources.testLocation.equals(combo.getMotionLoc())) || combo == ComboState.NONE
                            || combo == Extra.STANDBY_EX || combo == Extra.STANDBY_INAIR) continue;
                    for (int frame = 0; frame <= 16; frame++) {
                        float t = frame / 16F;
                        var pose = PlayerBladeAnimation.sample(new BladeAnimationTimeline(combo,
                                combo.getStartFrame() + (combo.getEndFrame() - combo.getStartFrame()) * t));
                        state.setRenderData(PlayerBladeAnimation.POSE, pose);
                        for (int mode = 0; mode < 4; mode++) {
                            state.isCrouching = mode == 1;
                            state.swimAmount = mode == 2 ? 1 : 0;
                            state.isPassenger = mode == 3;
                            state.walkAnimationSpeed = .7F; state.walkAnimationPos = frame * 2.25F;
                            model.setupAnim(state); verifyModel(model, state);
                            var before = BladeRig.attachments(model, state, false);
                            model.setupAnim(state); verifyModel(model, state);
                            var after = BladeRig.attachments(model, state, false);
                            for (int i = 0; i < 2; i++) require(before[i].equals(after[i], .00001F), "deferred submission accumulates transforms");
                            cases++;
                        }
                    }
                }
                state.isCrouching = state.isPassenger = false; state.swimAmount = 0;
                var history = new BladeMotionState.History(); UUID blade = UUID.randomUUID();
                long action = 0;
                for (int frame = 0; frame < 240; frame++) {
                    if (frame % 13 == 0) action++;
                    var combo = action % 2 == 0 ? Extra.EX_COMBO_A1 : Extra.EX_VOID_SLASH;
                    var sample = history.resolve(blade, action, 100 + frame / 12.0,
                            new BladeAnimationTimeline(combo, combo.getStartFrame() + (frame % 13) / 8F));
                    state.setRenderData(PlayerBladeAnimation.POSE, PlayerBladeAnimation.sample(sample));
                    model.setupAnim(state); verifyModel(model, state); transitions++;
                }
            }
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "passed"); report.put("anatomicalAndGripCases", cases);
        report.put("interruptedCombatFrames", transitions);
        report.put("maximumShoulderSeparationBlocks", maxShoulderError);
        report.put("maximumCombatGripErrorBlocks", maxGripError);
        report.put("scope", "69 clips sampled at 17 phases; standard/slim, left/right; walk, crouch, swim, mounted; body/shoulder connectivity; blade/sheath grip contact; repeated deferred setup; rapid interruptions; VMD curve and spherical interpolation");
        return report;
    }
    private static void verifyCurves() {
        int[] curves = {0,0,0,0,0,127,0,127,127,127,127,127,0,127,127,127};
        require(Math.abs(VmdBezier.evaluate(curves, 0, .15625F) - .015625F) < .00001, "VMD cubic inversion is incorrect");
        require(VmdBezier.evaluate(curves, 1, .15625F) > .5F, "VMD channels were mixed together");
        var a = new jp.nyatla.nymmd.types.MmdVector4(); a.w = 1;
        var b = new jp.nyatla.nymmd.types.MmdVector4(); b.y = Math.sin(Math.PI / 3); b.w = Math.cos(Math.PI / 3);
        var q = new jp.nyatla.nymmd.types.MmdVector4(); q.QuaternionSlerp(a, b, .25);
        require(Math.abs(q.y - Math.sin(Math.PI / 12)) < .000001, "rotation uses nlerp instead of spherical interpolation");
        b.y = -b.y; b.w = -b.w; q.QuaternionSlerp(a, b, .25);
        require(Math.abs(q.y - Math.sin(Math.PI / 12)) < .000001, "antipodal quaternion takes the long arc");
    }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }
    private BladeRigClientProbe() {}
}
