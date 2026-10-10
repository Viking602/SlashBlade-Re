package mods.flammpfeil.slashblade.verification;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Measures the actual resolved item model and deferred vertex callbacks, without controlling the UI. */
public final class BladeIconClientProbe {
    public static Map<String, Object> verify() {
        var mc = Minecraft.getInstance();
        var results = new ArrayList<Map<String, Object>>();
        int cases = 0;
        double margin = Double.POSITIVE_INFINITY;
        for (String model : new String[]{"blade", "named/agito", "named/yamato", "named/muramasa/muramasa", "named/sange/sange"}) {
            for (int variant = 0; variant < 3; variant++) for (float damage : new float[]{0, .25F, .5F, .75F, .99F, 1})
                for (boolean glint : new boolean[]{false, true}) {
                    var stack = new ItemStack(SBItems.slashblade);
                    var blade = SBData.get(stack, ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
                    blade.setModel(SlashBlade.id("model/" + model + ".obj"));
                    blade.setDamage(damage);
                    blade.setBroken(variant == 2);
                    blade.setNoScabbard(variant == 1);
                    stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint);
                    var state = new ItemStackRenderState();
                    mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, null, null, 0);
                    require(!state.isEmpty(), "item definition missing");
                    require(!state.usesBlockLight(), "GUI lighting differs from the flat item setting");
                    var reported = state.getModelBoundingBox();
                    var capture = new Capture();
                    state.submit(new PoseStack(), capture, 15728880, OverlayTexture.NO_OVERLAY, 0);
                    require(capture.all.vertices > 0 && capture.first.vertices > 0, "special renderer did not submit geometry");
                    double edge = Math.min(Math.min(capture.all.minX + .5, .5 - capture.all.maxX),
                            Math.min(capture.all.minY + .5, .5 - capture.all.maxY)) * 16;
                    var reference=referenceBounds(model,variant);
                    require(Math.abs(reference.minX-capture.first.minX)<.00001 && Math.abs(reference.maxX-capture.first.maxX)<.00001
                            && Math.abs(reference.minY-capture.first.minY)<.00001 && Math.abs(reference.maxY-capture.first.maxY)<.00001,
                            "icon differs from Resharped's authored GUI transform: "+model+"/"+variant);
                    require(reported.minX<=capture.all.minX+.00001 && reported.maxX>=capture.all.maxX-.00001
                            && reported.minY<=capture.all.minY+.00001 && reported.maxY>=capture.all.maxY-.00001,"model bounds omit rendered geometry");
                    double size = Math.max(capture.first.maxX - capture.first.minX, capture.first.maxY - capture.first.minY) * 16;
                    margin = Math.min(margin, edge);
                    cases++;
                    if (!glint && damage == 0) {
                        var result = new LinkedHashMap<String, Object>();
                        result.put("model", model); result.put("variant", variant); result.put("longEdgePixels", size);
                        result.put("widthPixels", (capture.first.maxX - capture.first.minX) * 16);
                        result.put("heightPixels", (capture.first.maxY - capture.first.minY) * 16);
                        result.put("edgeMarginPixels", edge); results.add(result);
                    }
                }
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "passed"); report.put("cases", cases); report.put("minimumEdgeMarginPixels", margin);
        report.put("scope", "real ItemModelResolver and deferred geometry vs independent Resharped GUI matrix and OBJ vertices; 5 models, 3 variants, 6 damage states, glint on/off");
        report.put("models", results);
        SlashBlade.LOGGER.info("Blade icon verification PASSED: cases={} minimumEdgeMarginPixels={}", cases, margin);
        return report;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Blade icon verification failed: " + message);
    }

    private static Bounds referenceBounds(String name,int variant) {
        // Resharped slashblade.json: translation [2,3,0]/16, XYZ rotation
        // [15,-25,-5], scale .65; TEISR then centers the OBJ and scales by .008.
        // ItemTransform's -.5 and TEISR's +.5 cancel each other.
        var matrix=new org.joml.Matrix4f().translation(2/16F,3/16F,0)
                .rotateXYZ((float)Math.toRadians(15),(float)Math.toRadians(-25),(float)Math.toRadians(-5)).scale(.65F*.008F);
        var model=mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager.getInstance().getModel(SlashBlade.id("model/"+name+".obj"));
        String group=variant==2?"item_damaged":variant==1?"item_bladens":"item_blade";
        var bounds=new Bounds();
        for(var part:model.groupObjects)if(part.name.equals(group))for(var face:part.faces)for(var v:face.vertices) {
            var p=matrix.transformPosition(new org.joml.Vector3f(v.x,v.y,v.z));bounds.addVertex(p.x,p.y,p.z);
        }
        require(bounds.vertices>0,"missing reference OBJ group "+group);return bounds;
    }

    private static final class Capture extends SubmitNodeStorage {
        final Bounds all = new Bounds(), first = new Bounds();
        private int batches;
        @Override public void submitCustomGeometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
            var bounds = new Bounds();
            renderer.render(pose.last().copy(), bounds);
            all.include(bounds);
            if (batches++ == 0) first.include(bounds);
        }
    }

    private static final class Bounds implements VertexConsumer {
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        int vertices;
        void include(Bounds bounds) {
            minX = Math.min(minX, bounds.minX); minY = Math.min(minY, bounds.minY);
            maxX = Math.max(maxX, bounds.maxX); maxY = Math.max(maxY, bounds.maxY); vertices += bounds.vertices;
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            require(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z), "nonfinite vertex");
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); vertices++; return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setColor(int argb) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
    private BladeIconClientProbe() {}
}
