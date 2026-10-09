package mods.flammpfeil.slashblade.verification;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.client.animation.BladeMotionState;
import mods.flammpfeil.slashblade.client.animation.PlayerBladeAnimation;
import mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade;
import mods.flammpfeil.slashblade.client.renderer.layers.LivingBladeLayer;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Opt-in renderer regressions; only invoked by the marked, disposable client fixtures. */
public final class BladeMotionClientProbe {
    public static Map<String, Object> verifyTransitions() {
        int cases = 0;
        var layer = new LayerMainBlade();
        UUID blade = UUID.randomUUID();
        var initial = new BladeAnimationTimeline(Extra.EX_COMBO_A1, 6);
        var idle = new BladeAnimationTimeline(Extra.STANDBY_EX, 0);
        for (var combo : ComboState.NONE.getRegistry().values()) {
            if (!DefaultResources.ExMotionLocation.equals(combo.getMotionLoc()) || combo == ComboState.NONE
                    || combo == Extra.STANDBY_EX || combo == Extra.STANDBY_INAIR) continue;
            var history = new BladeMotionState.History();
            var before = history.resolve(blade, 0, 100, initial);
            var target = new BladeAnimationTimeline(combo, combo.getStartFrame());
            var boundary = history.resolve(blade, 1, 101, target);
            // An adjacent clip intentionally needs no extra transition.
            if (boundary.blending()) {
                samePose(PlayerBladeAnimation.sample(before), PlayerBladeAnimation.sample(boundary));
                sameMatrices(layer.sampleHardpoints(before), layer.sampleHardpoints(boundary));
                var middle = history.resolve(blade, 1, 102, target);
                checkRigid(layer.sampleHardpoints(middle));
                // A second action interrupts the half-finished transition. It
                // must start from the pose already on screen, including the blend.
                var interrupted = history.resolve(blade, 2, 102, new BladeAnimationTimeline(Extra.EX_VOID_SLASH, 2242));
                samePose(PlayerBladeAnimation.sample(middle), PlayerBladeAnimation.sample(interrupted));
                sameMatrices(layer.sampleHardpoints(middle), layer.sampleHardpoints(interrupted));
                cases += 4;
            }
            history = new BladeMotionState.History();
            history.resolve(blade, 0, 100, idle);
            var start = history.resolve(blade, 1, 101, target);
            var neutral = PlayerBladeAnimation.sample(start);
            require(neutral != null, "attack start omitted the native animation callback");
            for (int bone = 0; bone < 7; bone++) for (int axis = 0; axis < 6; axis++)
                require(Math.abs(neutral.component(bone, axis)) < .00001, "attack start jumps from idle");
            for (double offset : new double[]{.125, .25, .5, 1, 1.5, 1.875, 2}) {
                var sample = history.resolve(blade, 1, 101 + offset, target);
                var pose = PlayerBladeAnimation.sample(sample);
                for (int bone = 0; bone < 7; bone++) for (int axis = 0; axis < 6; axis++)
                    require(Float.isFinite(pose.component(bone, axis)), "nonfinite transition pose");
                checkRigid(layer.sampleHardpoints(sample)); cases++;
            }
            var complete = history.resolve(blade, 1, 101 + BladeMotionState.DRAW_TICKS, target);
            require(!complete.blending(), "transition never completes");
            samePose(PlayerBladeAnimation.sample(target), PlayerBladeAnimation.sample(complete));
            double recoveryStart = 102 + BladeMotionState.DRAW_TICKS;
            var recover = history.resolve(blade, 2, recoveryStart, idle);
            samePose(PlayerBladeAnimation.sample(complete), PlayerBladeAnimation.sample(recover));
            require(PlayerBladeAnimation.sample(history.resolve(blade, 2, recoveryStart + BladeMotionState.SHEATH_TICKS, idle)) == null,
                    "recovery does not restore vanilla movement");
            cases += 3;
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "passed"); report.put("cases", cases);
        report.put("transitionTicks", BladeMotionState.TRANSITION_TICKS);
        report.put("scope", "all supported clips: idle entry, action boundary, interrupted blend, exact completion, idle recovery; body quaternions and blade/sheath rigid matrices");
        SlashBlade.LOGGER.info("Blade motion continuity verification PASSED: cases={}", cases);
        return report;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> verifyFirstPerson() {
        var mc = Minecraft.getInstance();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.hideGui = false;
        var sword = new ItemStack(SBItems.slashblade);
        mc.player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        var blade = SBData.get(sword, ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        UserPoseOverrider.resetRot(mc.player);
        var parity=OriginalFirstPersonClientProbe.verify();
        int cases=(Integer)parity.get("samples"),vertices=(Integer)parity.get("verticesCompared");
        var ordinary = new RenderHandEvent(InteractionHand.MAIN_HAND, new PoseStack(), new Capture(new Matrix4f()),
                15728880, .5F, 0, 0, 0, new ItemStack(Items.DIAMOND_SWORD));
        NeoForge.EVENT_BUS.post(ordinary);
        require(!ordinary.isCanceled(), "ordinary Minecraft item was intercepted");
        var offhand = new RenderHandEvent(InteractionHand.OFF_HAND, new PoseStack(), new Capture(new Matrix4f()),
                15728880, .5F, 0, 0, 0, sword);
        NeoForge.EVENT_BUS.post(offhand);
        require(!offhand.isCanceled(), "offhand was intercepted by the main-hand animation");

        mc.player.setMainArm(HumanoidArm.RIGHT);
        mc.player.setXRot(0); mc.player.setYRot(0);
        blade.setComboSeq(Extra.EX_SUPER_SA); blade.setLastActionTime(mc.level.getGameTime() - 5);
        BladeMotionState.clear();
        var renderer = (AvatarRenderer<AbstractClientPlayer>) mc.getEntityRenderDispatcher().getPlayerRenderers().get(PlayerModelType.SLIM);
        var state = renderer.createRenderState(mc.player, .5F);
        state.setRenderData(LivingBladeLayer.ROTATION, new Matrix4f());
        var input = new PoseStack(); input.translate(.25, -.5, .75);
        Matrix4f before = new Matrix4f(input.last().pose());
        NeoForge.EVENT_BUS.post(new RenderLivingEvent.Pre<>(state, renderer, .5F, input, new SubmitNodeStorage()));
        require(input.last().pose().equals(before, .00001F), "player body transform was applied to sword geometry a second time");
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "passed"); report.put("cameraCases", cases); report.put("verticesCompared", vertices);
        report.put("originalMotionReference",parity);
        report.put("attackVisibility", verifyAttackVisibility());
        report.put("scope", "original VMD first-person weapon geometry and combat clock; vanilla/offhand pass-through; no duplicated third-person body transform");
        SlashBlade.LOGGER.info("Blade camera verification PASSED: cases={} vertices={}", cases, vertices);
        return report;
    }

    /** Measure submitted sword vertices across real clip time, not just the entry pose.
     * Hide skin and saya for this measurement so moving arms/effects cannot mask a static sword. */
    private static java.util.Map<String,Object> verifyAttackVisibility() {
        var mc=Minecraft.getInstance();
        var sword=new ItemStack(SBItems.slashblade);
        var blade=SBData.get(sword,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        blade.setNoScabbard(true);
        mc.player.setItemInHand(InteractionHand.MAIN_HAND,sword);
        boolean invisible=mc.player.isInvisible();
        mc.player.setInvisible(true);
        mc.gameRenderer.getMainCamera().update(mc.getDeltaTracker());
        var results=new java.util.LinkedHashMap<String,Object>();
        try {
            for(var hand:HumanoidArm.values()) {
                mc.player.setMainArm(hand);
                for(var combo:new ComboState[]{Extra.EX_COMBO_A1,Extra.EX_COMBO_A2,Extra.EX_COMBO_A3,
                        Extra.EX_COMBO_A4,Extra.EX_COMBO_A4EX,Extra.EX_COMBO_A5EX,Extra.EX_COMBO_B2,
                        Extra.EX_COMBO_C,Extra.EX_JUDGEMENT_CUT_SLASH,Extra.EX_JUDGEMENT_CUT_SLASH_JUST,Extra.EX_SUPER_SA}) {
                    float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY;
                    float minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
                    int visible=0;
                    // Dense enough to resolve the brief Super SA cut inside its
                    // much longer charge/recovery; 81 samples undersample it.
                    for(int frame=0;frame<=320;frame++) {
                        float elapsed=(combo.getEndFrame()-combo.getStartFrame())/1.5F/combo.getSpeed()*frame/320F;
                        blade.setComboSeq(combo);blade.setLastActionTime(mc.level.getGameTime()-(int)elapsed);
                        BladeMotionState.clear();
                        var capture=new Capture(new Matrix4f());
                        var event=new RenderHandEvent(InteractionHand.MAIN_HAND,new PoseStack(),capture,15728880,
                                elapsed-(int)elapsed,0,0,0,sword);
                        NeoForge.EVENT_BUS.post(event);
                        require(event.isCanceled() && !capture.positions.isEmpty(),"attack never submits first-person blade: "+combo.getName());
                        // A physical avatar may hold the hilt below the viewport
                        // while the cutting edge crosses it, or prepare overhead.
                        // Measure actual visible blade vertices, not its centroid.
                        boolean inView=false;
                        for(var p:capture.positions) {
                            require(Float.isFinite(p.x+p.y+p.z),"nonfinite first-person blade");
                            if(p.z>=-.05F) continue;
                            float x=.5F+p.x/(-p.z*2*.7002075F*1.6F),y=.5F-p.y/(-p.z*2*.7002075F);
                            if(x>0 && x<1 && y>0 && y<1) {
                                minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);inView=true;
                            }
                        }
                        if(inView) visible++;
                    }
                    float travel=Math.max(maxX-minX,maxY-minY);
                    require(visible>=4 && travel>.15F,"first-person cutting edge not visible/moving: "+combo.getName()+" visible="+visible+" travel="+travel);
                    results.put(hand+"/"+combo.getName(),java.util.Map.of("screenTravel",travel,"highestVisibleBladeVertex",minY,"visibleSamples",visible,"samples",321));
                }
            }
        } finally {
            mc.player.setInvisible(invisible);mc.player.setMainArm(HumanoidArm.RIGHT);BladeMotionState.clear();
        }
        return results;
    }

    private static void samePose(PlayerBladeAnimation.Pose a, PlayerBladeAnimation.Pose b) {
        require(a != null && b != null, "missing transition pose");
        sameMatrices(new Matrix4f[]{a.score().blade(),a.score().sheath()},new Matrix4f[]{b.score().blade(),b.score().sheath()});
        for (int bone = 0; bone < 7; bone++) {
            for (int axis = 0; axis < 3; axis++) require(Math.abs(a.component(bone, axis) - b.component(bone, axis)) < .0001,
                    "action boundary jumps a body bone");
            var qa = new Quaternionf().rotationZYX(a.component(bone, 5), a.component(bone, 4), a.component(bone, 3));
            var qb = new Quaternionf().rotationZYX(b.component(bone, 5), b.component(bone, 4), b.component(bone, 3));
            require(Math.abs(qa.dot(qb)) > .99999, "action boundary jumps a body rotation");
        }
    }
    private static void sameMatrices(Matrix4f[] a, Matrix4f[] b) {
        for (int i = 0; i < a.length; i++) require(a[i].equals(b[i], .0001F), "action boundary jumps the blade or sheath");
    }
    private static void checkRigid(Matrix4f[] matrices) {
        for (var matrix : matrices) {
            require(matrix.isFinite(), "nonfinite weapon transform");
            require(Math.abs(matrix.determinant3x3() - 1) < .0001, "transition shears or collapses the weapon");
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    static class Capture extends SubmitNodeStorage {
        final Matrix4f view;
        final ArrayList<Vector3f> positions = new ArrayList<>(), normals = new ArrayList<>();
        Capture(Matrix4f view) { this.view = view; }
        @Override public void submitCustomGeometry(PoseStack input, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
            var pose = new PoseStack(); pose.mulPose(new Matrix4f(view).mul(input.last().pose()));
            renderer.render(pose.last(), new VertexConsumer() {
                @Override public VertexConsumer addVertex(float x, float y, float z) {
                    positions.add(new Vector3f(x, y, z)); return this;
                }
                @Override public VertexConsumer setNormal(float x, float y, float z) {
                    normals.add(new Vector3f(x, y, z)); return this;
                }
                @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
                @Override public VertexConsumer setColor(int argb) { return this; }
                @Override public VertexConsumer setUv(float u, float v) { return this; }
                @Override public VertexConsumer setUv1(int u, int v) { return this; }
                @Override public VertexConsumer setUv2(int u, int v) { return this; }
                @Override public VertexConsumer setLineWidth(float width) { return this; }
            });
        }
        void sameViewGeometry(Capture other) {
            require(positions.size() == other.positions.size() && normals.size() == other.normals.size(), "camera changes submitted geometry");
            for (int i = 0; i < positions.size(); i++) require(positions.get(i).distance(other.positions.get(i)) < .0001,
                    "weapon fails to follow camera yaw/pitch");
            for (int i = 0; i < normals.size(); i++) require(normals.get(i).distance(other.normals.get(i)) < .0001,
                    "weapon normal fails to follow camera yaw/pitch");
        }
    }
    private BladeMotionClientProbe() {}
}
