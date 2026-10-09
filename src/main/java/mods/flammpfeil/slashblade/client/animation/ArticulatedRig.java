package mods.flammpfeil.slashblade.client.animation;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Fixed-length shoulder/elbow/wrist and hip/knee/ankle chains. All lengths are model pixels. */
public final class ArticulatedRig {
    public static void apply(HumanoidModel<?> model, HumanoidRenderState state, PlayerBladeAnimation.Pose pose) {
        float weight = pose.weight();
        if (weight <= 0) return;
        for (var arm : new ModelPart[]{model.leftArm, model.rightArm}) {
            Quaternionf authored = PlayerBladeAnimation.vanillaRotation(arm);
            // Retarget a straight authored reach into an elbow arc. Raised arms fold more;
            // the independent wrist retains the original attack's blade orientation.
            Vector3f direction = authored.transform(new Vector3f(0, 1, 0));
            float bend = (.48F + .52F * (1 - direction.y) * .5F) * weight;
            PlayerBladeAnimation.setRotation(arm, new Quaternionf(authored).rotateX(bend * .5F));
            LimbSkinning.bind(arm, 4, 9, new Quaternionf().rotationX(-bend), new Quaternionf().rotationX(bend * .5F));
        }
        for (var leg : new ModelPart[]{model.leftLeg, model.rightLeg}) {
            Quaternionf authored = PlayerBladeAnimation.vanillaRotation(leg);
            float swing = Math.abs(leg.xRot);
            float bend = Math.min(1.1F, .22F + swing * .52F) * weight;
            if (state.isCrouching) bend += .2F * weight;
            PlayerBladeAnimation.setRotation(leg, new Quaternionf(authored).rotateX(-bend * .5F));
            LimbSkinning.bind(leg, 6, 11, new Quaternionf().rotationX(bend), new Quaternionf().rotationX(-bend * .5F));
        }
    }
    public static Matrix4f localHand(ModelPart arm, float center) {
        return PlayerBladeAnimation.partMatrix(arm).mul(LimbSkinning.hand(arm, center));
    }
    /** Solve both bones before skinning. Unreachable goals are clamped, never stretched. */
    public static void solveArm(ModelPart arm, float center, Matrix4f desired) {
        solveArm(arm, center, desired, null);
    }
    public static void solveArm(ModelPart arm, float center, Matrix4f desired, Vector3f elbowPole) {
        Quaternionf wrist = desired.getUnnormalizedRotation(new Quaternionf()).normalize();
        Vector3f shoulder = new Vector3f(arm.x, arm.y, arm.z).div(16);
        // The hand's skin center is offset sideways from the anatomical bone axis.
        Vector3f goal = desired.getTranslation(new Vector3f()).sub(wrist.transform(new Vector3f(center / 16, 0, 0)));
        Vector3f direction = goal.sub(shoulder, new Vector3f());
        float distance = direction.length();
        if (distance < 1E-6F) direction.set(0, 1, 0); else direction.div(distance);
        distance = Math.clamp(distance, .085F, .5625F);
        // At full extension, float round-off otherwise creates a visible sqrt(epsilon)
        // elbow angle when a native straight arm first enters the solver.
        if (distance > .562499F) distance = .5625F;
        Quaternionf reference = PlayerBladeAnimation.vanillaRotation(arm);
        Vector3f pole = elbowPole == null ? reference.transform(new Vector3f(0, 0, 1)) : new Vector3f(elbowPole);
        pole.fma(-pole.dot(direction), direction);
        if (pole.lengthSquared() < 1E-6F) {
            pole = reference.transform(new Vector3f(1, 0, 0));
            pole.fma(-pole.dot(direction), direction);
        }
        pole.normalize();
        // The palm is centred in pixel 9 of the existing 12-pixel arm, not on
        // its wrist boundary. Solve the unequal 4/5-pixel segments without stretch.
        float upperAlong=(distance*distance+.25F*.25F-.3125F*.3125F)/(2*distance);
        float height = (float)Math.sqrt(Math.max(0, .25F*.25F-upperAlong*upperAlong));
        Vector3f upperDirection = new Vector3f(direction).mul(upperAlong).fma(height, pole).normalize();
        Vector3f lowerDirection = new Vector3f(direction).mul(distance-upperAlong).fma(-height, pole).normalize();
        // Carry the hand's roll through the forearm and upper arm. Leaving all roll
        // at the wrist creates a candy-wrapper deformation during a cross-body grasp.
        Quaternionf lower = new Quaternionf().rotationTo(wrist.transform(new Vector3f(0, 1, 0)), lowerDirection).mul(wrist).normalize();
        Quaternionf upper = new Quaternionf().rotationTo(lowerDirection, upperDirection).mul(lower).normalize();
        PlayerBladeAnimation.setRotation(arm, upper);
        LimbSkinning.bind(arm, 4, 9, new Quaternionf(upper).invert().mul(lower), new Quaternionf(lower).invert().mul(wrist));
    }
    private ArticulatedRig() {}
}
