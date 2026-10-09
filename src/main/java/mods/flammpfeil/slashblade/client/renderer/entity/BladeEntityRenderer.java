package mods.flammpfeil.slashblade.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.util.GeometryBuffer;
import mods.flammpfeil.slashblade.verification.CombatClientProbe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;

/** Retains the original animation math while extracting a geometry snapshot for each frame. */
public abstract class BladeEntityRenderer<T extends Entity> extends EntityRenderer<T, BladeEntityRenderer.State> {
    public static class State extends EntityRenderState { GeometryBuffer geometry; int verificationEntityId = -1; }
    protected BladeEntityRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.geometry = new GeometryBuffer();
        render(entity, entity.getYRot(), partialTicks, new PoseStack(), state.geometry, state.lightCoords);
        if (CombatClientProbe.ENABLED) state.verificationEntityId = CombatClientProbe.extracted(entity);
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
        state.geometry.submit(pose, collector);
        if (CombatClientProbe.ENABLED) CombatClientProbe.submitted(state.verificationEntityId);
    }
    public abstract void render(T entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light);
}
