package mods.flammpfeil.slashblade.client.renderer.layers;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.client.renderer.util.GeometryBuffer;
import mods.flammpfeil.slashblade.event.client.SneakingMotionCanceller;
import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.joml.Matrix4f;

public final class LivingBladeLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
    public static final ContextKey<GeometryBuffer> GEOMETRY = new ContextKey<>(SlashBlade.id("carried_blade"));
    public static final ContextKey<Matrix4f> ROTATION = new ContextKey<>(SlashBlade.id("user_rotation"));
    public static final ContextKey<mods.flammpfeil.slashblade.client.animation.BladeRig.Meshes> RIG = new ContextKey<>(SlashBlade.id("bound_blade"));
    private static final LayerMainBlade animation = new LayerMainBlade();
    public LivingBladeLayer(RenderLayerParent<S, M> renderer) { super(renderer); }
    public static void register(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {}, LivingBladeLayer::extract);
    }
    private static void extract(LivingEntity entity, LivingEntityRenderState state) {
        mods.flammpfeil.slashblade.client.renderer.LockonCircleRender.extract(entity, state);
        if (!(entity.getMainHandItem().getItem() instanceof ItemSlashBlade)) return;
        float partial = state.partialTick;
        if (state instanceof AvatarRenderState avatar) {
            mods.flammpfeil.slashblade.compat.SBData.get(entity.getMainHandItem(), ItemSlashBlade.BLADESTATE).ifPresent(blade -> {
                var sample = mods.flammpfeil.slashblade.client.animation.BladeMotionState.sample(entity, blade, partial);
                mods.flammpfeil.slashblade.client.animation.PlayerBladeAnimation.extract(entity, avatar, sample);
                if (mods.flammpfeil.slashblade.client.animation.PlayerBladeAnimation.available()
                        && (mods.flammpfeil.slashblade.init.DefaultResources.ExMotionLocation.equals(sample.current().combo().getMotionLoc())
                        || mods.flammpfeil.slashblade.init.DefaultResources.testLocation.equals(sample.current().combo().getMotionLoc()))) {
                    var meshes = mods.flammpfeil.slashblade.client.animation.BladeRig.capture(entity.getMainHandItem(), blade, state.lightCoords);
                    state.setRenderData(RIG, meshes);
                    state.setRenderData(GEOMETRY, meshes.blade());
                }
            });
        }
        state.setRenderData(ROTATION, UserPoseOverrider.extractRotation(entity, partial));
        if (state.getRenderData(RIG) != null) return;
        GeometryBuffer geometry = new GeometryBuffer();
        animation.render(new PoseStack(), geometry, state.lightCoords, entity, 0, 0, partial, 0, 0, 0);
        state.setRenderData(GEOMETRY, geometry);
        state.setRenderData(ROTATION, UserPoseOverrider.extractRotation(entity, partial));
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            var renderer = event.getPlayerRenderer(skin);
            if (renderer != null) renderer.addLayer(new LivingBladeLayer(renderer));
        }
        for (var type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof LivingEntityRenderer renderer) renderer.addLayer(new LivingBladeLayer(renderer));
        }
    }
    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
        var rig = state.getRenderData(RIG);
        if (rig != null && state instanceof AvatarRenderState avatar && getParentModel() instanceof net.minecraft.client.model.HumanoidModel<?> humanoid) {
            if (!state.isInvisible) mods.flammpfeil.slashblade.client.animation.BladeRig.submit(rig, humanoid, avatar, pose, collector);
            return;
        }
        GeometryBuffer geometry = state.getRenderData(GEOMETRY);
        if (geometry != null && !state.isInvisible) geometry.submit(pose, collector);
    }
}
