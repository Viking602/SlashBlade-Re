from pathlib import Path
root = Path(__file__).resolve().parents[1]
j = root / 'src/main/java/mods/flammpfeil/slashblade'
p = j / 'client/renderer/util/BladeRenderState.java'
s = p.read_text(encoding='utf-8')
start = s.index('    private static final Color defaultColor')
end = s.index('    public static final VertexFormat POSITION_TEX')
body = s[start:end]
body = body[:body.index('    public BladeRenderState(')] + body[body.index('    static public void renderOverrided('):]
body = body.replace('Util.memoize(RenderType::entitySmoothCutout)', 'RenderTypes::entityCutout')
body = body.replace('RenderType.entityGlint()', 'RenderTypes.entityGlint()')
body = body.replace('        Face.setCol(col);', '        Face.forceQuad = rt.mode() == VertexFormat.Mode.QUADS;\n        Face.setCol(col);')
body = body.replace('        return null;', '        return glintIn ? com.mojang.blaze3d.vertex.VertexMultiConsumer.create(bufferIn.getBuffer(RenderTypes.entityGlint()), bufferIn.getBuffer(renderTypeIn)) : bufferIn.getBuffer(renderTypeIn);')
header = '''package mods.flammpfeil.slashblade.client.renderer.util;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.*;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.*;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.client.renderer.model.obj.*;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import java.awt.Color;
import java.util.function.Function;

public final class BladeRenderState {
'''
tail = '''
    private static RenderPipeline pipeline(String name, BlendFunction blend, boolean depthWrite, boolean emissive) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(SlashBlade.id("pipeline/" + name))
            .withVertexShader("core/entity").withFragmentShader("core/entity")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withUniform("Lighting", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0").withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withShaderDefine("ALPHA_CUTOUT", 0.001f)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.TRIANGLES)
            .withColorTargetState(new ColorTargetState(blend))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, depthWrite));
        if (emissive) builder.withShaderDefine("EMISSIVE");
        else builder.withSampler("Sampler2");
        return builder.build();
    }
    private static final RenderPipeline BLEND = pipeline("blade_blend", BlendFunction.TRANSLUCENT, true, false);
    private static final RenderPipeline COLOR = pipeline("blade_color", BlendFunction.TRANSLUCENT, false, false);
    private static final RenderPipeline GLOW = pipeline("blade_glow", BlendFunction.OVERLAY, false, true);
    private static final RenderPipeline GLOW_DEPTH = pipeline("blade_glow_depth", BlendFunction.OVERLAY, true, true);
    // The modern pipeline API provides blend factors rather than the old global GL blend equation.
    private static final RenderPipeline DARK = pipeline("blade_dark", new BlendFunction(SourceFactor.ZERO, DestFactor.ONE_MINUS_SRC_COLOR, SourceFactor.ZERO, DestFactor.ONE), false, true);
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        for (RenderPipeline pipeline : new RenderPipeline[]{BLEND, COLOR, GLOW, GLOW_DEPTH, DARK}) event.registerPipeline(pipeline);
    }
    private static RenderType type(RenderPipeline pipeline, Identifier texture) {
        return RenderType.create(pipeline.getLocation().toString(), RenderSetup.builder(pipeline)
            .withTexture("Sampler0", texture).useLightmap().bufferSize(65536).createRenderSetup());
    }
    private static final Function<Identifier, RenderType> blend = Util.memoize(t -> type(BLEND, t));
    private static final Function<Identifier, RenderType> color = Util.memoize(t -> type(COLOR, t));
    private static final Function<Identifier, RenderType> glow = Util.memoize(t -> type(GLOW, t));
    private static final Function<Identifier, RenderType> glowDepth = Util.memoize(t -> type(GLOW_DEPTH, t));
    private static final Function<Identifier, RenderType> dark = Util.memoize(t -> type(DARK, t));
    public static RenderType getSlashBladeBlend(Identifier texture) { return blend.apply(texture); }
    public static RenderType getSlashBladeBlendColorWrite(Identifier texture) { return color.apply(texture); }
    public static RenderType getSlashBladeBlendLuminous(Identifier texture) { return glow.apply(texture); }
    public static RenderType getSlashBladeBlendLuminousDepthWrite(Identifier texture) { return glowDepth.apply(texture); }
    public static RenderType getSlashBladeBlendReverseLuminous(Identifier texture) { return dark.apply(texture); }
}
'''
p.write_text(header + body + tail, encoding='utf-8')
p = j / 'client/renderer/model/obj/Face.java'
s = p.read_text(encoding='utf-8').replace('import net.minecraft.util.LazyLoadedValue;','')
s = s.replace('private static final LazyLoadedValue<Matrix4f> defaultTransform = new LazyLoadedValue(()->{Matrix4f m = new Matrix4f(); m.identity(); return m;});', 'private static final Matrix4f defaultTransform = new Matrix4f();')
s = s.replace('defaultTransform.get()', 'defaultTransform')
s = s.replace('if(forceQuad){', 'if(forceQuad && vertices.length == 3){')
s = s.replace('        for (int i = 0; i < vertices.length; ++i)\n        {\n            putVertex(wr,i,transform,textureOffset,averageU,averageV);\n        }', '''        for (int i = 0; i < vertices.length; ++i) {
            if (!forceQuad && i == 3) {
                putVertex(wr, 0, transform, textureOffset, averageU, averageV);
                putVertex(wr, 2, transform, textureOffset, averageU, averageV);
            }
            putVertex(wr, i, transform, textureOffset, averageU, averageV);
        }''')
