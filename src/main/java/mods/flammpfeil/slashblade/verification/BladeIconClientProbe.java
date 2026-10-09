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
                    require(reported.minX >= -.476 && reported.maxX <= .476 && reported.minY >= -.476 && reported.maxY <= .476,
                            "model reports inflated GUI extents");
                    var capture = new Capture();
                    state.submit(new PoseStack(), capture, 15728880, OverlayTexture.NO_OVERLAY, 0);
                    require(capture.all.vertices > 0 && capture.first.vertices > 0, "special renderer did not submit geometry");
                    double edge = Math.min(Math.min(capture.all.minX + .5, .5 - capture.all.maxX),
                            Math.min(capture.all.minY + .5, .5 - capture.all.maxY)) * 16;
                    require(edge >= .399, "icon is clipped: " + model + "/" + variant + "/" + damage);
                    double size = Math.max(capture.first.maxX - capture.first.minX, capture.first.maxY - capture.first.minY) * 16;
                    require(size >= (variant == 2 ? 14.0 : 15.19), "blade silhouette is undersized: " + model + " size=" + size);
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
        report.put("scope", "real ItemModelResolver + SpecialModelRenderer + deferred geometry, 5 models, 3 variants, 6 damage states, glint on/off; no GPU pixel assertion");
        report.put("models", results);
        SlashBlade.LOGGER.info("Blade icon verification PASSED: cases={} minimumEdgeMarginPixels={}", cases, margin);
        return report;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Blade icon verification failed: " + message);
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
