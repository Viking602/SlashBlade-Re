package mods.flammpfeil.slashblade.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Mod-owned CPU skinning of the existing skin/armor UVs. The source cubes are never mutated. */
public final class LimbSkinning {
    public interface Children { Map<String, ModelPart> slashblade$children(); }
    private static final Map<ModelPart, Binding> bindings = new WeakHashMap<>();
    private static final Map<ModelPart.Cube, List<ModelPart.Vertex[]>> meshes = new WeakHashMap<>();

    public record Binding(float joint, float end, Matrix4f lower, Matrix4f palm, Matrix4f hand,
                          Matrix4f toLimb, Matrix4f fromLimb, SkinTransform lowSkin, SkinTransform palmSkin) {
        public Binding(float joint, float end, Matrix4f lower, Matrix4f palm, Matrix4f hand, Matrix4f toLimb, Matrix4f fromLimb) {
            this(joint, end, lower, palm, hand, toLimb, fromLimb,
                    SkinTransform.of(lower, new Quaternionf()), SkinTransform.of(palm, SkinTransform.of(lower, new Quaternionf()).q));
        }
        public Vector3f position(Vector3f vertex) {
            Vector3f p = toLimb.transformPosition(new Vector3f(vertex));
            float a = smooth((p.y * 16 - joint + 1) / 2);
            float b = end > joint ? smooth(p.y * 16 - end + 1.5F) : 0;
            // Dual-quaternion skinning retains the limb's volume under large rotations.
            // Linear position blending collapses the wrist/inside elbow into a point.
            float w0 = (1-a)*(1-b), w1 = a*(1-b);
            float rx=w1*lowSkin.q.x+b*palmSkin.q.x, ry=w1*lowSkin.q.y+b*palmSkin.q.y;
            float rz=w1*lowSkin.q.z+b*palmSkin.q.z, rw=w0+w1*lowSkin.q.w+b*palmSkin.q.w;
            float inv = 1F/(float)Math.sqrt(rx*rx+ry*ry+rz*rz+rw*rw);
            rx*=inv; ry*=inv; rz*=inv; rw*=inv;
            float dx=(w1*lowSkin.d.x+b*palmSkin.d.x)*inv, dy=(w1*lowSkin.d.y+b*palmSkin.d.y)*inv;
            float dz=(w1*lowSkin.d.z+b*palmSkin.d.z)*inv, dw=(w1*lowSkin.d.w+b*palmSkin.d.w)*inv;
            new Quaternionf(rx,ry,rz,rw).transform(p);
            p.add(2*(-dw*rx+dx*rw-dy*rz+dz*ry), 2*(-dw*ry+dx*rz+dy*rw-dz*rx), 2*(-dw*rz-dx*ry+dy*rx+dz*rw));
            return fromLimb.transformPosition(p);
        }
    }
    public record SkinTransform(Quaternionf q, Quaternionf d) {
        static SkinTransform of(Matrix4f transform, Quaternionf reference) {
            Quaternionf q = transform.getUnnormalizedRotation(new Quaternionf()).normalize();
            if (q.dot(reference) < 0) q.set(-q.x,-q.y,-q.z,-q.w);
            Vector3f t = transform.getTranslation(new Vector3f());
            return new SkinTransform(q, new Quaternionf((t.x*q.w+t.y*q.z-t.z*q.y)*.5F,
                    (-t.x*q.z+t.y*q.w+t.z*q.x)*.5F, (t.x*q.y-t.y*q.x+t.z*q.w)*.5F,
                    (-t.x*q.x-t.y*q.y-t.z*q.z)*.5F));
        }
    }
    public static void clear() { bindings.clear(); meshes.clear(); }
    public static void reset(ModelPart root) { for (var part : root.getAllParts()) bindings.remove(part); }
    public static Binding get(ModelPart part) { return bindings.get(part); }
    public static void bindTorso(ModelPart part, Quaternionf chestRotation) {
        // Keep the belt ring aligned with the pelvis while the chest twists/bends.
        // The two-pixel blend band replaces the rigid waist hinge with a continuous surface.
        Matrix4f waist = new Matrix4f().translation(0, .75F, 0).rotate(new Quaternionf(chestRotation).invert()).translate(0, -.75F, 0);
        Binding binding = new Binding(8, 0, waist, waist, new Matrix4f(), new Matrix4f(), new Matrix4f());
        bindings.put(part, binding); bindChildren(part, binding, new Matrix4f());
    }
    public static float smooth(float t) { t = Math.clamp(t, 0, 1); return t*t*t*(t*(t*6-15)+10); }
    public static void bind(ModelPart part, float joint, float end, Quaternionf lower, Quaternionf palm) {
        Matrix4f low = new Matrix4f().translation(0, joint / 16, 0).rotate(lower).translate(0, -joint / 16, 0);
        Matrix4f tip = new Matrix4f(low).translate(0, end / 16, 0).rotate(palm).translate(0, -end / 16, 0);
        Matrix4f hand = new Matrix4f(tip).translate(0, end / 16, 0);
        Binding binding = new Binding(joint, end, low, tip, hand, new Matrix4f(), new Matrix4f());
        bindings.put(part, binding);
        // Sleeves, trouser layers and custom child cubes share the same skinning space.
        bindChildren(part, binding, new Matrix4f());
    }
    private static void bindChildren(ModelPart parent, Binding binding, Matrix4f transform) {
        for (var child : ((Children)(Object)parent).slashblade$children().values()) {
            Matrix4f local = new Matrix4f(transform).mul(PlayerBladeAnimation.partMatrix(child));
            bindings.put(child, new Binding(binding.joint, binding.end, binding.lower, binding.palm,
                    binding.hand, local, new Matrix4f(local).invert()));
            bindChildren(child, binding, local);
        }
    }
    public static Vector3f deform(ModelPart part, Vector3f vertex) {
        var binding = bindings.get(part);
        return binding == null ? new Vector3f(vertex) : binding.position(vertex);
    }
    public static Matrix4f hand(ModelPart part, float center) {
        var binding = bindings.get(part);
        return binding == null ? new Matrix4f().translation(center / 16, 9F / 16, 0)
                : new Matrix4f(binding.hand).translate(center / 16, 0, 0);
    }
    /** Called inside ModelPart.compile, so vanilla visibility, tint, armor trims and glint survive. */
    public static boolean render(ModelPart part, List<ModelPart.Cube> cubes, PoseStack.Pose pose,
                                 VertexConsumer output, int light, int overlay, int color) {
        Binding binding = bindings.get(part);
        if (binding == null) return false;
        Vector3f[] points = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        for (var cube : cubes) for (var quad : meshes.computeIfAbsent(cube, LimbSkinning::subdivide)) {
            for (int i = 0; i < 4; i++) {
                var v = quad[i];
                points[i].set(binding.position(new Vector3f(v.worldX(), v.worldY(), v.worldZ())));
            }
            // Surface normals follow the bent surface (including the weight gradient).
            var normal = new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0]));
            if (normal.lengthSquared() < 1E-14F) continue;
            normal.normalize(); pose.transformNormal(normal, normal);
            for (int i = 0; i < 4; i++) {
                Vector3f p = pose.pose().transformPosition(points[i], new Vector3f());
                output.addVertex(p.x, p.y, p.z, color, quad[i].u(), quad[i].v(), overlay, light, normal.x, normal.y, normal.z);
            }
        }
        return true;
    }
    private static List<ModelPart.Vertex[]> subdivide(ModelPart.Cube cube) {
        List<ModelPart.Vertex[]> result = new ArrayList<>();
        for (var polygon : cube.polygons) {
            var vertices = polygon.vertices();
            float low = Float.MAX_VALUE, high = -Float.MAX_VALUE;
            for (var v : vertices) { low = Math.min(low, v.y()); high = Math.max(high, v.y()); }
            if (high - low < .0001F) { result.add(vertices); continue; }
            // Half-pixel rings weld across every face and preserve the original UV interpolation.
            float bottom = low;
            while (bottom < high - .0001F) {
                float top = Math.min(high, (float)(Math.floor(bottom * 2 + .0001) + 1) / 2);
                List<ModelPart.Vertex> clipped = clip(Arrays.asList(vertices), bottom, true);
                clipped = clip(clipped, top, false);
                if (clipped.size() == 4) result.add(clipped.toArray(ModelPart.Vertex[]::new));
                else for (int i = 1; i + 1 < clipped.size(); i++)
                    result.add(new ModelPart.Vertex[]{clipped.get(0), clipped.get(i), clipped.get(i+1), clipped.get(i+1)});
                bottom = top;
            }
        }
        return List.copyOf(result);
    }
    private static List<ModelPart.Vertex> clip(List<ModelPart.Vertex> input, float y, boolean above) {
        List<ModelPart.Vertex> output = new ArrayList<>();
        if (input.isEmpty()) return output;
        ModelPart.Vertex previous = input.getLast();
        boolean prior = above ? previous.y() >= y : previous.y() <= y;
        for (var v : input) {
            boolean inside = above ? v.y() >= y : v.y() <= y;
            if (inside != prior) {
                float t = (y - previous.y()) / (v.y() - previous.y());
                output.add(new ModelPart.Vertex(previous.x() + t*(v.x()-previous.x()), y,
                        previous.z() + t*(v.z()-previous.z()), previous.u() + t*(v.u()-previous.u()), previous.v() + t*(v.v()-previous.v())));
            }
            if (inside) output.add(v);
            previous = v; prior = inside;
        }
        return output;
    }
    private LimbSkinning() {}
}
