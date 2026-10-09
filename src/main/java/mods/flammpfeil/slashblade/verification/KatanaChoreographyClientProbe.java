package mods.flammpfeil.slashblade.verification;

import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.client.animation.*;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.HumanoidArm;

/** Check motion intent independently of the old VMD: clearance, timing and both hand roles. */
public final class KatanaChoreographyClientProbe {
    public static Map<String,Object> verify() {
        var a=Extra.EX_COMBO_A1;
        var cut=KatanaChoreography.sample(new BladeAnimationTimeline(a,a.getEndFrame()));
        var clear=KatanaChoreography.draw(KatanaChoreography.DRAW_CLEAR,cut);
        Vector3f tip=new Matrix4f(clear.sheath()).invert().mul(clear.blade())
                .transformPosition(new Vector3f(-291*BladeRig.MODEL_SCALE,0,0));
        require(tip.x > -35.4F*BladeRig.MODEL_SCALE,"blade turns before the tip leaves the saya");
        require(Math.abs(tip.y)+Math.abs(tip.z)<.00001F,"draw does not follow the scabbard axis");
        float maxStep=0;
        for(var combo:new mods.flammpfeil.slashblade.capability.slashblade.ComboState[]{a,Extra.EX_COMBO_A2,Extra.EX_COMBO_A3,Extra.EX_UPPERSLASH}) {
            Matrix4f previous=null;
            for(int i=0;i<=1000;i++) {
                var f=KatanaChoreography.sample(new BladeAnimationTimeline(combo,combo.getStartFrame()+(combo.getEndFrame()-combo.getStartFrame())*i/1000F));
                if(previous!=null) {
                    float distance=previous.transformPosition(new Vector3f(-291*BladeRig.MODEL_SCALE,0,0)).distance(
                            new Matrix4f(f.blade()).transformPosition(new Vector3f(-291*BladeRig.MODEL_SCALE,0,0)));
                    maxStep=Math.max(maxStep,distance);
                    require(distance<.05F,"cut has an internal discontinuity: "+combo.getName()+" step="+i+" distance="+distance);
                }
                previous=new Matrix4f(f.blade());
                if(f.support()==1) require(f.sheath().equals(KatanaChoreography.idle().sheath(),.00001F),"two-hand cut waves the saya with the support hand");
            }
        }
        var idle=BladeMotionState.Sample.direct(new BladeAnimationTimeline(Extra.STANDBY_EX,0));
        int recoveryBoundaries=0;
        for(var c:mods.flammpfeil.slashblade.capability.slashblade.ComboState.NONE.getRegistry().values()) {
            var next=c.getNextOfTimeout();
            if(c.getName().contains("_end") && next.getName().contains("_end")) {
                require(KatanaChoreography.continuous(new BladeAnimationTimeline(c,c.getEndFrame()),new BladeAnimationTimeline(next,next.getStartFrame())),
                        "recovery restarts between segments: "+c.getName()+" -> "+next.getName());
                recoveryBoundaries++;
            }
        }
        var end=BladeMotionState.Sample.direct(new BladeAnimationTimeline(Extra.EX_COMBO_A1_END2,Extra.EX_COMBO_A1_END2.getEndFrame()));
        for(int i=0;i<100;i++) {
            var pose=PlayerBladeAnimation.sample(new BladeMotionState.Sample(idle.current(),end,i/100F));
            require(pose.weight()==0 && pose.score().blade().equals(KatanaChoreography.idle().blade(),.00001F),"completed recovery draws the weapon again at standby");
        }
        var report=new LinkedHashMap<String,Object>();
        report.put("status","passed");report.put("tipClearanceBlocks",tip.x+35.4F*BladeRig.MODEL_SCALE);
        report.put("maximumTipStepAt1000Samples",maxStep);
        report.put("continuousRecoveryBoundaries",recoveryBoundaries);
        report.put("sideMount",verifySideMount());
        report.put("interruptedDraw",verifyInterruptedDraw());
        report.put("yamatoStyle",verifyYamatoStyle());
        report.put("checks","full blade clearance before turning; continuous opposed A3 cuts; actual saya stays on pelvis while chest turns; no repeated sheathing at standby");
        return report;
    }
    private static Map<String,Object> verifyYamatoStyle() {
        int prepared=0;
        for(int i=0;i<=100;i++) {
            var c=Extra.EX_JUDGEMENT_CUT;
            var f=KatanaChoreography.sample(new BladeAnimationTimeline(c,c.getStartFrame()+(c.getEndFrame()-c.getStartFrame())*i/100F));
            require(KatanaChoreography.containsBlade(f),"judgement windup draws the sword prematurely");
            prepared++;
        }
        var just=Extra.EX_JUDGEMENT_CUT_SLASH_JUST;
        var next=Extra.EX_JUDGEMENT_CUT_SLASH_JUST2;
        var end=KatanaChoreography.sample(new BladeAnimationTimeline(just,just.getEndFrame()));
        for(int i=0;i<=100;i++) {
            var held=KatanaChoreography.sample(new BladeAnimationTimeline(next,next.getStartFrame()+(next.getEndFrame()-next.getStartFrame())*i/100F));
            require(end.blade().equals(held.blade(),.00001F),"Just SA replays a second ordinary slash");
        }
        float peakSpeed=0,peakTick=0;
        var c=Extra.EX_COMBO_A4; Vector3f prior=null;
        for(int i=0;i<=1000;i++) {
            var f=KatanaChoreography.sample(new BladeAnimationTimeline(c,c.getStartFrame()+(c.getEndFrame()-c.getStartFrame())*i/1000F));
            var tip=f.blade().transformPosition(new Vector3f(-291*BladeRig.MODEL_SCALE,0,0));
            if(prior!=null && prior.distance(tip)>peakSpeed) {
                peakSpeed=prior.distance(tip);peakTick=(c.getEndFrame()-c.getStartFrame())/1.5F*i/1000F;
            }
            prior=tip;
        }
        require(peakTick>=6 && peakTick<=11,"A4 swing occurs outside its attack event window: "+peakTick);
        return Map.of("closedPreparationSamples",prepared,"justRecoveryHoldSamples",101,"a4PeakSwingTick",peakTick,
                "checks","sheathed SA windup, single Just release, finishing swing near server hit rather than halfway through recovery");
    }
    private static Map<String,Object> verifyInterruptedDraw() {
        int samples=0;
        for(int scenario=0;scenario<6;scenario++) {
            var history=new BladeMotionState.History(); var blade=new java.util.UUID(1,scenario);
            double time=100;
            BladeMotionState.Sample before;
            if(scenario<3) {
                history.resolve(blade,0,90,new BladeAnimationTimeline(Extra.STANDBY_EX,0));
                history.resolve(blade,1,100,new BladeAnimationTimeline(Extra.EX_COMBO_A1,1));
                time+=BladeMotionState.DRAW_TICKS*new float[]{.12F,.35F,.5F}[scenario];
                before=history.resolve(blade,1,time,new BladeAnimationTimeline(Extra.EX_COMBO_A1,8));
            } else before=history.resolve(blade,1,time,new BladeAnimationTimeline(Extra.EX_COMBO_A1_END2,new float[]{31,36,39}[scenario-3]));
            var initial=PlayerBladeAnimation.sample(before).score();
            require(KatanaChoreography.containsBlade(initial),"interruption fixture must start with blade inside saya");
            float depth=KatanaChoreography.withdrawal(initial); boolean guiding=true;
            for(int i=0;i<=300;i++) {
                float elapsed=(float)BladeMotionState.DRAW_TICKS*i/300;
                var next=history.resolve(blade,2,time+elapsed,new BladeAnimationTimeline(Extra.EX_COMBO_A2,100+elapsed*1.5F));
                var f=PlayerBladeAnimation.sample(next).score();
                if(i==0) require(f.blade().equals(initial.blade(),.00001F) && f.sheath().equals(initial.sheath(),.00001F)
                        && Math.abs(f.mainContact()-initial.mainContact())<.00001F,"renewed attack snaps its captured grip");
                Matrix4f relative=new Matrix4f(f.sheath()).invert().mul(f.blade());
                boolean onAxis=relative.equals(new Matrix4f().translation(relative.m30(),0,0),.0001F);
                // Once fully clear, the free cutting arc can remain almost collinear
                // for its first subpixel sample; that is no longer an occupied draw.
                if(guiding && onAxis && KatanaChoreography.containsBlade(f)) {
                    float current=KatanaChoreography.withdrawal(f);
                    require(current+0.00001F>=depth,"renewed attack pushes the blade back in before drawing: scenario="+scenario+" sample="+i+" before="+depth+" after="+current);
                    depth=current;
                } else if(guiding) {
                    require(depth*KatanaChoreography.DRAW_DISTANCE/16>(291-35.4F)*BladeRig.MODEL_SCALE,"interrupted draw turns while tip is inside saya");
                    guiding=false;
                }
                samples++;
            }
            require(!guiding,"renewed attack never reaches its free cutting arc");
        }
        return Map.of("cases",samples,"scenarios",6,"checks","grasp, partial draw, partial insertion and grip release: snapshot continuity, monotonic extraction, full tip clearance before turning");
    }
    private static Map<String,Object> verifySideMount() {
        Matrix4f dock=KatanaChoreography.idle().sheath();
        float minSide=Float.MAX_VALUE;
        // Built-in saya bounds, including its width. Its whole tube must clear the jacket.
        for(float x:new float[]{-296,-35.4F}) for(float y:new float[]{-30,9.4F}) for(float z:new float[]{-4.33F,4.33F})
            minSide=Math.min(minSide,new Matrix4f(dock).transformPosition(new Vector3f(x,y,z).mul(BladeRig.MODEL_SCALE)).x);
        require(minSide>4.5F/16,"saya tube is across the abdomen instead of outside the flank");
        Vector3f axis=dock.transformDirection(new Vector3f(-1,0,0));
        require(axis.z>3*Math.abs(axis.x),"saya does not run backwards along the side");
        int samples=0; float maxError=0;
        for(boolean slim:new boolean[]{false,true}) for(var main:HumanoidArm.values())
            for(boolean view:new boolean[]{false,true}) for(boolean crouch:new boolean[]{false,true}) {
            var model=new PlayerModel(Minecraft.getInstance().getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER),slim);
            var state=new AvatarRenderState(); state.mainArm=main; state.isCrouching=crouch;
            state.skin=DefaultPlayerSkin.get(new java.util.UUID(0,slim ? 0 : 9));
            state.setRenderData(PlayerBladeAnimation.VIEWMODEL,view);
            model.setupAnim(state);
            Vector3f hip=new Vector3f(model.leftLeg.x+model.rightLeg.x,model.leftLeg.y+model.rightLeg.y,model.leftLeg.z+model.rightLeg.z).div(32);
            if(view) hip.y+=1;
            Matrix4f local=new Matrix4f(dock);
            if(view) local.m31(local.m31()-6F/16);
            if(main==HumanoidArm.LEFT) local=new Matrix4f().scaling(-1,1,1).mul(local).scale(1,1,-1);
            Matrix4f expected=new Matrix4f().translation(hip).rotate(PlayerBladeAnimation.vanillaRotation(model.body)).translate(0,-12F/16,0).mul(local);
            for(int step=0;step<=100;step++) {
                var combo=Extra.EX_COMBO_A3;
                var pose=PlayerBladeAnimation.sample(new BladeAnimationTimeline(combo,combo.getStartFrame()+(combo.getEndFrame()-combo.getStartFrame())*step/100F));
                if(pose.score().support()!=1) continue;
                state.setRenderData(PlayerBladeAnimation.POSE,pose); model.setupAnim(state);
                Matrix4f actual=PlayerBladeAnimation.partMatrix(model.root()).invert().mul(BladeRig.attachments(model,state,false)[1]);
                maxError=Math.max(maxError,actual.getTranslation(new Vector3f()).distance(expected.getTranslation(new Vector3f())));
                require(actual.equals(expected,.0001F),"saya follows the upper chest instead of its waist anchor");
                samples++;
            }
        }
        return Map.of("pelvisAnchorCases",samples,"minimumLateralPositionBlocks",minSide,"maximumPelvisAnchorErrorBlocks",maxError);
    }
    private static void require(boolean valid,String message) { if(!valid) throw new IllegalStateException(message); }
    private KatanaChoreographyClientProbe() {}
}
