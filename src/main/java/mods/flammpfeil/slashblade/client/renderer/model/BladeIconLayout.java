package mods.flammpfeil.slashblade.client.renderer.model;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Fits the GUI silhouette once per loaded OBJ; the sword and its damage gauge have separate sizes. */
public final class BladeIconLayout {
    public static final float BLADE_SPAN = 15.8F / 16.0F;
    private static final float GAUGE_SPAN = 10.5F / 16.0F;
    private final Matrix4f display, projection, inverseProjection;
    private final Map<WavefrontObject, Variants> blades = new IdentityHashMap<>();
    private final Map<WavefrontObject, Fit> gauges = new IdentityHashMap<>();

    public BladeIconLayout(ItemTransform gui) {
        var pose = new PoseStack.Pose();
        gui.apply(false, pose);
        display = new Matrix4f(pose.pose());
        projection = new Matrix4f(display).translate(.5F, .5F, .5F).scale(.008F);
        inverseProjection = new Matrix4f(projection).invert();
    }

    public void applyBlade(PoseStack pose, WavefrontObject model, String target) {
        blades.computeIfAbsent(model, value -> new Variants(fit(value, false, "item_blade"),
                fit(value, false, "item_bladens"), fit(value, false, "item_damaged"))).get(target).apply(pose);
    }

    public void applyGauge(PoseStack pose, WavefrontObject model) {
        gauges.computeIfAbsent(model, value -> fit(value, true, "")).apply(pose);
    }

    public void getExtents(Consumer<Vector3fc> output) {
        // Supply the fitted screen bounds in model coordinates. An axis-aligned
        // OBJ cube becomes much wider after the GUI rotation and misstates the icon size.
        Matrix4f inverseDisplay = new Matrix4f(display).invert();
        float half = BLADE_SPAN * .5F;
        for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) for (int z : new int[]{-1, 1})
            output.accept(inverseDisplay.transformPosition(new Vector3f(x * half, y * half, z * .5F)));
    }

    private Fit fit(WavefrontObject model, boolean gauge, String target) {
        if (!Float.isFinite(inverseProjection.m00())) return Fit.IDENTITY;
        float minX = Float.POSITIVE_INFINITY, minY = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX;
        Vector3f point = new Vector3f();
        for (var group : model.groupObjects) {
            if (group == null) continue;
            String name = group.name;
            boolean include = gauge ? name.equals("base") || name.equals("color") || name.equals("color_r")
                    : name.equals(target) || name.equals(target + "_luminous");
            if (!include) continue;
            // Include both depth endpoints so damage changes never resize or recenter the gauge.
            for (var face : group.faces) for (var vertex : face.vertices) for (int endpoint = 0; endpoint < (gauge ? 2 : 1); endpoint++) {
                float depth = gauge ? .1F - (name.equals("base") ? 0 : 2 * endpoint) : 0;
                projection.transformPosition(point.set(vertex.x, vertex.y, vertex.z + depth));
                minX = Math.min(minX, point.x); minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y);
            }
        }
        float span = Math.max(maxX - minX, maxY - minY);
        if (!(span > 0) || !Float.isFinite(span)) return Fit.IDENTITY;
        float scale = (gauge ? GAUGE_SPAN : BLADE_SPAN) / span;
        float cx = (maxX + minX) * .5F, cy = (maxY + minY) * .5F;
        Vector3f offset = inverseProjection.transformDirection(new Vector3f(
                -scale * (cx - projection.m30()) - projection.m30(),
                -scale * (cy - projection.m31()) - projection.m31(), 0));
        return new Fit(scale, offset.x, offset.y, offset.z);
    }

    private record Fit(float scale, float x, float y, float z) {
        private static final Fit IDENTITY = new Fit(1, 0, 0, 0);
        void apply(PoseStack pose) { pose.translate(x, y, z); pose.scale(scale, scale, scale); }
    }
    private record Variants(Fit withSheath, Fit noSheath, Fit broken) {
        Fit get(String target) {
            return switch (target) { case "item_bladens" -> noSheath; case "item_damaged" -> broken; default -> withSheath; };
        }
    }
}
