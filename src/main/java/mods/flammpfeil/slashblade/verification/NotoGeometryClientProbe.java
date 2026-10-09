package mods.flammpfeil.slashblade.verification;

import com.mojang.blaze3d.vertex.*;
import mods.flammpfeil.slashblade.client.animation.*;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.*;
import java.util.*;

/** Actual OBJ faces against the rendered, deformed torso and thighs for the entire noto. */
public final class NotoGeometryClientProbe {
    private record Triangle(Vector3f a,Vector3f b,Vector3f c,Vector3f min,Vector3f max) {
        Triangle(Vector3f[] p) { this(p[0],p[1],p[2],new Vector3f(p[0]).min(p[1]).min(p[2]),new Vector3f(p[0]).max(p[1]).max(p[2])); }
    }
    public static Map<String,Object> verify() {
        var sword=new ItemStack(SBItems.slashblade);
        var meshes=BladeRig.capture(sword,SBData.get(sword,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new),15728880);
        var idle=BladeMotionState.Sample.direct(new BladeAnimationTimeline(Extra.STANDBY_EX,0));
        int samples=0, collisions=0, clipped=0, negativeControls=0; List<String> failures=new ArrayList<>();
        var combos=new mods.flammpfeil.slashblade.capability.slashblade.ComboState[]{Extra.EX_COMBO_A1,Extra.EX_COMBO_A2,
                Extra.EX_COMBO_A3,Extra.EX_COMBO_A4,Extra.EX_COMBO_A5EX,Extra.EX_COMBO_B2,Extra.EX_COMBO_B3,Extra.EX_UPPERSLASH};
        for(boolean slim:new boolean[]{false,true}) for(var main:HumanoidArm.values()) for(boolean crouch:new boolean[]{false,true}) {
            var model=new PlayerModel(Minecraft.getInstance().getEntityModels().bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
            var state=new AvatarRenderState(); state.mainArm=main; state.isCrouching=crouch;
            state.skin=DefaultPlayerSkin.get(new UUID(0,slim?0:9));
            for(var combo:combos) for(int frame=0;frame<=200;frame++) {
                float t=frame/200F;
                var motion=new BladeMotionState.Sample(idle.current(),BladeMotionState.Sample.direct(new BladeAnimationTimeline(combo,combo.getEndFrame())),t);
                state.setRenderData(PlayerBladeAnimation.POSE,PlayerBladeAnimation.sample(motion)); model.setupAnim(state);
                var score=BladeRig.score(state); var attachments=BladeRig.attachments(model,state,false);
                var body=new Surface(); var pose=new PoseStack(); pose.mulPose(PlayerBladeAnimation.partMatrix(model.root()));
                model.body.render(pose,body,15728880,0); model.rightLeg.render(pose,body,15728880,0); model.leftLeg.render(pose,body,15728880,0);
                if(frame==0) {
                    if(body.triangles.size()<100 || meshes.blade().triangles().size()<10) throw new IllegalStateException("Geometry probe received an empty surface");
                    Matrix4f throughChest=PlayerBladeAnimation.partMatrix(model.root()).mul(PlayerBladeAnimation.partMatrix(model.body))
                            .translate(.55F,.25F,0).scale(BladeRig.MODEL_SCALE);
                    boolean detected=false;
                    for(var face:meshes.blade().triangles()) {
                        for(var v:face) throughChest.transformPosition(v);
                        var bad=new Triangle(face);
                        for(var skin:body.triangles) if(intersects(bad,skin)) { detected=true; break; }
                    }
                    if(!detected) throw new IllegalStateException("Geometry probe failed to detect its intentional through-body control");
                    negativeControls++;
                }
                var visible=BladeRig.visibleBlade(meshes,score);
                if(visible!=meshes.blade()) {
                    float boundary=meshes.sheath().maxX()-new Matrix4f(score.sheath()).invert().mul(score.blade()).m30()/BladeRig.MODEL_SCALE;
                    for(var face:visible.triangles()) for(var v:face)
                        if(v.x<boundary-.0001F) throw new IllegalStateException("Enclosed sword surface protrudes through the saya");
                    clipped++;
                }
                boolean hit=false;
                for(int weapon=0;weapon<2;weapon++) {
                    Matrix4f matrix=new Matrix4f(attachments[weapon]).scale(BladeRig.MODEL_SCALE);
                    for(var face:(weapon==0?visible:meshes.sheath()).triangles()) {
                        // Hilt/guard intentionally meet palms; only the blade/tube are tested.
                        if(weapon==0 && face[0].x>-35 && face[1].x>-35 && face[2].x>-35) continue;
                        for(var v:face) matrix.transformPosition(v);
                        var triangle=new Triangle(face);
                        for(var skin:body.triangles) if(intersects(triangle,skin)) { hit=true; break; }
                        if(hit) break;
                    }
                    if(hit) {
                        collisions++;
                        if(failures.size()<100) failures.add(combo.getName()+" phase="+t+" hand="+main+" slim="+slim+" crouch="+crouch+" weapon="+weapon);
                        break;
                    }
                }
                samples++;
            }
        }
        var report=new LinkedHashMap<String,Object>(); report.put("samples",samples); report.put("collisionFrames",collisions);
        report.put("enclosedBladeSamples",clipped); report.put("firstCollisions",failures);
        report.put("intentionalCollisionControlsDetected",negativeControls);
        mods.flammpfeil.slashblade.SlashBlade.LOGGER.info("Noto mesh regression: {}",report);
        if(collisions>0 && !Boolean.getBoolean("slashblade.geometryDiagnostic")) throw new IllegalStateException("Noto mesh collision: "+report);
        report.put("status",collisions==0?"passed":"diagnostic-failed"); return report;
    }
    private static boolean intersects(Triangle a,Triangle b) {
        if(a.max.x<b.min.x || b.max.x<a.min.x || a.max.y<b.min.y || b.max.y<a.min.y || a.max.z<b.min.z || b.max.z<a.min.z) return false;
        return segment(a.a,a.b,b)||segment(a.b,a.c,b)||segment(a.c,a.a,b)||segment(b.a,b.b,a)||segment(b.b,b.c,a)||segment(b.c,b.a,a);
    }
    private static boolean segment(Vector3f from,Vector3f to,Triangle t) {
        var d=new Vector3f(to).sub(from); var e1=new Vector3f(t.b).sub(t.a); var e2=new Vector3f(t.c).sub(t.a);
        var p=new Vector3f(d).cross(e2); float det=e1.dot(p); if(java.lang.Math.abs(det)<1E-10F) return false;
        var s=new Vector3f(from).sub(t.a); float u=s.dot(p)/det; if(u<0||u>1) return false;
        var q=new Vector3f(s).cross(e1); float v=d.dot(q)/det; if(v<0||u+v>1) return false;
        float distance=e2.dot(q)/det; return distance>.0001F && distance<.9999F;
    }
    private static final class Surface implements VertexConsumer {
        final List<Triangle> triangles=new ArrayList<>(); final List<Vector3f> quad=new ArrayList<>();
        public VertexConsumer addVertex(float x,float y,float z) {
            quad.add(new Vector3f(x,y,z));
            if(quad.size()==4) { triangles.add(new Triangle(new Vector3f[]{quad.get(0),quad.get(1),quad.get(2)})); triangles.add(new Triangle(new Vector3f[]{quad.get(0),quad.get(2),quad.get(3)})); quad.clear(); }
            return this;
        }
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setColor(int rgba){return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
        public VertexConsumer setLineWidth(float w){return this;}
    }
    private NotoGeometryClientProbe() {}
}
