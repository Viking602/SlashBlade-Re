package mods.flammpfeil.slashblade.client;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.client.renderer.*;
import mods.flammpfeil.slashblade.client.renderer.entity.*;
import mods.flammpfeil.slashblade.client.renderer.gui.RankRenderer;
import mods.flammpfeil.slashblade.client.renderer.model.*;
import mods.flammpfeil.slashblade.event.*;
import mods.flammpfeil.slashblade.event.client.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Physical client entry point, kept separate from dedicated-server registration. */
public final class SlashBladeClient {
    public static void register(IEventBus bus) {
        bus.addListener(ClientNetwork::register);
        bus.addListener(SlashBladeClient::renderers);
        bus.addListener(SlashBladeClient::setup);
        bus.addListener(mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState::registerPipelines);
        bus.addListener((net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent event) -> mods.flammpfeil.slashblade.client.renderer.layers.LivingBladeLayer.register(event));
        bus.addListener((EntityRenderersEvent.AddLayers event) -> mods.flammpfeil.slashblade.client.renderer.layers.LivingBladeLayer.addLayers(event));
        bus.addListener((net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent event) -> event.register(SlashBlade.id("blade"), BladeSpecialRenderer.Unbaked.CODEC));
        bus.addListener((net.neoforged.neoforge.client.event.AddClientReloadListenersEvent event) -> {
            event.addListener(SlashBlade.id("models"), (net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources -> BladeModelManager.getInstance().reload(resources));
            event.addListener(SlashBlade.id("motions"), (net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources -> {
                BladeMotionManager.getInstance().reload(resources);
                mods.flammpfeil.slashblade.client.animation.BladeMotionState.clear();
                mods.flammpfeil.slashblade.client.animation.PlayerBladeAnimation.reload(resources);
            });
        });
        NeoForge.EVENT_BUS.addListener(MoveInputHandler::onPlayerPostTick);
        NeoForge.EVENT_BUS.addListener(BladeFirstPersonRender.getInstance()::onRenderHand);
        mods.flammpfeil.slashblade.verification.CombatClientProbe.register();
        mods.flammpfeil.slashblade.verification.PlayerAnimationClientProbe.register();
        if (net.neoforged.fml.ModList.get().isLoaded("jei")) mods.flammpfeil.slashblade.verification.BladeRecipeClientProbe.register();
    }
    private static void setup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(LockOnClient::onEntityUpdate);
        BlockPickCanceller.getInstance().register();
        UserPoseOverrider.getInstance().register();
        LockonCircleRender.getInstance().register();
        BladeComponentTooltips.getInstance().register();
        BladeMaterialTooltips.getInstance().register();
        AdvancementsRecipeRenderer.getInstance().register();
        RankRenderer.getInstance().register();
    }
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SlashBlade.RegistryEvents.SummonedSword, SummonedSwordRenderer::new);
        event.registerEntityRenderer(SlashBlade.RegistryEvents.StormSwords, SummonedSwordRenderer::new);
        event.registerEntityRenderer(SlashBlade.RegistryEvents.SpiralSwords, SummonedSwordRenderer::new);
        event.registerEntityRenderer(SlashBlade.RegistryEvents.BlisteringSwords, SummonedSwordRenderer::new);
        event.registerEntityRenderer(SlashBlade.RegistryEvents.HeavyRainSwords, SummonedSwordRenderer::new);
        event.registerEntityRenderer(SlashBlade.RegistryEvents.JudgementCut, JudgementCutRenderer::new);
        event.registerEntityRenderer(SlashBlade.RegistryEvents.BladeItem, BladeItemEntityRenderer::new);
        event.registerEntityRenderer(SlashBlade.RegistryEvents.BladeStand, BladeStandEntityRenderer::new);
        event.registerEntityRenderer(SlashBlade.RegistryEvents.Drive, mods.flammpfeil.slashblade.client.renderer.entity.DriveRenderer::new);
        event.registerEntityRenderer(SlashBlade.RegistryEvents.SlashEffect, SlashEffectRenderer::new);
        event.registerEntityRenderer(SlashBlade.RegistryEvents.PlacePreview, PlacePreviewEntityRenderer::new);
    }
    private SlashBladeClient() {}
}
