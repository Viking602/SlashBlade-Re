package mods.flammpfeil.slashblade.verification;

import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.client.animation.*;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.init.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.*;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Compare the camera against actual baked head bones, then exercise the registered
 * camera event. This catches a shake that moves but is unrelated to the body. */
public final class BladeCameraClientProbe {
    public static Map<String,Object> verify() {
        var mc=Minecraft.getInstance();int samples=0;float error=0;
        var idle=BladeMotionState.Sample.direct(new BladeAnimationTimeline(Extra.STANDBY_EX,0));
        for(boolean slim:new boolean[]{false,true}) for(var hand:HumanoidArm.values()) {
            var model=new PlayerModel(mc.getEntityModels().bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
            var state=new AvatarRenderState();state.mainArm=hand;
            state.skin=DefaultPlayerSkin.get(new UUID(0,slim?0:9));
            for(var combo:ComboState.NONE.getRegistry().values()) {
                if(!DefaultResources.ExMotionLocation.equals(combo.getMotionLoc()) || combo==ComboState.NONE) continue;
                for(int frame=0;frame<=16;frame++) for(float blend:new float[]{.2F,.55F,1}) {
                    var timeline=new BladeAnimationTimeline(combo,combo.getStartFrame()+(combo.getEndFrame()-combo.getStartFrame())*frame/16F);
                    var pose=PlayerBladeAnimation.sample(new BladeMotionState.Sample(timeline,idle,blend));
                    state.setRenderData(PlayerBladeAnimation.POSE,pose);model.setupAnim(state);
                    var head=PlayerBladeAnimation.partMatrix(model.root()).mul(PlayerBladeAnimation.partMatrix(model.head))
                            .getUnnormalizedRotation(new Quaternionf()).normalize().getEulerAnglesYXZ(new Vector3f()).mul(180F/(float)Math.PI);
                    var expected=BladeCameraAnimation.fromHeadAngles(head.y,head.x,head.z);
                    var actual=BladeCameraAnimation.target(pose,hand);
                    float distance=distance(expected,actual);error=Math.max(error,distance);
                    require(distance<.001F,"camera detached from animated head: "+combo.getName()+" phase="+frame+" blend="+blend+" error="+distance);
                    require(Math.abs(actual.yaw())<=5.001F && Math.abs(actual.pitch())<=4.001F && Math.abs(actual.roll())<=3.501F,"unbounded head follow");
                    samples++;
                }
            }
        }
        var report=new LinkedHashMap<String,Object>();
        report.put("headBoneSamples",samples);report.put("maxHeadFollowErrorDegrees",error);
        report.put("registeredCameraEvent",verifyEvent());report.put("status","passed");
        return report;
    }
    private static Map<String,Object> verifyEvent() {
        var mc=Minecraft.getInstance();var player=mc.player;
        var item=player.getMainHandItem();var cameraType=mc.options.getCameraType();var entity=mc.getCameraEntity();var hand=player.getMainArm();
        float yaw=player.getYRot(),pitch=player.getXRot();
        var sword=new ItemStack(SBItems.slashblade);
        var blade=SBData.get(sword,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        var report=new LinkedHashMap<String,Object>();
        try {
            mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setCameraEntity(player);player.setMainArm(HumanoidArm.RIGHT);
            player.setItemInHand(InteractionHand.MAIN_HAND,sword);
            for(var combo:new ComboState[]{Extra.EX_COMBO_A1,Extra.EX_COMBO_A3,Extra.EX_COMBO_A4,Extra.EX_COMBO_B2,
                    Extra.EX_COMBO_C,Extra.EX_JUDGEMENT_CUT_SLASH,Extra.EX_JUDGEMENT_CUT_SLASH_JUST,Extra.EX_SUPER_SA}) {
                blade.setComboSeq(combo);blade.setLastActionTime(mc.level.getGameTime());BladeMotionState.clear();
                float max=0,travel=0;BladeCameraAnimation.Angles previous=null;
                for(int i=0;i<=100;i++) {
                    double time=(combo.getEndFrame()-combo.getStartFrame())/(1.5*combo.getSpeed())*i/100;
                    var a=event(time);var b=event(time);
                    require(distance(a,b)<.00001F,"repeated camera rendering accumulates offsets");
                    max=Math.max(max,Math.max(Math.abs(a.yaw()),Math.max(Math.abs(a.pitch()),Math.abs(a.roll()))));
                    if(previous!=null) travel+=distance(previous,a);previous=a;
                }
                require(max>.5F && travel>1,"registered camera hook does not follow "+combo.getName());
                report.put(combo.getName(),Map.of("peakDegrees",max,"angularTravel",travel));
            }
            // Action interruption must retain the displayed head on its first frame.
            blade.setComboSeq(Extra.EX_COMBO_A3);blade.setLastActionTime(mc.level.getGameTime());BladeMotionState.clear();
            var before=event(5);
            blade.setComboSeq(Extra.EX_COMBO_A2);var after=event(5);
            require(distance(before,after)<.00001F,"interrupted camera blend snaps");
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            require(distance(event(5),BladeCameraAnimation.Angles.ZERO)<.00001F,"third-person camera was animated");
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_SWORD));
            require(distance(event(5),BladeCameraAnimation.Angles.ZERO)<.00001F,"ordinary item inherits camera motion");
            player.setItemInHand(InteractionHand.MAIN_HAND,sword);
            blade.setComboSeq(Extra.STANDBY_EX);BladeMotionState.clear();
            require(distance(event(0),BladeCameraAnimation.Angles.ZERO)<.00001F,"idle camera does not return to neutral");
            require(player.getXRot()==pitch && player.getYRot()==yaw,"rendering changed gameplay aim");
            report.put("guards","duplicate frames, interruption, third person, ordinary item, standby and unchanged player aim");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND,item);player.setMainArm(hand);mc.options.setCameraType(cameraType);
            mc.setCameraEntity(entity);BladeMotionState.clear();
        }
        return report;
    }
    private static BladeCameraAnimation.Angles event(double time) {
        var mc=Minecraft.getInstance();
        var e=new ViewportEvent.ComputeCameraAngles(mc.gameRenderer.getMainCamera(),time,137,22,1.5F);
        NeoForge.EVENT_BUS.post(e);
        return new BladeCameraAnimation.Angles(e.getYaw()-137,e.getPitch()-22,e.getRoll()-1.5F);
    }
    private static float distance(BladeCameraAnimation.Angles a,BladeCameraAnimation.Angles b) {
        return Math.max(Math.abs(a.yaw()-b.yaw()),Math.max(Math.abs(a.pitch()-b.pitch()),Math.abs(a.roll()-b.roll())));
    }
    private static void require(boolean b,String message) {if(!b)throw new IllegalStateException(message);}
    private BladeCameraClientProbe() {}
}
