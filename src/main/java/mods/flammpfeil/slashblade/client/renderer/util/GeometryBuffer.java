package mods.flammpfeil.slashblade.client.renderer.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import java.util.*;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.VertexFormat;

/** Captures immutable OBJ geometry during extraction, for the deferred 26.1 renderer. */
public final class GeometryBuffer implements MultiBufferSource {
    private final Map<RenderType, Vertices> batches = new LinkedHashMap<>();
    @Override public VertexConsumer getBuffer(RenderType type) {
        return batches.computeIfAbsent(type, ignored -> new Vertices());
    }
    /** Object-space triangles, also used by the opt-in geometry regression probe. */
    public List<Vector3f[]> triangles() {
        List<Vector3f[]> result=new ArrayList<>();
        batches.forEach((type, data) -> {
            var v=data.snapshot(); int stride=type.mode()==VertexFormat.Mode.QUADS ? 4 : 3;
            for(int i=0;i+stride<=v.size();i+=stride) {
                result.add(new Vector3f[]{v.get(i).position(),v.get(i+1).position(),v.get(i+2).position()});
                if(stride==4) result.add(new Vector3f[]{v.get(i).position(),v.get(i+2).position(),v.get(i+3).position()});
            }
        });
        return result;
    }
    public float maxX() {
        float result=Float.NEGATIVE_INFINITY;
        for(var batch:batches.values()) for(var v:batch.snapshot()) result=Math.max(result,v.x);
        return result;
    }
    /** Clip only the part already inside the saya. Both opaque and luminous surfaces use
     * the same mouth plane; interpolated UVs avoid a moving texture seam at the opening. */
    public GeometryBuffer outsideMouth(float x) {
        var result=new GeometryBuffer();
        batches.forEach((type,data) -> {
            var vertices=data.snapshot(); int stride=type.mode()==VertexFormat.Mode.QUADS ? 4 : 3;
            var out=result.batches.computeIfAbsent(type,ignored -> new Vertices());
            for(int start=0;start+stride<=vertices.size();start+=stride) {
                var polygon=new ArrayList<Vertex>();
                Vertex prior=vertices.get(start+stride-1);
                for(int j=0;j<stride;j++) {
                    Vertex v=vertices.get(start+j);
                    if((prior.x>=x)!=(v.x>=x)) polygon.add(prior.mix(v,(x-prior.x)/(v.x-prior.x)));
                    if(v.x>=x) polygon.add(v);
                    prior=v;
                }
                for(int j=1;j+1<polygon.size();j++) {
                    out.data.add(polygon.get(0)); out.data.add(polygon.get(j)); out.data.add(polygon.get(j+1));
                    if(stride==4) out.data.add(polygon.get(j+1));
                }
            }
        });
        return result;
    }
    public void submit(PoseStack pose, SubmitNodeCollector collector) {
        batches.forEach((type, vertices) -> {
            List<Vertex> snapshot = vertices.snapshot();
            if (!snapshot.isEmpty()) collector.submitCustomGeometry(pose, type, (transform, output) -> {
                for (Vertex v : snapshot) {
                    output.addVertex(transform, v.x, v.y, v.z).setColor(v.color).setUv(v.u, v.v)
                        .setUv1(v.overlayU, v.overlayV).setUv2(v.lightU, v.lightV)
                        .setNormal(transform, v.nx, v.ny, v.nz);
                }
            });
        });
    }
    private record Vertex(float x, float y, float z, int color, float u, float v,
                          int overlayU, int overlayV, int lightU, int lightV, float nx, float ny, float nz) {
        Vector3f position() { return new Vector3f(x,y,z); }
        Vertex mix(Vertex b,float t) {
            Vector3f n=new Vector3f(nx,ny,nz).lerp(new Vector3f(b.nx,b.ny,b.nz),t).normalize();
            int rgba=0;
            for(int shift=0;shift<32;shift+=8) rgba|=Math.round(((color>>>shift)&255)*(1-t)+((b.color>>>shift)&255)*t)<<shift;
            return new Vertex(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t,rgba,u+(b.u-u)*t,v+(b.v-v)*t,
                    Math.round(overlayU+(b.overlayU-overlayU)*t),Math.round(overlayV+(b.overlayV-overlayV)*t),
                    Math.round(lightU+(b.lightU-lightU)*t),Math.round(lightV+(b.lightV-lightV)*t),n.x,n.y,n.z);
        }
    }
    private static final class Vertices implements VertexConsumer {
        private final List<Vertex> data = new ArrayList<>();
        private boolean pending;
        private float x,y,z,u,v,nx,ny,nz;
        private int color=-1,ou,ov,lu,lv;
        private void commit() {
            if (pending) data.add(new Vertex(x,y,z,color,u,v,ou,ov,lu,lv,nx,ny,nz));
            pending=false;
        }
        List<Vertex> snapshot() { commit(); return List.copyOf(data); }
        @Override public VertexConsumer addVertex(float x,float y,float z) {
            commit(); this.x=x; this.y=y; this.z=z; pending=true; return this;
        }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { return setColor(a<<24|r<<16|g<<8|b); }
        @Override public VertexConsumer setColor(int argb) { color=argb; return this; }
        @Override public VertexConsumer setUv(float u,float v) { this.u=u; this.v=v; return this; }
        @Override public VertexConsumer setUv1(int u,int v) { ou=u; ov=v; return this; }
        @Override public VertexConsumer setUv2(int u,int v) { lu=u; lv=v; return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { nx=x; ny=y; nz=z; return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
