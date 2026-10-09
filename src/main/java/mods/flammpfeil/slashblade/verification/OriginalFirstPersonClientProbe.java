package mods.flammpfeil.slashblade.verification;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.client.animation.*;
import mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.init.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;

/** The hand hook must sample the original weapon tracks, not the world hand IK.
 * The independent source player and matrix chain preserve the upstream reference. */
public final class OriginalFirstPersonClientProbe {
    public static Map<String,Object> verify() {
        var mc=Minecraft.getInstance();var player=mc.player;
        var saved=player.getMainHandItem();var hand=player.getMainArm();
        var item=new ItemStack(SBItems.slashblade);
        var blade=SBData.get(item,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        var source=new LayerMainBlade();int samples=0,vertices=0;float error=0;
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND,item);
            for(var side:HumanoidArm.values()) {
                player.setMainArm(side);
                for(var combo:ComboState.NONE.getRegistry().values()) {
                    if(combo==ComboState.NONE || !(DefaultResources.ExMotionLocation.equals(combo.getMotionLoc()) || DefaultResources.testLocation.equals(combo.getMotionLoc())))continue;
                    for(float phase:new float[]{0,.05F,.25F,.45F,.8F,1}) {
                        float elapsed=(combo.getEndFrame()-combo.getStartFrame())/(1.5F*combo.getSpeed())*phase;
                        float partial=elapsed-(int)elapsed;
                        blade.setComboSeq(combo);blade.setLastActionTime(mc.level.getGameTime()-(int)elapsed);BladeMotionState.clear();
                        var timeline=BladeAnimationTimeline.resolve(blade,mc.level.getGameTime(),partial);
                        var raw=source.sampleHardpoints(BladeMotionState.Sample.direct(timeline));
                        var expected=new BladeMotionClientProbe.Capture(new Matrix4f());
                        var mesh=BladeRig.capture(item,blade,15728880);
                        var bob=new Matrix4f().translation(.025F,-.015F,.01F).rotateZ(.026F);
                        for(int index=0;index<2;index++) {
                            var stack=new PoseStack();stack.mulPose(bob);stack.mulPose(FirstPersonBladeMotion.view(partial));
                            stack.scale(-1,1,1);stack.mulPose(raw[index]);stack.scale(-1,1,1);stack.scale(.0625F,.0625F,.0625F);
                            (index==0?mesh.blade():mesh.sheath()).submit(stack,expected);
                        }
                        var actual=new BladeMotionClientProbe.Capture(new Matrix4f());
                        var input=new PoseStack();input.mulPose(bob);var before=new Matrix4f(input.last().pose());
                        var event=new RenderHandEvent(InteractionHand.MAIN_HAND,input,actual,15728880,partial,player.getXRot(),0,0,item);
                        NeoForge.EVENT_BUS.post(event);
                        if(!event.isCanceled() || !before.equals(input.last().pose(),.00001F))throw new IllegalStateException("original hand renderer did not restore its input");
                        actual.sameViewGeometry(expected);
                        for(int i=0;i<actual.positions.size();i++)error=Math.max(error,actual.positions.get(i).distance(expected.positions.get(i)));
                        samples++;vertices+=actual.positions.size();
                    }
                }
            }
        } finally {player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.setMainArm(hand);BladeMotionState.clear();}
        var report=new LinkedHashMap<String,Object>();
        report.put("status","passed");report.put("samples",samples);report.put("verticesCompared",vertices);report.put("maxVertexErrorBlocks",error);
        report.put("scope","actual registered hand renderer versus original VMD hardpointA/B on the resolved combat frame, both hands and every supported clip; transitions tested separately");
        report.put("transitions",verifyTransitions(source));
        return report;
    }

    private static Map<String,Object> verifyTransitions(LayerMainBlade reference) {
        var clips=new ArrayList<ComboState>();
        for(var c:ComboState.NONE.getRegistry().values())
            if(c!=ComboState.NONE && (DefaultResources.ExMotionLocation.equals(c.getMotionLoc()) || DefaultResources.testLocation.equals(c.getMotionLoc())))clips.add(c);
        UUID blade=new UUID(0,42);int cases=0;float relativeError=0,boundaryError=0;
        // Every clip can interrupt idle or be interrupted by another draw/SA.
        // Include recoveries, not just the usual A/B/C path.
        for(var clip:clips) for(var destination:new ComboState[]{clip,Extra.EX_COMBO_A1,Extra.EX_COMBO_A2,
                Extra.EX_JUDGEMENT_CUT,Extra.EX_SUPER_SA,Extra.STANDBY_EX}) {
            for(float phase:new float[]{0,.4F,.8F,1}) {
                var previous=new BladeAnimationTimeline(clip,clip.getStartFrame()+(clip.getEndFrame()-clip.getStartFrame())*phase);
                var dense=new FirstPersonBladeMotion.History();var sparse=new FirstPersonBladeMotion.History();
                var before=dense.resolve(blade,90,99.9,previous);sparse.resolve(blade,90,99.9,previous);
                for(int step=0;step<=16;step++) {
                    double dt=step/16.0;
                    var timeline=new BladeAnimationTimeline(destination,Math.min(destination.getEndFrame(),destination.getStartFrame()+(float)(dt*1.5*destination.getSpeed())));
                    var raw=reference.sampleHardpoints(BladeMotionState.Sample.direct(timeline));
                    var out=dense.resolve(blade,100,100+dt,timeline);
                    if(step==0) {
                        boundaryError=Math.max(boundaryError,difference(before[1],out[1]));
                        require(before[1].equals(out[1],.001F),"saya snaps at combo boundary");
                        sparse.resolve(blade,100,100,timeline);
                    }
                    var authored=new Matrix4f(raw[1]).invert().mul(raw[0]);
                    var displayed=new Matrix4f(out[1]).invert().mul(out[0]);
                    relativeError=Math.max(relativeError,difference(authored,displayed));
                    require(authored.equals(displayed,.001F),"transition alters blade/saya relative pose");
                    for(int i=0;i<2;i++) {
                        require(out[i].isFinite() && Math.abs(out[i].determinant()-raw[i].determinant())<.0001F,"transition scales or shears weapon");
                        if(step==16)require(out[i].equals(raw[i],.00001F),"transition delays original cut beyond one tick");
                    }
                    if(step==8 || step==16) {
                        var skipped=sparse.resolve(blade,100,100+dt,timeline);
                        require(out[0].equals(skipped[0],.00001F) && out[1].equals(skipped[1],.00001F),"render rate changes transition");
                    }
                    // A caller must not mutate the cached sample used by another pass.
                    out[0].zero();
                    var repeated=dense.resolve(blade,100,100+dt,timeline);
                    require(repeated[0].isFinite() && Math.abs(repeated[0].determinant())>.9F,"mutable render cache");
                    cases++;
                }
                var reset=new BladeAnimationTimeline(Extra.STANDBY_EX,0);
                var raw=reference.sampleHardpoints(BladeMotionState.Sample.direct(reset));
                for(var out:new Matrix4f[][]{dense.resolve(new UUID(0,43),101,101.1,reset),
                        dense.resolve(blade,99,99,reset),dense.resolve(blade,102,104,reset)})
                    require(out[0].equals(raw[0],.00001F) && out[1].equals(raw[1],.00001F),"stale pose after blade switch, clock reset or late update");
            }
        }
        return Map.of("status","passed","samples",cases,"maxRelativeMatrixError",relativeError,"maxBoundarySayaError",boundaryError,
                "settleTicks",FirstPersonBladeMotion.SETTLE_TICKS,"scope","all clips, interrupted draw/SA/recovery, four outgoing phases; rigid common correction preserves VMD blade/saya relation, ends within one action tick, render-rate independence and cache resets");
    }
    private static float difference(Matrix4f a,Matrix4f b) {
        float error=0;for(int row=0;row<4;row++)for(int column=0;column<4;column++)error=Math.max(error,Math.abs(a.get(column,row)-b.get(column,row)));return error;
    }
    private static void require(boolean ok,String message) {if(!ok)throw new IllegalStateException(message);}
    private OriginalFirstPersonClientProbe() {}
}
