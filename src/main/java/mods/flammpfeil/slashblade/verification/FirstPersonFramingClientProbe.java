package mods.flammpfeil.slashblade.verification;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.client.animation.*;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import java.util.*;

/** Rasterize the actual registered hand-pass triangles. Bone parity alone cannot
 * detect an empty viewport or shoulders covering the entire first-person image. */
public final class FirstPersonFramingClientProbe {
    public static Map<String,Object> verify() {
        var mc=Minecraft.getInstance();var player=mc.player;var camera=mc.gameRenderer.getMainCamera();
        var saved=player.getMainHandItem();var hand=player.getMainArm();var cameraType=mc.options.getCameraType();
        var cameraEntity=mc.getCameraEntity();
        float pitch=player.getXRot(),oldPitch=player.xRotO,yaw=player.getYRot(),oldYaw=player.yRotO;
        float head=player.yHeadRot,oldHead=player.yHeadRotO,body=player.yBodyRot,oldBody=player.yBodyRotO;
        var sword=new ItemStack(SBItems.slashblade);
        var blade=SBData.get(sword,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        float worstArms=0,minIdleWeapon=1,minIdleHeight=1;int samples=0,idles=0;
        String worst="";
        var failures=new ArrayList<String>();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND,sword);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setCameraEntity(player);
            player.setYRot(0);player.yRotO=player.yHeadRot=player.yHeadRotO=player.yBodyRot=player.yBodyRotO=0;
            for(var side:HumanoidArm.values()) for(int angle:new int[]{-89,-65,0,45,65,80,89}) for(int look:new int[]{-75,0,75}) {
                player.setMainArm(side);player.setXRot(angle);player.xRotO=angle;
                player.setYRot(look);player.yRotO=player.yHeadRot=player.yHeadRotO=look;
                camera.update(mc.getDeltaTracker());
                var skin=RenderTypes.entityTranslucent(player.getSkin().body().texturePath());
                for(var combo:new ComboState[]{Extra.STANDBY_EX,Extra.EX_COMBO_A1,Extra.EX_COMBO_A2,Extra.EX_COMBO_A3,
                        Extra.EX_JUDGEMENT_CUT_SLASH,Extra.EX_SUPER_SA}) {
                    for(int phase=0;phase<(combo==Extra.STANDBY_EX ? 1 : 9);phase++) {
                        float elapsed=(combo.getEndFrame()-combo.getStartFrame())/(1.5F*combo.getSpeed())*phase/8;
                        blade.setComboSeq(combo);blade.setLastActionTime(mc.level.getGameTime()-(int)elapsed);BladeMotionState.clear();
                        var capture=new ViewCapture(skin,(float)mc.getWindow().getWidth()/mc.getWindow().getHeight());
                        var event=new RenderHandEvent(InteractionHand.MAIN_HAND,new PoseStack(),capture,15728880,
                                elapsed-(int)elapsed,angle,0,0,sword);
                        NeoForge.EVENT_BUS.post(event);
                        if(!event.isCanceled()) throw new IllegalStateException("framing bypassed the registered hand renderer");
                        if(capture.armVertices<48 || capture.weaponVertices<30) throw new IllegalStateException("framing probe did not distinguish geometry: arms="+capture.armVertices+" weapon="+capture.weaponVertices);
                        String label=side+"/pitch="+angle+"/yaw="+look+"/"+combo.getName()+"/"+phase;
                        float arms=capture.arms.coverage();
                        if(arms>worstArms) {worstArms=arms;worst=label;}
                        if(arms>.42F && failures.size()<30) failures.add("arm occlusion "+arms+" "+label);
                        if(combo==Extra.STANDBY_EX) {
                            float visible=capture.weapon.coverage(),height=capture.weapon.height();
                            minIdleWeapon=Math.min(minIdleWeapon,visible);minIdleHeight=Math.min(minIdleHeight,height);idles++;
                            if((visible<.002F || height<.12F) && failures.size()<30) failures.add("idle weapon missing/small "+visible+" height="+height+" "+label);
                        }
                        samples++;
                    }
                }
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.setMainArm(hand);
            player.setXRot(pitch);player.xRotO=oldPitch;player.setYRot(yaw);player.yRotO=oldYaw;
            player.yHeadRot=head;player.yHeadRotO=oldHead;player.yBodyRot=body;player.yBodyRotO=oldBody;
            mc.options.setCameraType(cameraType);mc.setCameraEntity(cameraEntity);BladeMotionState.clear();camera.update(mc.getDeltaTracker());
        }
        var result=new LinkedHashMap<String,Object>();
        result.put("status",failures.isEmpty()?"passed":"failed");result.put("samples",samples);result.put("idleViews",idles);
        result.put("skinModel",player.getSkin().model().toString());
        result.put("maxArmScreenCoverage",worstArms);result.put("worstArmView",worst);
        result.put("minimumIdleWeaponCoverage",minIdleWeapon);result.put("minimumIdleWeaponHeight",minIdleHeight);result.put("failures",failures);
        mods.flammpfeil.slashblade.SlashBlade.LOGGER.info("First-person framing regression: {}",result);
        if(!failures.isEmpty()) throw new IllegalStateException("first-person framing: "+result);
        return result;
    }
    private static final class ViewCapture extends SubmitNodeStorage {
        final RenderType skin;final Raster arms,weapon;int armVertices,weaponVertices;
        ViewCapture(RenderType skin,float aspect) {this.skin=skin;arms=new Raster(aspect);weapon=new Raster(aspect);}
        @Override public void submitCustomGeometry(PoseStack pose,RenderType type,SubmitNodeCollector.CustomGeometryRenderer renderer) {
            var capture=new BladeMotionClientProbe.Capture(new Matrix4f());capture.submitCustomGeometry(pose,type,renderer);
            var raster=type==skin ? arms : weapon;var points=capture.positions;
            if(type==skin) armVertices+=points.size();else weaponVertices+=points.size();
            int stride=type.mode()==VertexFormat.Mode.QUADS ? 4 : 3;
            for(int i=0;i+stride<=points.size();i+=stride) {
                raster.triangle(points.get(i),points.get(i+1),points.get(i+2));
                if(stride==4) raster.triangle(points.get(i),points.get(i+2),points.get(i+3));
            }
        }
    }
    private static final class Raster {
        static final int W=96,H=60;final boolean[] mask=new boolean[W*H];final float aspect;
        Raster(float aspect) {this.aspect=aspect;}
        void triangle(Vector3f a,Vector3f b,Vector3f c) {
            var polygon=new ArrayList<Vector3f>();var prior=c;
            for(var p:new Vector3f[]{a,b,c}) {
                if((prior.z<-.05F)!=(p.z<-.05F)) polygon.add(new Vector3f(prior).lerp(p,(-.05F-prior.z)/(p.z-prior.z)));
                if(p.z<=-.05F) polygon.add(p);prior=p;
            }
            for(int i=1;i+1<polygon.size();i++) fill(project(polygon.get(0)),project(polygon.get(i)),project(polygon.get(i+1)));
        }
        Vector2f project(Vector3f p) {return new Vector2f((.5F+p.x/(-p.z*2*.7002075F*aspect))*W,(.5F-p.y/(-p.z*2*.7002075F))*H);}
        void fill(Vector2f a,Vector2f b,Vector2f c) {
            int x0=Math.max(0,(int)Math.floor(Math.min(a.x,Math.min(b.x,c.x)))),x1=Math.min(W-1,(int)Math.ceil(Math.max(a.x,Math.max(b.x,c.x))));
            int y0=Math.max(0,(int)Math.floor(Math.min(a.y,Math.min(b.y,c.y)))),y1=Math.min(H-1,(int)Math.ceil(Math.max(a.y,Math.max(b.y,c.y))));
            float area=cross(a,b,c.x,c.y);if(Math.abs(area)<.000001F) return;
            for(int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++) {
                float u=cross(a,b,x+.5F,y+.5F)/area,v=cross(b,c,x+.5F,y+.5F)/area,w=cross(c,a,x+.5F,y+.5F)/area;
                if(u>=0 && v>=0 && w>=0) mask[y*W+x]=true;
            }
        }
        float coverage() {int n=0;for(boolean occupied:mask) if(occupied)n++;return (float)n/mask.length;}
        float height() {int low=H,high=-1;for(int i=0;i<mask.length;i++) if(mask[i]){low=Math.min(low,i/W);high=Math.max(high,i/W);}return high<low?0:(high-low+1F)/H;}
        static float cross(Vector2f a,Vector2f b,float x,float y) {return (b.x-a.x)*(y-a.y)-(b.y-a.y)*(x-a.x);}
    }
    private FirstPersonFramingClientProbe() {}
}
