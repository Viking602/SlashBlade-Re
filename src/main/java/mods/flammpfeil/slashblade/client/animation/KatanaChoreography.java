package mods.flammpfeil.slashblade.client.animation;

import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Original katana motion score. Weapons share a pelvis-relative bind space.
 * The sword leads the hand constraint; the left hand changes between saya and the same hilt.
 * Nonuniform keys describe anticipation, cutting acceleration, braking and zanshin separately.
 */
public final class KatanaChoreography {
    private static final float DEG = (float)Math.PI / 180;
    public static final float DRAW_DISTANCE = 13.28F * 320 * BladeRig.MODEL_SCALE;
    public static final float SUPPORT_GRIP = 18F;
    public static final float GRASP_END = .24F, DRAW_CLEAR = .64F;
    public static final float INSERT_START = .55F, INSERT_END = .86F;
    public record Frame(Matrix4f blade, Matrix4f sheath, float support, float mainContact,
                        float offContact, float active, float hip, float chest, float lean, float step, float bank) {
        public Frame(Matrix4f blade,Matrix4f sheath,float support,float mainContact,float offContact,float active,
                     float hip,float chest,float lean,float step) {
            this(blade,sheath,support,mainContact,offContact,active,hip,chest,lean,step,-chest*.10F);
        }
        public Quaternionf spineRotation() { return new Quaternionf().rotationYXZ(chest*DEG,lean*DEG,bank*DEG); }
        public Quaternionf[] rotations() {
            return new Quaternionf[]{new Quaternionf().rotationY(hip*DEG),
                    spineRotation(),
                    new Quaternionf().rotationY(-(hip+chest)*.75F*DEG),
                    new Quaternionf().rotationXYZ(-.45F*active,0,-.15F*active), new Quaternionf().rotationXYZ(-.45F*active,0,.15F*active),
                    new Quaternionf().rotationXYZ(step*DEG, -.05F*active, -.045F*active),
                    new Quaternionf().rotationXYZ(-step*DEG, .08F*active, .045F*active)};
        }
    }
    private record Key(float t, float x, float y, float z, float yaw, float tilt, float roll,
                       float hip, float chest, float lean, float support, float step) {
        float value(int i) { return switch(i) {
            case 0 -> x; case 1 -> y; case 2 -> z; case 3 -> yaw; case 4 -> tilt; case 5 -> roll;
            case 6 -> hip; case 7 -> chest; case 8 -> lean; case 9 -> support; default -> step;
        }; }
    }
    // Horizontal nukitsuke: open the hips before the blade crosses the front, then brake.
    private static final Key[] HORIZONTAL = {
        k(0,-7,6,-4,166,0,90,-13,-18,4,0,7),
        k(.15F,-7,5,-4.5F,190,0,90,-16,-22,5,0,9),
        k(.38F,-3,4,-5.8F,264,0,90,0,-5,7,0,12),
        k(.58F,-1,5,-5,330,-8,90,15,23,6,0,14),
        k(1,-1.5F,6,-4.5F,348,-14,75,12,16,3,0,10)};
    // Return cut with both hands: high outside preparation, oblique cut, low opposite finish.
    private static final Key[] DIAGONAL = {
        k(0,-1.5F,5,-4.5F,345,-15,25,12,16,3,0,10),
        k(.18F,-2,-1,-3,290,67,8,17,20,1,1,9),
        k(.32F,-2,-1.5F,-3,280,78,5,8,17,2,1,11),
        k(.49F,-1,3,-5.5F,256,8,-12,-7,-12,8,1,16),
        k(.68F,-2,7,-4.2F,205,-36,-20,-17,-23,6,1,17),
        k(1,-1.5F,6,-4,225,-16,0,-10,-14,3,1,10)};
    private static final Key[] OVERHEAD = {
        k(0,-1,6,-4,235,-12,0,-10,-14,3,1,10),
        k(.20F,-1,-2,-2.8F,270,84,0,-9,-5,-3,1,8),
        k(.34F,-1,-2.5F,-2.5F,270,105,0,-5,0,-4,1,10),
        k(.50F,-1,2,-5.5F,270,20,0,3,6,9,1,18),
        k(.68F,-1,7,-4.5F,270,-25,0,6,10,8,1,18),
        k(1,-1,6,-4.5F,270,-8,0,2,3,3,1,10)};
    private static final Key[] RISING = {
        k(0,-2,7,-3.5F,205,-45,-25,-15,-20,9,1,13),
        k(.17F,-2.5F,8,-3.8F,220,-58,-25,-17,-23,12,1,16),
        k(.40F,-1,3,-5.5F,265,15,0,2,8,3,1,10),
        k(.62F,-1,-2,-3,295,80,10,14,20,-5,1,-9),
        k(1,-1,0,-4,280,55,5,7,9,0,1,5)};
    private static final Key[] REVERSE = {
        k(0,-1,6,-4,340,-20,55,12,20,5,1,10),
        k(.18F,-2,4,-4,375,-15,70,16,24,7,1,13),
        k(.43F,-1,4,-5.5F,278,5,80,-3,-7,7,1,17),
        k(.64F,-2,5,-4.5F,195,18,75,-16,-24,4,1,16),
        k(1,-1,6,-4,230,4,35,-10,-12,3,1,10)};
    private static final Key[] THRUST = {
        k(0,-2,6,-3,270,0,15,-8,-13,3,1,8),
        k(.22F,-2,5,-2,270,4,15,-14,-18,1,1,10),
        k(.43F,-1,4,-6,270,0,0,8,13,10,1,19),
        k(.61F,-1,4,-6,270,0,0,10,15,9,1,19),
        k(1,-1,6,-4,270,-6,0,2,4,3,1,10)};
    private static final Key[] DOUBLE_CUT = {
        k(0,-1,6,-4,225,-16,0,-10,-14,3,1,10),
        k(.09F,-2,-1,-3,280,68,6,8,12,-1,1,9),
        k(.17F,-1,3,-5.5F,255,5,-10,-4,-8,8,1,16),
        k(.27F,-2,7,-3.5F,210,-45,-25,-16,-22,10,1,17),
        k(.35F,-2,7.5F,-3.5F,215,-52,-25,-14,-20,10,1,15),
        k(.50F,-1,3,-5.5F,267,15,0,2,9,3,1,10),
        k(.65F,-1,-2,-3,295,80,10,14,20,-4,1,6),
        k(1,-1,0,-4,280,55,5,7,9,0,1,7)};
    // A directed cutting arc after clearance prevents slerp taking a backwards shortcut
    // from a side-facing saya to a completed horizontal cut (>180 degrees apart).
    private static final Key[] DRAW_CUT = {
        k(0,8.5F-DRAW_DISTANCE,3.5F,-3.5F,180,0,180,-8,0,0,0,8),
        k(.35F,-5,4.5F,-6,218,0,135,-4,-30,3,0,10),
        k(.68F,-2,4.5F,-5.5F,285,-3,90,8,10,6,0,12),
        k(1,-1.5F,6,-4.5F,348,-14,75,12,16,3,0,10)};
    // Yamato-inspired rapid alternating cuts: keep the support hand on the saya,
    // use a compact wrist path and let the cutting plane alternate through the target.
    // These are original keys retargeted to the Minecraft body, not extracted game assets.
    private static final Key[] FLURRY = {
        k(0,-3,4,-4.5F,210,-25,60,-9,-12,6,0,18),
        k(.22F,-1,3,-5.2F,335,22,110,10,13,7,0,20),
        k(.46F,-3,5,-4.6F,202,-20,65,-10,-14,7,0,18),
        k(.70F,-1,3.5F,-5.2F,337,28,110,11,14,7,0,20),
        k(1,-3,4,-4.5F,210,-25,60,-9,-12,6,0,18)};
    private static final Key[] POWER_CROSS = {
        k(0,-1,6,-4,225,-16,0,-10,-14,3,1,10),
        k(.15F,-2,-1,-3,280,75,5,12,18,-3,1,16),
        k(.20F,-1,3,-5.5F,255,5,-10,-4,-8,9,1,22),
        k(.22F,-2,7,-3.5F,210,-45,-25,-17,-23,10,1,22),
        k(.25F,-1,-1,-3,295,70,10,14,20,-3,1,15),
        k(.28F,-1,4,-5.5F,255,0,-10,-4,-8,9,1,22),
        k(.42F,-1.5F,6,-4,225,-16,0,-10,-14,3,1,10),
        k(1,-1.5F,6,-4,225,-16,0,-10,-14,3,1,10)};
    private static Key k(float t,float x,float y,float z,float yaw,float tilt,float roll,
                         float hip,float chest,float lean,float support,float step) {
        return new Key(t,x,y,z,yaw,tilt,roll,hip,chest,lean,support,step);
    }
    public static Matrix4f transform(float x,float y,float z,float yaw,float tilt,float roll) {
        return new Matrix4f().translation(x/16,y/16,z/16).rotateY(yaw*DEG).rotateZ(tilt*DEG).rotateX(roll*DEG);
    }
    public static Frame idle() {
        // The mouth sits outside the left waist; the length follows the flank backwards.
        // +X of the OBJ points towards its pommel, -X towards the closed saya tip.
        // The built-in OBJ's cutting edge is +Y. Carry and draw it edge-up (-Y).
        var dock = transform(4.7F,5.5F,-4.7F,106,-8,180);
        return new Frame(dock,new Matrix4f(dock),0,0,0,0,0,0,0,0);
    }
    private static Frame ready() {
        return sample(OVERHEAD,1);
    }
    private static Frame clear() {
        Matrix4f saya = transform(8.5F,3.5F,-3.5F,180,0,180);
        return new Frame(new Matrix4f(saya).translate(DRAW_DISTANCE/16,0,0),saya,0,1,1,1,-8,0,0,8,0);
    }
    private static Frame aligned(float pull) {
        Matrix4f saya = BladeRig.rigidBlend(idle().sheath,clear().sheath,pull);
        Matrix4f sword = new Matrix4f(saya).translate(DRAW_DISTANCE/16*pull,0,0);
        // A cross-body grasp needs more shoulder turn on the wide Minecraft torso.
        // Unwind as the right hand extends and the left hand draws the saya back;
        // holding one extreme chest angle throughout the pull looked mechanical.
        return new Frame(sword,saya,0,1,1,1,-8*pull,-68*(1-pull),0,8*pull,0);
    }
    /** Forward draw includes sayabiki; the full blade clears before its cutting plane turns. */
    public static Frame draw(float t, Frame target) {
        t = Math.clamp(t,0,1);
        if (t < GRASP_END) {
            float turn=ease(0,GRASP_END,t);
            return new Frame(idle().blade,idle().sheath,0,ease(.08F,GRASP_END,t),ease(.02F,.18F,t),turn,
                    0,-68*turn,0,0,0);
        }
        if (t <= DRAW_CLEAR) return aligned(ease(GRASP_END,DRAW_CLEAR,t));
        return freeCut((t-DRAW_CLEAR)/(1-DRAW_CLEAR),target);
    }
    private static Frame freeCut(float cut,Frame target) {
        Frame arc=sample(DRAW_CUT,cut,false);
        arc=new Frame(arc.blade,BladeRig.rigidBlend(clear().sheath,idle().sheath,ease(0,1,cut)),0,1,1,1,
                arc.hip,arc.chest,arc.lean,arc.step,arc.bank);
        return mix(arc,target,ease(.25F,1,cut));
    }
    public static float withdrawal(Frame frame) {
        return Math.clamp(new Matrix4f(frame.sheath).invert().mul(frame.blade).m30()*16/DRAW_DISTANCE,0,1);
    }
    /** Detect actual occupancy, including an interrupted insertion, rather than combo names. */
    public static boolean containsBlade(Frame frame) {
        if(frame.support>.001F) return false;
        Matrix4f relative=new Matrix4f(frame.sheath).invert().mul(frame.blade);
        float x=relative.m30();
        return x>=-.0001F && x<DRAW_DISTANCE/16-.0001F
                && relative.equals(new Matrix4f().translation(x,0,0),.0001F);
    }
    /** A renewed attack must finish extracting an occupied blade before rotating it. */
    public static Frame resumeDraw(float t,Frame source,Frame target) {
        if(t<=0) return source;
        if(t>=1) return target;
        float start=withdrawal(source);
        float clearAt=DRAW_CLEAR*(1-start)/(1-DRAW_CLEAR*start);
        if(t>=clearAt) return freeCut((t-clearAt)/(1-clearAt),target);
        float grasp=Math.min(clearAt*.375F,GRASP_END*(1-Math.min(source.mainContact,source.offContact)));
        Frame held=aligned(start);
        held=new Frame(source.blade,source.sheath,0,1,1,1,held.hip,held.chest,held.lean,held.step,held.bank);
        if(t<grasp) return mix(source,held,ease(0,grasp,t));
        // Closed blade and saya always share the same interpolated rotation/axis.
        float pull=ease(grasp,clearAt,t);
        Frame body=mix(grasp>0 ? held : source,clear(),pull);
        Matrix4f saya=BladeRig.rigidBlend(source.sheath,clear().sheath,pull);
        return new Frame(new Matrix4f(saya).translate(DRAW_DISTANCE/16*lerp(start,1,pull),0,0),saya,
                0,1,1,1,body.hip,body.chest,body.lean,body.step,body.bank);
    }
    /** Interrupted recovery closes from the displayed depth instead of replaying the search. */
    public static Frame finishSheath(float t,Frame source) {
        float start=withdrawal(source);
        if(start<.0001F) return mix(source,idle(),ease(0,1,t));
        if(t>=.75F) return mix(aligned(0),idle(),ease(.75F,1,t));
        float insert=ease(0,.75F,t);
        Frame body=mix(source,aligned(0),insert);
        Matrix4f saya=BladeRig.rigidBlend(source.sheath,idle().sheath,insert);
        return new Frame(new Matrix4f(saya).translate(DRAW_DISTANCE/16*start*(1-insert),0,0),saya,
                0,body.mainContact,body.offContact,body.active,body.hip,body.chest,body.lean,body.step,body.bank);
    }
    /** Deliberately different from a reversed attack: settle, lower, find mouth, insert, release. */
    public static Frame sheath(float t, Frame source) {
        t = Math.clamp(t,0,1);
        Frame low = new Frame(transform(-3,6,-5,275,-30,180),idle().sheath,0,1,1,1,4,-12,3,7);
        if (t < .18F) return mix(source,low,ease(0,.18F,t));
        // Rest the spine above the left thumb, slide it along the guide, locate the tip.
        // This is distinct from reversing the attack or snapping the sword onto the mouth.
        Frame guide=guided(.25F,1);
        if (t < .34F) {
            float u=ease(.18F,.34F,t);
            Frame body=mix(low,guide,u);
            // Return across the front of the waist. The left hand turns the mouth
            // towards the approaching spine before the right hand extends outwards.
            Matrix4f sword=BladeRig.rigidBlend(low.blade,guide.blade,u);
            float arch=(float)Math.sin(Math.PI*u);
            sword.m31(sword.m31()-1F/16*arch);
            sword.m32(sword.m32()-2F/16*arch);
            return new Frame(sword,body.sheath,0,1,1,1,body.hip,body.chest,body.lean,body.step,body.bank);
        }
        if (t < INSERT_START) return guided(lerp(.25F,1,ease(.34F,INSERT_START,t)),1-ease(.34F,INSERT_START,t));
        if (t < INSERT_END) {
            float insert = ease(INSERT_START,INSERT_END,t);
            return aligned(1-insert);
        }
        float release = 1-ease(INSERT_END,1,t);
        return new Frame(idle().blade,idle().sheath,0,release,release,release,0,-68*release,0,0,0);
    }
    private static Frame guided(float pull,float lift) {
        Frame axis=aligned(pull);
        // With the edge-up bind, +Y points upwards; keep the guide above the saya.
        return new Frame(new Matrix4f(axis.blade).translate(0,.7F/16*lift,0),axis.sheath,0,1,1,1,
                axis.hip,axis.chest,axis.lean,axis.step,axis.bank);
    }
    public static boolean judgementRelease(String name) {
        return name.startsWith("ex_judgement_cut_slash") && !name.contains("sheath");
    }
    private static Frame oneHand(Frame frame) {
        return new Frame(frame.blade,idle().sheath,0,1,1,frame.active,
                frame.hip,frame.chest,frame.lean,frame.step,frame.bank);
    }
    private static Frame judgement(float t) {
        // Compact draw/cut followed by a readable, still finishing silhouette.
        // The history controller handles extraction when this interrupts an occupied saya.
        return oneHand(sample(HORIZONTAL,Math.clamp(t/.32F,0,1)));
    }
    private static Frame preparation(float t) {
        return draw(GRASP_END*ease(0,.55F,t),idle());
    }
    private static Frame flurry(float t) { return sample(FLURRY,Math.clamp(t,0,1)); }
    public static Frame mix(Frame a, Frame b, float t) {
        return new Frame(BladeRig.rigidBlend(a.blade,b.blade,t),BladeRig.rigidBlend(a.sheath,b.sheath,t),
                lerp(a.support,b.support,t),lerp(a.mainContact,b.mainContact,t),lerp(a.offContact,b.offContact,t),
                lerp(a.active,b.active,t),lerp(a.hip,b.hip,t),lerp(a.chest,b.chest,t),lerp(a.lean,b.lean,t),lerp(a.step,b.step,t),lerp(a.bank,b.bank,t));
    }
    public static Frame sample(BladeAnimationTimeline timeline) {
        ComboState combo = timeline.combo();
        String name = combo.getName();
        float t = (timeline.frame()-combo.getStartFrame())/Math.max(1,combo.getEndFrame()-combo.getStartFrame());
        t=Math.clamp(t,0,1);
        if(name.startsWith("resharped_")) return resharped(name.substring(10),t);
        if(name.equals("ex_judgement_cut")) return preparation(t);
        if(name.startsWith("ex_judgement_cut")) {
            if(name.contains("sheath")) return sheath(t,judgement(1));
            if(name.endsWith("just2")) return judgement(1);
            return judgement(t);
        }
        if(name.equals("ex_super_sa")) {
            if(t<.22F) return preparation(t/.22F);
            if(t<.42F) return resumeDraw((t-.22F)/.20F,aligned(0),judgement(1));
            return judgement(1);
        }
        if (name.endsWith("_loop")) return sample(OVERHEAD,1);
        if (name.endsWith("_landing")) return mix(sample(OVERHEAD,1),ready(),ease(0,1,t));
        // B's first "end" still contains its finishing attack events.
        if (name.equals("ex_combo_b1_end") || name.equals("ex_combo_b_end")) return sample(OVERHEAD,t);
        int end = name.indexOf("_end");
        if (end >= 0) {
            String group = name.substring(0,end);
            boolean finisher = group.equals("ex_combo_b1") || group.equals("ex_combo_b");
            String firstName = group+(finisher ? "_end2" : "_end");
            ComboState first = ComboState.NONE.getRegistry().values().stream()
                    .filter(c -> c.getName().equals(firstName)).findFirst().orElse(null);
            if (first != null) {
                float total=0, elapsed=0; boolean found=false;
                ComboState segment=first;
                for (int i=0;i<10 && segment!=ComboState.NONE && segment.getName().contains("_end");i++) {
                    float span=Math.max(1,segment.getEndFrame()-segment.getStartFrame())/segment.getSpeed();
                    if (segment==combo) { elapsed=total+t*span; found=true; }
                    total+=span; segment=segment.getNextOfTimeout();
                }
                Frame source=finisher ? sample(OVERHEAD,1) : group.equals("ex_aerial_cleave") ? ready() : attack(group,1);
                if (found) return sheath(elapsed/Math.max(1,total),source);
            }
            return sheath(t,ready());
        }
        if (name.contains("_sheath")) return sheath(t,attack(name,1));
        return attack(name,Math.clamp(t,0,1));
    }
    private static Frame resharped(String name,float t) {
        if(name.startsWith("sakura_end")) {
            if(name.contains("finish2"))return sheath(t,oneHand(sample(REVERSE,1)));
            if(name.contains("finish"))return oneHand(sample(REVERSE,1));
            if(name.contains("right")) return t<.16F
                    ? mix(oneHand(sample(HORIZONTAL,1)),oneHand(sample(REVERSE,0)),ease(0,.16F,t))
                    : oneHand(sample(REVERSE,(t-.16F)/.84F));
            return oneHand(sample(HORIZONTAL,t));
        }
        if(name.startsWith("piercing")) {
            if(name.equals("piercing"))return mix(ready(),sample(THRUST,0),ease(0,1,t));
            if(name.endsWith("end2"))return sheath(t,sample(THRUST,1));
            if(name.endsWith("end"))return sample(THRUST,1);
            return sample(THRUST,Math.clamp(t*2,0,1));
        }
        if(name.startsWith("void_slash"))return name.contains("sheath")?sheath(t,judgement(1)):judgement(t);
        if(name.equals("judgement_cut_end"))return judgement(t);
        if(name.startsWith("circle_slash")) {
            if(name.contains("end"))return sheath(name.endsWith("end2")?(21+23*t)/44F:21*t/44F,oneHand(sample(HORIZONTAL,1)));
            var cut=oneHand(sample(HORIZONTAL,t));
            return new Frame(cut.blade,cut.sheath,cut.support,cut.mainContact,cut.offContact,cut.active,cut.hip,cut.chest,cut.lean,cut.step,cut.bank);
        }
        boolean vertical=name.contains("vertical");var path=vertical?OVERHEAD:HORIZONTAL;
        if(name.endsWith("_end"))return sheath(t,sample(path,1));
        return sample(path,Math.clamp(t*2,0,1));
    }
    public static boolean continuous(BladeAnimationTimeline a, BladeAnimationTimeline b) {
        var end=sample(new BladeAnimationTimeline(a.combo(),a.combo().getEndFrame()));
        var start=sample(new BladeAnimationTimeline(b.combo(),b.combo().getStartFrame()));
        return end.blade.equals(start.blade,.0001F) && end.sheath.equals(start.sheath,.0001F)
                && Math.abs(end.support-start.support)<.0001F && Math.abs(end.active-start.active)<.0001F
                && Math.abs(end.hip-start.hip)+Math.abs(end.chest-start.chest)+Math.abs(end.lean-start.lean)+Math.abs(end.step-start.step)+Math.abs(end.bank-start.bank)<.001F;
    }
    private static Frame attack(String name,float t) {
        if(name.equals("ex_combo_b1")) return t<.4F
                ? mix(oneHand(sample(DOUBLE_CUT,1)),flurry(0),ease(0,.4F,t)) : flurry((t-.4F)/.6F);
        if(name.matches("ex_combo_b[2-6]")) return flurry(t);
        if(name.equals("ex_combo_b7")) return t<.22F ? flurry(t/.22F)
                : sample(OVERHEAD,Math.clamp((t-.22F)/.24F,0,1));
        if(name.equals("ex_combo_c")) return oneHand(sample(DOUBLE_CUT,Math.clamp(t/.15F,0,1)));
        if(name.equals("ex_combo_a2")) return oneHand(sample(REVERSE,Math.clamp(t/.65F,0,1)));
        if(name.equals("ex_combo_a3")) return oneHand(sample(DOUBLE_CUT,t));
        if(name.equals("ex_combo_a4")) return sample(OVERHEAD,Math.clamp(t/.32F,0,1));
        if(name.equals("ex_combo_a4ex")) return oneHand(sample(DOUBLE_CUT,Math.clamp(t/.95F,0,1)));
        if(name.equals("ex_combo_a5ex")) return sample(POWER_CROSS,t);
        Key[] score;
        if (name.contains("upperslash") || name.contains("rising")) score=RISING;
        else if (name.contains("cleave") || name.contains("a4") || name.contains("super_sa")) score=OVERHEAD;
        else if (name.contains("a3")) score=DOUBLE_CUT;
        else if (name.contains("a2") || name.contains("combo_c")) score=DIAGONAL;
        else if (name.contains("combo_b") || name.contains("a5")) {
            int digit = name.charAt(name.length()-1)-'0';
            score = digit%3==0 ? THRUST : digit%2==0 ? REVERSE : DIAGONAL;
        }
        else score=HORIZONTAL;
        return sample(score,t);
    }
    private static Frame sample(Key[] keys,float t) {
        return sample(keys,t,true);
    }
    private static Frame sample(Key[] keys,float t,boolean chestLocal) {
        int segment=0;
        while(segment+2<keys.length && t>keys[segment+1].t) segment++;
        float[] v=new float[11];
        for(int c=0;c<v.length;c++) v[c]=curve(keys,segment,c,t);
        float support=Math.clamp(v[9],0,1);
        Matrix4f blade=transform(v[0],v[1],v[2],v[3],v[4],v[5]);
        if(chestLocal) blade=new Matrix4f().translation(0,12F/16,0)
                .rotateYXZ(v[7]*DEG,v[8]*DEG,-v[7]*.10F*DEG).translate(0,-12F/16,0).mul(blade);
        return new Frame(blade,idle().sheath,support,1,1-support,1,
                v[6],v[7],v[8],v[10],chestLocal ? -v[7]*.10F : 0);
    }
    // Shape-preserving cubic Hermite: continuous velocity without pausing at every key.
    private static float curve(Key[] k,int i,int channel,float t) {
        float duration=k[i+1].t-k[i].t, u=Math.clamp((t-k[i].t)/duration,0,1);
        float a=k[i].value(channel),b=k[i+1].value(channel);
        float m0=slope(k,i,channel)*duration,m1=slope(k,i+1,channel)*duration;
        return (2*u*u*u-3*u*u+1)*a+(u*u*u-2*u*u+u)*m0+(-2*u*u*u+3*u*u)*b+(u*u*u-u*u)*m1;
    }
    private static float slope(Key[] k,int i,int c) {
        if(i==0 || i==k.length-1) return 0;
        float a=(k[i].value(c)-k[i-1].value(c))/(k[i].t-k[i-1].t);
        float b=(k[i+1].value(c)-k[i].value(c))/(k[i+1].t-k[i].t);
        return a*b<=0 ? 0 : 2*a*b/(a+b);
    }
    public static float ease(float a,float b,float t) { return LimbSkinning.smooth((t-a)/(b-a)); }
    private static float lerp(float a,float b,float t) { return a+(b-a)*t; }
    private KatanaChoreography() {}
}
