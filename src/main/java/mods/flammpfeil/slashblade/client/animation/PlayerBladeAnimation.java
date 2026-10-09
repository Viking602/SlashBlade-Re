package mods.flammpfeil.slashblade.client.animation;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;
import mods.flammpfeil.slashblade.init.DefaultResources;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Local joint rotations are blended before hierarchy evaluation. No animated joint translations. */
public final class PlayerBladeAnimation {
    public static final ContextKey<Pose> POSE = new ContextKey<>(SlashBlade.id("player_pose"));
    public static final ContextKey<Matrix4f> BODY_TRANSFORM = new ContextKey<>(SlashBlade.id("player_body_transform"));
    public static final ContextKey<Boolean> GROUNDED = new ContextKey<>(SlashBlade.id("rig_grounded"));
    public static final ContextKey<Boolean> NO_SCABBARD = new ContextKey<>(SlashBlade.id("rig_no_scabbard"));
    // Retained as an opt-in regression marker; perspective never changes the pose.
    public static final ContextKey<Boolean> VIEWMODEL = new ContextKey<>(SlashBlade.id("rig_viewmodel"));
    private static boolean ready;
    public static boolean available() { return ready; }
    public static void reload(ResourceManager resources) {
        BladeRig.clear();
        LimbSkinning.clear();
        FirstPersonBladeMotion.reload();
        ready = true;
        SlashBlade.LOGGER.info("Loaded original Yamato-inspired choreography: alternating flurry, timed finishers, dedicated judgement preparation/release, constrained draw and noto");
    }
    public static void extract(LivingEntity entity, AvatarRenderState state, BladeMotionState.Sample motion) {
        if (!entity.isAlive() || entity.isSleeping()) return;
        mods.flammpfeil.slashblade.verification.BladeVisualClientProbe.lockComparisonState(entity,state);
        Pose pose = sample(motion);
        if (pose == null) return;
        state.setRenderData(POSE, pose);
        state.setRenderData(GROUNDED, entity.onGround());
        state.setRenderData(NO_SCABBARD, mods.flammpfeil.slashblade.compat.SBData.get(entity.getMainHandItem(),
                mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE).map(s -> s.isNoScabbard()).orElse(false));
        state.setRenderData(BODY_TRANSFORM, pose.rootMatrix(state.mainArm == HumanoidArm.LEFT));
        state.attackTime = 0;
        if (state.mainArm == HumanoidArm.RIGHT) state.rightArmPose = BladeArmPose.BLADE.getValue();
        else state.leftArmPose = BladeArmPose.BLADE.getValue();
        mods.flammpfeil.slashblade.verification.PlayerAnimationClientProbe.extracted(entity, motion.current(), pose);
    }
    public static Pose sample(BladeMotionState.Sample motion) {
        Pose target = sample(motion.current());
        if (!motion.blending()) return target;
        Pose source = sample(motion.previous());
        if (source == null && target == null) return null;
        if (motion.alpha() == 0 && source != null) return source;
        if (target == null) {
            // Authored recovery already inserted the blade. Do not play a second noto
            // when the gameplay clock finally enters STANDBY.
            var frame = KatanaChoreography.containsBlade(source.score)
                    ? KatanaChoreography.finishSheath(motion.alpha(),source.score)
                    : source.score.mainContact()<1
                    ? KatanaChoreography.mix(source.score,KatanaChoreography.idle(),motion.alpha())
                    : KatanaChoreography.sheath(motion.alpha(),source.score);
            return fromScore(frame,source.legWeight*(1-motion.alpha()));
        }
        if (source == null || source.weight==0) return fromScore(KatanaChoreography.containsBlade(target.score)
                ? KatanaChoreography.mix(KatanaChoreography.idle(),target.score,motion.alpha())
                : KatanaChoreography.draw(motion.alpha(), target.score),target.legWeight*motion.alpha());
        if(KatanaChoreography.containsBlade(source.score)) return fromScore(
                KatanaChoreography.resumeDraw(motion.alpha(),source.score,target.score),Mth.lerp(motion.alpha(),source.legWeight,target.legWeight));
        return fromScore(KatanaChoreography.mix(source.score, target.score, motion.alpha()),Mth.lerp(motion.alpha(),source.legWeight,target.legWeight));
    }
    public static Pose sample(BladeAnimationTimeline timeline) {
        var combo = timeline.combo();
        if (!ready || (!DefaultResources.ExMotionLocation.equals(combo.getMotionLoc()) && !DefaultResources.testLocation.equals(combo.getMotionLoc()))
                || combo == Extra.STANDBY_EX || combo == Extra.STANDBY_INAIR) return null;
        String name=combo.getName();
        boolean fullBody=name.startsWith("resharped_") || name.startsWith("ex_aerial_") || name.startsWith("ex_rapid_slash")
                || name.startsWith("ex_rising_star") || name.startsWith("ex_judgement_cut")
                || name.startsWith("ex_void_slash") || name.equals("ex_upperslash_jump") || name.equals("ex_super_sa");
        var frame=KatanaChoreography.sample(timeline);
        return fromScore(frame,fullBody ? frame.active() : 0);
    }
    private static Pose fromScore(KatanaChoreography.Frame score,float legWeight) {
        return new Pose(score.rotations(),new Vector3f(),score.active(),legWeight,score);
    }
    /** Invoked once at HumanoidModel.setupAnim RETURN, after vanilla swimming, crouching and arm bob. */
    public static void apply(HumanoidModel<?> model, HumanoidRenderState state) {
        LimbSkinning.reset(model.root());
        Pose pose = state.getRenderData(POSE);
        if (pose == null) return;
        pose.apply(model, state);
        mods.flammpfeil.slashblade.verification.PlayerAnimationClientProbe.applied(state, model);
    }
    public static final class Pose {
        private final Quaternionf[] rotations;
        private final Vector3f translation;
        private final float weight, legWeight;
        public final boolean blendLegs;
        private final KatanaChoreography.Frame score;
        public KatanaChoreography.Frame score() { return score; }
        private Pose(Quaternionf[] rotations, Vector3f translation, float weight, float legWeight, KatanaChoreography.Frame score) {
            this.rotations = rotations; this.translation = translation;
            this.weight = weight; this.legWeight = legWeight; this.blendLegs = legWeight < .5F;
            this.score = score;
        }
        public float weight() { return weight; }
        // The entity's grounded flag controls floor contact. A full-body animation
        // mask alone must not lift both feet off the floor during a grounded skill.
        public float contactWeight() { return weight; }
        public Quaternionf rotation(int bone) { return new Quaternionf(rotations[bone]); }
        public float component(int bone, int component) {
            if (component < 3) return bone == 0 ? translation.get(component) : 0;
            return rotations[bone].getEulerAnglesZYX(new Vector3f()).get(component - 3);
        }
        public Matrix4f rootMatrix(boolean mirror) {
            // Stable pelvis pivot in model units; translations never accumulate between frames.
            Quaternionf q = jointRotation(0, mirror);
            Vector3f shift = new Vector3f(translation);
            if (mirror) shift.x = -shift.x;
            return new Matrix4f().translation(shift.x / 16, (shift.y + 12) / 16, shift.z / 16)
                    .rotate(q).translate(0, -12F / 16, 0);
        }
        private Quaternionf jointRotation(int joint, boolean mirror) {
            if (mirror) joint = switch (joint) { case 3 -> 4; case 4 -> 3; case 5 -> 6; case 6 -> 5; default -> joint; };
            Quaternionf q = rotation(joint);
            if (mirror) q.set(q.x, -q.y, -q.z, q.w);
            return q;
        }
        private void apply(HumanoidModel<?> model, HumanoidRenderState state) {
            boolean mirror = state.mainArm == HumanoidArm.LEFT;
            setMatrix(model.root(), rootMatrix(mirror));
            // Vanilla models flatten the torso, head and shoulders into siblings.
            // Evaluate their actual parent chain here, then flatten once for submission.
            Quaternionf torso = vanillaRotation(model.body).mul(jointRotation(1, mirror));
            // The spine turns about the pelvis. Rotating the chest about its
            // baked neck origin separates its lower edge from the leg joints.
            var hip = new Vector3f(model.leftLeg.x + model.rightLeg.x,
                    model.leftLeg.y + model.rightLeg.y, model.leftLeg.z + model.rightLeg.z).div(32);
            Matrix4f chestTarget = new Matrix4f().translation(hip).rotate(torso).translate(0, -12F / 16, 0);
            Vector3f chestPosition = new Vector3f(model.body.x, model.body.y, model.body.z).div(16)
                    .lerp(chestTarget.getTranslation(new Vector3f()), weight);
            setMatrix(model.body, chestTarget.setTranslation(chestPosition));
            if (weight > 0) LimbSkinning.bindTorso(model.body, new Quaternionf().slerp(torso, weight));
            var chest = partMatrix(model.body);
            applyUpper(model.head, 2, chest, 0, 0, 0, mirror, 1);
            applyUpper(model.leftArm, 3, chest, 5, 2, 0, mirror, 1 - weight);
            applyUpper(model.rightArm, 4, chest, -5, 2, 0, mirror, 1 - weight);
            setRotation(model.leftLeg, new Quaternionf().slerp(vanillaRotation(model.leftLeg), 1 - legWeight).mul(jointRotation(5, mirror)));
            setRotation(model.rightLeg, new Quaternionf().slerp(vanillaRotation(model.rightLeg), 1 - legWeight).mul(jointRotation(6, mirror)));
            ArticulatedRig.apply(model, state, this);
            BladeRig.constrain(model, state);
            if (grounded(state, this)) {
                // Ground clips have no root-motion authority over the entity.
                // Keep a supporting sole on the floor instead of floating after
                // retargeting the author's limb lengths to the vanilla model.
                model.root().y += (1.5F - lowestSole(model)) * 16 * contactWeight();
            }
        }
        private void applyUpper(ModelPart part, int joint, Matrix4f parent, float x, float y, float z, boolean mirror, float vanillaWeight) {
            Quaternionf vanilla = vanillaRotation(part);
            Quaternionf local = new Quaternionf().slerp(vanilla, vanillaWeight).mul(jointRotation(joint, mirror));
            Matrix4f result = new Matrix4f(parent).translate(x / 16, y / 16, z / 16).rotate(local);
            Vector3f position = new Vector3f(part.x, part.y, part.z).div(16).lerp(result.getTranslation(new Vector3f()), weight);
            Quaternionf rotation = vanilla.slerp(result.getUnnormalizedRotation(new Quaternionf()).normalize(), weight);
            setMatrix(part, new Matrix4f().translationRotate(position, rotation));
        }
    }
    public static boolean grounded(HumanoidRenderState state, Pose pose) {
        return Boolean.TRUE.equals(state.getRenderData(GROUNDED)) && pose.contactWeight() > 0 && !state.isPassenger;
    }
    public static float lowestSole(HumanoidModel<?> model) {
        float lowest = -Float.MAX_VALUE;
        for (var leg : new ModelPart[]{model.leftLeg, model.rightLeg}) {
            var transform = partMatrix(model.root()).mul(partMatrix(leg));
            for (float x : new float[]{-2F / 16, 2F / 16}) for (float z : new float[]{-2F / 16, 2F / 16})
                lowest = Math.max(lowest, transform.transformPosition(LimbSkinning.deform(leg, new Vector3f(x, 12F / 16, z))).y);
        }
        return lowest;
    }
    public static Matrix4f partMatrix(ModelPart part) {
        return new Matrix4f().translation(part.x / 16, part.y / 16, part.z / 16)
                .rotate(vanillaRotation(part)).scale(part.xScale, part.yScale, part.zScale);
    }
    public static Quaternionf vanillaRotation(ModelPart part) { return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot); }
    private static void setMatrix(ModelPart part, Matrix4f matrix) {
        Vector3f p = matrix.getTranslation(new Vector3f()).mul(16);
        part.setPos(p.x, p.y, p.z);
        setRotation(part, matrix.getUnnormalizedRotation(new Quaternionf()).normalize());
    }
    public static void setRotation(ModelPart part, Quaternionf rotation) {
        // ModelPart exposes Euler fields, but the animation remains quaternion-based.
        // A stable singular branch avoids arbitrary roll/yaw at +/-90 degree pitch.
        Matrix4f m = new Matrix4f().rotation(rotation.normalize());
        float cosine = (float)Math.hypot(m.m00(),m.m01());
        float y = (float)Math.atan2(-m.m02(),cosine);
        if (cosine < .000001F)
            part.setRotation(0, y, (float)Math.atan2(-m.m10(), m.m11()));
        else
            part.setRotation((float)Math.atan2(m.m12(), m.m22()), y, (float)Math.atan2(m.m01(), m.m00()));
    }
    private PlayerBladeAnimation() {}
}
