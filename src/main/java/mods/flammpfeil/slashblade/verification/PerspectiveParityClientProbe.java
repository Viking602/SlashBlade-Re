package mods.flammpfeil.slashblade.verification;

import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.client.animation.*;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;
import mods.flammpfeil.slashblade.init.DefaultResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Compare view-specific solved palms/weapons after removing only presentation. */
public final class PerspectiveParityClientProbe {
    @SuppressWarnings("unchecked")
    public static Map<String,Object> verifyRegisteredRig() {
        var mc=Minecraft.getInstance();var player=mc.player;
        var saved=player.getMainHandItem();var savedHand=player.getMainArm();var savedPose=player.getPose();
        boolean ground=player.onGround(),crouching=player.isCrouching();
        float yaw=player.getYRot(),pitch=player.getXRot(),head=player.yHeadRot,headOld=player.yHeadRotO,body=player.yBodyRot,bodyOld=player.yBodyRotO;
        var item=new net.minecraft.world.item.ItemStack(mods.flammpfeil.slashblade.init.SBItems.slashblade);
        var blade=mods.flammpfeil.slashblade.compat.SBData.get(item,mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        var renderer=(net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.player.AbstractClientPlayer>)mc.getEntityRenderDispatcher().getPlayerRenderers().get(player.getSkin().model());
        int samples=0,vertices=0,thirdPersonArmVertices=0;float maxError=0;
        try {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,item);
            for(var hand:HumanoidArm.values()) for(int scenario=0;scenario<4;scenario++) {
                player.setMainArm(hand);player.setPose(scenario==1?net.minecraft.world.entity.Pose.CROUCHING:net.minecraft.world.entity.Pose.STANDING);
                setLocalCrouching(player,scenario==1);
                player.setOnGround(scenario!=3);player.walkAnimation.stop();if(scenario==2) {
                    player.walkAnimation.update(.8F,1,1);player.walkAnimation.update(.8F,1,1);
                }
                player.setYRot(scenario*43);player.setXRot(scenario*22-33);
                player.yHeadRot=player.yHeadRotO=player.getYRot();player.yBodyRot=player.yBodyRotO=player.getYRot()-scenario*9;
                for(var combo:ComboState.NONE.getRegistry().values()) {
                    if(!DefaultResources.ExMotionLocation.equals(combo.getMotionLoc()) || combo==ComboState.NONE) continue;
                    for(float phase:new float[]{.05F,.45F,.8F}) {
                        float elapsed=(combo.getEndFrame()-combo.getStartFrame())/(1.5F*combo.getSpeed())*phase;
                        float partial=elapsed-(int)elapsed;
                        blade.setComboSeq(combo);blade.setLastActionTime(mc.level.getGameTime()-(int)elapsed);BladeMotionState.clear();
                        var state=renderer.createRenderState(player,partial);
                        if(state.isCrouching!=(scenario==1) || scenario==2 && state.walkAnimationSpeed<=0)
                            throw new IllegalStateException("fixture did not enter the requested locomotion state: "+scenario);
                        var camera=mc.gameRenderer.getMainCamera();var cp=camera.position();
                        var view=new Matrix4f().rotation(new Quaternionf(camera.rotation()).conjugate())
                                .translate((float)(state.x-cp.x),(float)(state.y-cp.y),(float)(state.z-cp.z));
                        var bob=new Matrix4f().translation(.025F,-.015F,.01F).rotateZ(.026F);
                        var frame=BladeAvatarPose.extract(player,partial);
                        var presentation=BladeAvatarPose.firstPersonSpace(frame,partial).mul(BladeAvatarPose.cameraSpace(frame).invert());
                        var expected=new WorldCapture(new Matrix4f(bob).mul(presentation).mul(view));
                        var stack=new com.mojang.blaze3d.vertex.PoseStack();var offset=renderer.getRenderOffset(state);
                        stack.translate(offset.x,offset.y,offset.z);
                        renderer.submit(state,stack,expected,new net.minecraft.client.renderer.state.level.CameraRenderState());
                        if(expected.armVertices==0) throw new IllegalStateException("third-person arms were hidden");
                        thirdPersonArmVertices+=expected.armVertices;
                        var actual=new BladeMotionClientProbe.Capture(new Matrix4f());
                        var input=new com.mojang.blaze3d.vertex.PoseStack();input.mulPose(bob);var before=new Matrix4f(input.last().pose());
                        input.pushPose();input.mulPose(BladeAvatarPose.firstPersonSpace(frame,partial));
                        BladeRig.submit(frame.state().getRenderData(mods.flammpfeil.slashblade.client.renderer.layers.LivingBladeLayer.RIG),frame.model(),frame.state(),input,actual);
                        input.popPose();
                        if(!input.last().pose().equals(before,.00001F)) throw new IllegalStateException("rig pose restoration failure");
                        var wm=renderer.getModel();var fm=BladeRig.firstPersonModel(state.skin.model()==net.minecraft.world.entity.player.PlayerModelType.SLIM);
                        var worldParts=new net.minecraft.client.model.geom.ModelPart[]{wm.root(),wm.body,wm.head,wm.leftArm,wm.rightArm,wm.leftLeg,wm.rightLeg};
                        var firstParts=new net.minecraft.client.model.geom.ModelPart[]{fm.root(),fm.body,fm.head,fm.leftArm,fm.rightArm,fm.leftLeg,fm.rightLeg};
                        for(int bone=0;bone<worldParts.length;bone++) {
                            if(!PlayerBladeAnimation.partMatrix(worldParts[bone]).equals(PlayerBladeAnimation.partMatrix(firstParts[bone]),.00001F))
                                throw new IllegalStateException("same-frame bone differs: "+combo.getName()+" bone="+bone+" scenario="+scenario);
                            var wb=LimbSkinning.get(worldParts[bone]);var fb=LimbSkinning.get(firstParts[bone]);
                            if((wb==null)!=(fb==null) || wb!=null && (!wb.lower().equals(fb.lower(),.00001F) || !wb.palm().equals(fb.palm(),.00001F)))
                                throw new IllegalStateException("same-frame elbow/wrist skinning differs: "+combo.getName()+" bone="+bone);
                        }
                        try { actual.sameViewGeometry(expected); }
                        catch(Exception error) { throw new IllegalStateException("first/third submitted vertices differ: "+combo.getName()+" phase="+phase+" scenario="+scenario+" hand="+hand+" actual="+actual.positions.size()+" expected="+expected.positions.size(),error); }
                        for(int i=0;i<actual.positions.size();i++) maxError=Math.max(maxError,actual.positions.get(i).distance(expected.positions.get(i)));
                        vertices+=actual.positions.size();samples++;
                    }
                }
            }
        } finally {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,saved);player.setMainArm(savedHand);player.setPose(savedPose);player.setOnGround(ground);
            setLocalCrouching(player,crouching);
            player.setYRot(yaw);player.setXRot(pitch);player.yHeadRot=head;player.yHeadRotO=headOld;player.yBodyRot=body;player.yBodyRotO=bodyOld;player.walkAnimation.stop();BladeMotionState.clear();
        }
        return Map.of("status","passed","samples",samples,"verticesCompared",vertices,"maxVertexErrorBlocks",maxError,
                "thirdPersonArmVertices",thirdPersonArmVertices,
                "scope","world AvatarRenderer versus isolated copy of the same articulated rig; every supported clip; left/right; standing/crouching/walking/airborne; identical bones, weapon vertices and normals; third-person skin remains rendered");
    }

    private static void setLocalCrouching(net.minecraft.client.player.LocalPlayer player,boolean value) {
        // LocalPlayer uses an input-derived flag, not Entity.pose, for extraction.
        // Set it only inside this isolated synchronous probe and restore it above.
        try {
            var field=net.minecraft.client.player.LocalPlayer.class.getDeclaredField("crouching");
            field.setAccessible(true);field.setBoolean(player,value);
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("cannot enter crouching fixture",error);}
    }

    private static final class WorldCapture extends BladeMotionClientProbe.Capture {
        int armVertices;
        WorldCapture(Matrix4f view) {super(view);}
        @Override public <S> void submitModel(net.minecraft.client.model.Model<? super S> model,S renderState,
                com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.rendertype.RenderType type,
                int light,int overlay,int tint,net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,int outline,
                net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
            if(!(model instanceof PlayerModel avatar) || !(renderState instanceof AvatarRenderState state)) return;
            avatar.setupAnim(state);
            var mesh=new mods.flammpfeil.slashblade.client.renderer.util.GeometryBuffer();
            var local=new com.mojang.blaze3d.vertex.PoseStack();local.mulPose(PlayerBladeAnimation.partMatrix(avatar.root()));
            var skin=net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(state.skin.body().texturePath());
            avatar.rightArm.render(local,mesh.getBuffer(skin),light,overlay);avatar.leftArm.render(local,mesh.getBuffer(skin),light,overlay);
            // Still exercise the real third-person skin submission, separately
            // from the weapon geometry shared with the arm-free first-person view.
            var skinCapture=new BladeMotionClientProbe.Capture(new Matrix4f());
            mesh.submit(pose,skinCapture);
            armVertices+=skinCapture.positions.size();
        }
    }
    public static Map<String,Object> verify(boolean strict) {
        var mc=Minecraft.getInstance();int samples=0,different=0;float maxPosition=0,maxAngle=0;
        String worst="";
        for(boolean slim:new boolean[]{false,true}) for(var hand:HumanoidArm.values()) {
            var a=new PlayerModel(mc.getEntityModels().bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
            var b=new PlayerModel(mc.getEntityModels().bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
            for(var combo:ComboState.NONE.getRegistry().values()) {
                if(!DefaultResources.ExMotionLocation.equals(combo.getMotionLoc()) || combo==ComboState.NONE) continue;
                for(int frame=0;frame<=16;frame++) {
                    var pose=PlayerBladeAnimation.sample(new BladeAnimationTimeline(combo,
                            combo.getStartFrame()+(combo.getEndFrame()-combo.getStartFrame())*frame/16F));
                    var world=new AvatarRenderState();world.mainArm=hand;world.skin=DefaultPlayerSkin.get(new UUID(0,slim?0:9));world.speedValue=1;
                    var first=new AvatarRenderState();first.mainArm=hand;first.skin=world.skin;first.speedValue=1;
                    world.setRenderData(PlayerBladeAnimation.POSE,pose);first.setRenderData(PlayerBladeAnimation.POSE,pose);
                    first.setRenderData(PlayerBladeAnimation.VIEWMODEL,true);
                    a.setupAnim(world);b.setupAnim(first);
                    // port.19 virtual shoulders have a common +1 block offset.
                    // Removing that is legitimate presentation; remaining IK changes are not.
                    Matrix4f correction=new Matrix4f().translation(0,strict?0:-1,0);
                    if(pose==null && !strict) correction.identity();
                    var wa=BladeRig.attachments(a,world,false);var fb=BladeRig.attachments(b,first,false);
                    Matrix4f[] left={BladeRig.hand(a,HumanoidArm.LEFT,slim),BladeRig.hand(a,HumanoidArm.RIGHT,slim),wa[0],wa[1]};
                    Matrix4f root=PlayerBladeAnimation.partMatrix(b.root());
                    Matrix4f normalize=strict?new Matrix4f():new Matrix4f(root).mul(correction).mul(new Matrix4f(root).invert());
                    Matrix4f[] right={BladeRig.hand(b,HumanoidArm.LEFT,slim),BladeRig.hand(b,HumanoidArm.RIGHT,slim),fb[0],fb[1]};
                    float sampleError=0;
                    for(int joint=0;joint<left.length;joint++) {
                        var expected=left[joint];var actual=new Matrix4f(normalize).mul(right[joint]);
                        float position=expected.getTranslation(new Vector3f()).distance(actual.getTranslation(new Vector3f()));
                        var difference=expected.getUnnormalizedRotation(new Quaternionf()).normalize().invert()
                                .mul(actual.getUnnormalizedRotation(new Quaternionf()).normalize());
                        // atan2 remains accurate near zero; acos(dot) amplifies
                        // float roundoff into a spurious ~0.08 degree discrepancy.
                        float angle=(float)Math.toDegrees(2*Math.atan2(Math.sqrt(difference.x*difference.x+difference.y*difference.y+difference.z*difference.z),Math.abs(difference.w)));
                        if(position>maxPosition) {maxPosition=position;worst=combo.getName()+" / "+hand+" / slim="+slim+" / sample="+frame+" / socket="+joint;}
                        maxAngle=Math.max(maxAngle,angle);sampleError=Math.max(sampleError,position);
                    }
                    if(sampleError>.00001F) different++;samples++;
                }
            }
        }
        if(strict && different!=0) throw new IllegalStateException("perspective rigs differ: "+different+" / "+samples+" max="+maxPosition+" "+worst);
        return Map.of("status",different==0?"passed":"different","samples",samples,"differentSamples",different,
                "maxSocketPositionErrorBlocks",maxPosition,"maxSocketAngleErrorDegrees",maxAngle,"worst",worst);
    }
    private PerspectiveParityClientProbe() {}
}
