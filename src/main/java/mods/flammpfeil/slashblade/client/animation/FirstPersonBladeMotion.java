package mods.flammpfeil.slashblade.client.animation;

import mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;

/** First-person weapon tracks from SlashBlade's original VMD, sampled on the
 * synchronized combat clock. The world avatar retains its articulated rig. */
public final class FirstPersonBladeMotion {
    private static LayerMainBlade source=new LayerMainBlade();
    private static final WeakHashMap<LivingEntity, History> histories=new WeakHashMap<>();
    public static final double SETTLE_TICKS=1;
    public static void clearHistory() { histories.clear(); }
    public static void reload() { source=new LayerMainBlade(); clearHistory(); }
    public static Matrix4f[] hardpoints(BladeAnimationTimeline timeline) {
        return source.sampleHardpoints(BladeMotionState.Sample.direct(timeline));
    }

    /** A short common grip correction at an interrupted combo boundary. Blade and
     * saya always receive the SAME rigid correction: their original VMD relation,
     * extraction axis and blade length cannot drift during the transition. */
    public static final class History {
        private UUID blade;
        private long action;
        private double lastTime=Double.NaN, start, end;
        private BladeAnimationTimeline last;
        private Matrix4f[] displayed;
        private final Vector3f offset=new Vector3f();
        private final Quaternionf rotation=new Quaternionf();

        public Matrix4f[] resolve(UUID blade,long action,double time,BladeAnimationTimeline timeline) {
            if(time==lastTime && blade.equals(this.blade) && action==this.action && timeline.equals(last))
                return copy(displayed);
            Matrix4f[] raw=hardpoints(timeline);
            if(displayed==null || !blade.equals(this.blade) || time<lastTime || time-lastTime>2) {
                end=time;
            } else if(action!=this.action) {
                end=time;
                // Finish against the action clock, not accumulated render dt. Never
                // restart the blend at recovery boundaries or on a late packet.
                if(time>=action && time<action+SETTLE_TICKS) {
                    Matrix4f correction=new Matrix4f(displayed[1]).mul(new Matrix4f(raw[1]).invert());
                    correction.getTranslation(offset);
                    correction.getUnnormalizedRotation(rotation).normalize();
                    start=time;end=action+SETTLE_TICKS;
                }
            }
            if(time<end) {
                float progress=(float)Math.clamp((time-start)/(end-start),0,1);
                float weight=1-progress*progress*(3-2*progress);
                var correction=new Matrix4f().translationRotate(new Vector3f(offset).mul(weight),
                        new Quaternionf().slerp(rotation,weight));
                for(int i=0;i<raw.length;i++) raw[i]=new Matrix4f(correction).mul(raw[i]);
            }
            this.blade=blade;this.action=action;lastTime=time;last=timeline;displayed=raw;
            return copy(raw);
        }
        private static Matrix4f[] copy(Matrix4f[] values) {
            return new Matrix4f[]{new Matrix4f(values[0]),new Matrix4f(values[1])};
        }
    }
    public static Matrix4f view(float partialTick) {
        var mc=Minecraft.getInstance();var player=mc.player;
        float rad=(float)Math.PI/180;
        var nativeView=new Quaternionf().rotationYXZ((float)Math.PI-player.getViewYRot(partialTick)*rad,
                -player.getViewXRot(partialTick)*rad,0);
        // Retain the animated head's rotation relative to the user's look, without
        // making the weapon orbit when the user looks up or down.
        var view=new Matrix4f().rotation(new Quaternionf(mc.gameRenderer.getMainCamera().rotation()).conjugate()).rotate(nativeView);
        // Original BladeFirstPersonRender / LayerMainBlade coordinate chain.
        // Uniformly resize the complete authored space to the shared katana size.
        // Keep the resting hilt readable along the lower edge, without enlarging
        // the weapon or adding a separate motion to the saya.
        view.translate(0,.10F,-.5F).rotateZ((float)Math.PI).scale(BladeRig.MODEL_SCALE*128)
                .translate(0,1.5F,0).scale(.125F).rotateZ((float)Math.PI);
        if(player.getMainArm()==HumanoidArm.LEFT) view.scale(-1,1,1);
        return view;
    }
    public static Matrix4f[] transforms(LivingEntity entity,ISlashBladeState blade,BladeMotionState.Sample sample,float partialTick) {
        var raw=histories.computeIfAbsent(entity,ignored->new History()).resolve(blade.getUniqueId(),
                blade.getLastActionTime(),entity.level().getGameTime()+(double)partialTick,sample.current());
        var root=view(partialTick);
        for(int i=0;i<raw.length;i++) raw[i]=new Matrix4f(root).scale(-1,1,1).mul(raw[i]).scale(-1,1,1).scale(.0625F);
        return raw;
    }
    private FirstPersonBladeMotion() {}
}
