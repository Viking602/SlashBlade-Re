package mods.flammpfeil.slashblade.client.renderer.util;

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
    private static final Color defaultColor = Color.white;
    private static Color col = defaultColor;
    public static void setCol(int rgba){
        setCol(rgba, true);
    }
    public static void setCol(int rgb, boolean hasAlpha){
        setCol(new Color(rgb, hasAlpha));
    }
    public static void setCol(Color value) {
        col = value;
    }

    public static final int MAX_LIGHT = 15728864;

    public static void resetCol() {
        col = defaultColor;
    }

    static public void renderOverrided(ItemStack stack, WavefrontObject model, String target, Identifier texture, PoseStack  matrixStackIn, MultiBufferSource bufferIn, int packedLightIn){

        Face.forceQuad = true;
        renderOverrided(stack, model, target, texture, matrixStackIn, bufferIn, packedLightIn, RenderTypes::entityCutout, true);
        Face.forceQuad = false;

        //renderOverrided(stack, model, target, texture, matrixStackIn, bufferIn, packedLightIn, Util.memoize(BladeRenderState::getSlashBladeBlend), true);
    }

    /** GUI icons retain the upstream entitySmoothCutout vertex lighting. */
    public static void renderOverridedIcon(ItemStack stack, WavefrontObject model, String target, Identifier texture,
                                         PoseStack pose, MultiBufferSource buffers, int light) {
        renderOverrided(stack, model, target, texture, pose, buffers, light, icon, true);
    }

    static public void renderOverridedColorWrite(ItemStack stack, WavefrontObject model, String target, Identifier texture, PoseStack  matrixStackIn, MultiBufferSource bufferIn, int packedLightIn){
        renderOverrided(stack, model, target, texture, matrixStackIn, bufferIn, packedLightIn, Util.memoize(BladeRenderState::getSlashBladeBlendColorWrite), true);
    }

    static public void renderOverridedLuminous(ItemStack stack, WavefrontObject model, String target, Identifier texture, PoseStack  matrixStackIn, MultiBufferSource bufferIn, int packedLightIn){
        renderOverrided(stack, model, target, texture, matrixStackIn, bufferIn, packedLightIn, Util.memoize(BladeRenderState::getSlashBladeBlendLuminous), false);
    }
    static public void renderOverridedLuminousDepthWrite(ItemStack stack, WavefrontObject model, String target, Identifier texture, PoseStack  matrixStackIn, MultiBufferSource bufferIn, int packedLightIn){
        renderOverrided(stack, model, target, texture, matrixStackIn, bufferIn, packedLightIn, Util.memoize(BladeRenderState::getSlashBladeBlendLuminousDepthWrite), false);
    }

    static public void renderOverridedReverseLuminous(ItemStack stack, WavefrontObject model, String target, Identifier texture, PoseStack  matrixStackIn, MultiBufferSource bufferIn, int packedLightIn){
        renderOverrided(stack, model, target, texture, matrixStackIn, bufferIn, packedLightIn, Util.memoize(BladeRenderState::getSlashBladeBlendReverseLuminous), false);
    }


    static public void renderOverrided(ItemStack stack, WavefrontObject model, String target, Identifier texture, PoseStack  matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, Function<Identifier,RenderType> getRenderType, boolean enableEffect){
        RenderOverrideEvent event
                = RenderOverrideEvent.onRenderOverride(stack, model, target, texture, matrixStackIn, bufferIn);

        if(event.isCanceled()) return;

        Identifier loc = event.getTexture();

        RenderType rt = getRenderType.apply(loc);//getSlashBladeBlendLuminous(event.getTexture());
        VertexConsumer vb;
        vb = bufferIn.getBuffer(rt);

        Face.forceQuad = rt.mode() == VertexFormat.Mode.QUADS;
        Face.setCol(col);
        Face.setLightMap(packedLightIn);
        Face.setMatrix(matrixStackIn);
        event.getModel().tessellateOnly(vb, event.getTarget());


        if(stack.hasFoil() && enableEffect){
            boolean forceQuad = Face.forceQuad;
            Face.forceQuad = true;
            vb = bufferIn.getBuffer(RenderTypes.entityGlint());
            event.getModel().tessellateOnly(vb, event.getTarget());
            Face.forceQuad = forceQuad;
        }

        Face.resetMatrix();
        Face.resetLightMap();
        Face.resetCol();

        Face.resetAlphaOverride();
        Face.resetUvOperator();

        resetCol();
    }

    public static VertexConsumer getBuffer(MultiBufferSource bufferIn, RenderType renderTypeIn, boolean glintIn) {
        return glintIn ? com.mojang.blaze3d.vertex.VertexMultiConsumer.create(bufferIn.getBuffer(RenderTypes.entityGlint()), bufferIn.getBuffer(renderTypeIn)) : bufferIn.getBuffer(renderTypeIn);
    }


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
    // entityCutout in 26.1 enables PER_FACE_LIGHTING and reverses back-face
    // normals. Upstream entitySmoothCutout lights the supplied OBJ normals
    // once per vertex, including two-sided icon and durability meshes.
    private static final RenderPipeline ICON = RenderPipeline.builder()
        .withLocation(SlashBlade.id("pipeline/blade_icon"))
        .withVertexShader("core/entity").withFragmentShader("core/entity")
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withUniform("Fog", UniformType.UNIFORM_BUFFER)
        .withUniform("Lighting", UniformType.UNIFORM_BUFFER)
        .withSampler("Sampler0").withSampler("Sampler1").withSampler("Sampler2")
        .withShaderDefine("ALPHA_CUTOUT", 0.1f)
        .withCull(false)
        .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
        .withDepthStencilState(DepthStencilState.DEFAULT)
        .build();
    private static final Function<Identifier, RenderType> icon = Util.memoize(texture ->
        RenderType.create(ICON.getLocation().toString(), RenderSetup.builder(ICON)
            .withTexture("Sampler0", texture).useLightmap().useOverlay().bufferSize(65536).createRenderSetup()));

    private static final RenderPipeline BLEND = pipeline("blade_blend", BlendFunction.TRANSLUCENT, true, false);
    private static final RenderPipeline COLOR = pipeline("blade_color", BlendFunction.TRANSLUCENT, false, false);
    private static final RenderPipeline GLOW = pipeline("blade_glow", BlendFunction.OVERLAY, false, true);
    private static final RenderPipeline GLOW_DEPTH = pipeline("blade_glow_depth", BlendFunction.OVERLAY, true, true);
    // The modern pipeline API provides blend factors rather than the old global GL blend equation.
    private static final RenderPipeline DARK = pipeline("blade_dark", new BlendFunction(SourceFactor.ZERO, DestFactor.ONE_MINUS_SRC_COLOR, SourceFactor.ZERO, DestFactor.ONE), false, true);
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        for (RenderPipeline pipeline : new RenderPipeline[]{ICON, BLEND, COLOR, GLOW, GLOW_DEPTH, DARK}) event.registerPipeline(pipeline);
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
