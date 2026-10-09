package mods.flammpfeil.slashblade.client.animation;

import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Render-only head follow. The shared sword/body clock also drives the camera;
 * entity look angles, aim, hit direction and network input are never modified. */
public final class BladeCameraAnimation {
    public record Angles(float yaw,float pitch,float roll) {
        public static final Angles ZERO=new Angles(0,0,0);
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        var mc=Minecraft.getInstance();
        var player=mc.player;
        if(player==null || mc.level==null || mc.getCameraEntity()!=player
                || event.getCamera()!=mc.gameRenderer.getMainCamera() || !mc.options.getCameraType().isFirstPerson()
                || !player.isAlive() || player.isSleeping() || player.isSpectator()) {
            return;
        }
        Angles offset=Angles.ZERO;
        if(PlayerBladeAnimation.available() && player.getMainHandItem().getItem() instanceof ItemSlashBlade) {
            var blade=SBData.get(player.getMainHandItem(),ItemSlashBlade.BLADESTATE).orElse(null);
            if(blade!=null) offset=target(PlayerBladeAnimation.sample(BladeMotionState.sample(player,blade,(float)event.getPartialTick())),player.getMainArm());
        }
        // Fade pitch at the poles; never force the player's look through vertical.
        event.setYaw(event.getYaw()+offset.yaw());
        event.setPitch(event.getPitch()+offset.pitch()*pitchScale(event.getPitch()));
        event.setRoll(event.getRoll()+offset.roll());
    }

    private static float pitchScale(float pitch) {
        return (float)Math.max(0,Math.cos(Math.toRadians(pitch)));
    }

    public static Angles target(PlayerBladeAnimation.Pose pose,HumanoidArm hand) {
        if(pose==null || pose.weight()<=0) return Angles.ZERO;
        // This is the same flattened head chain used by Pose.applyUpper with
        // neutral native look: pelvis * blended(chest * neck). Mouse look is
        // already present in the event and must not be included a second time.
        var head=pose.rotation(0).mul(new org.joml.Quaternionf().slerp(
                pose.rotation(1).mul(pose.rotation(2)),pose.weight()));
        if(hand==HumanoidArm.LEFT) head.set(head.x,-head.y,-head.z,head.w);
        var angles=head.getEulerAnglesYXZ(new org.joml.Vector3f()).mul(180F/(float)Math.PI);
        // In model space +Y is down; the camera's roll therefore has the opposite sign.
        // Attenuate full-body turns, but preserve their timing and direction.
        // No extra spring, wall clock or attack-triggered shake can lag behind the body.
        return fromHeadAngles(angles.y,angles.x,angles.z);
    }
    public static Angles fromHeadAngles(float yaw,float pitch,float roll) {
        return new Angles(limit(yaw*.65F,5),limit(pitch*.50F,4),limit(-roll*.85F,3.5F));
    }
    private static float limit(float value,float maximum) { return maximum*(float)Math.tanh(value/maximum); }

    private BladeCameraAnimation() {}
}