for old,new in [('wr.vertex(', 'wr.addVertex('), ('wr.color(', 'wr.setColor('), ('wr.uv(', 'wr.setUv('), ('wr.overlayCoords(', 'wr.setOverlay('), ('wr.uv2(', 'wr.setLight('), ('wr.normal(', 'wr.setNormal(')]: s=s.replace(old,new)
s=s.replace('        wr.endVertex();','')
p.write_text(s,encoding='utf-8')
p=j/'client/renderer/model/obj/WavefrontObject.java'
s=p.read_text(encoding='utf-8')
line=next(x for x in s.splitlines() if 'static public VertexFormat POSITION_TEX_LMAP_COL_NORMAL' in x)
s=s.replace(line, '    public static final VertexFormat POSITION_TEX_LMAP_COL_NORMAL = DefaultVertexFormat.ENTITY;')
p.write_text(s,encoding='utf-8')
for p in j.rglob('*.java'):
    s=p.read_text(encoding='utf-8')
    s=s.replace('import com.mojang.blaze3d.platform.GlStateManager;\n','')
    s=s.replace('import net.minecraft.util.LazyLoadedValue;', 'import mods.flammpfeil.slashblade.compat.LazyValue;')
    s=s.replace('LazyLoadedValue<','LazyValue<').replace('new LazyLoadedValue(', 'new LazyValue(')
    if p.name=='RenderOverrideEvent.java':
        s=s.replace('import net.neoforged.bus.api.Cancelable;', 'import net.neoforged.bus.api.ICancellableEvent;')
        s=s.replace('@Cancelable\n','').replace('extends Event {', 'extends Event implements ICancellableEvent {')
    p.write_text(s,encoding='utf-8')
at=root/'src/main/resources/META-INF/accesstransformer.cfg'
s=at.read_text(encoding='utf-8')
s+='\npublic net.minecraft.client.renderer.rendertype.RenderType create(Ljava/lang/String;Lnet/minecraft/client/renderer/rendertype/RenderSetup;)Lnet/minecraft/client/renderer/rendertype/RenderType;\n'
at.write_text(s,encoding='utf-8')
print('Ported OBJ vertices and render pipelines')
