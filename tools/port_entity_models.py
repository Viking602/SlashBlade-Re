from pathlib import Path
root=Path(__file__).resolve().parents[1]
j=root/'src/main/java/mods/flammpfeil/slashblade'
p=j/'event/client/UserPoseOverrider.java'
s=p.read_text(encoding='utf-8')
a=s.index('    @SubscribeEvent\n    public void onRenderPlayerEventPre'); b=s.index('    static public void anotherPoseRotP',a)
s=s[:a]+'''    @SubscribeEvent
    public void onRenderPlayerEventPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        org.joml.Matrix4f rotation = event.getRenderState().getRenderData(mods.flammpfeil.slashblade.client.renderer.layers.LivingBladeLayer.ROTATION);
        if (rotation != null) event.getPoseStack().mulPose(rotation);
    }

    public static org.joml.Matrix4f extractRotation(LivingEntity entity, float partialTicks) {
        PoseStack pose = new PoseStack();
        float rot = entity.getPersistentData().getFloatOr(TAG_ROT, 0.0F);
        float prev = entity.getPersistentData().getFloatOr(TAG_ROT_PREV, 0.0F);
        float body = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - body));
        anotherPoseRotP(pose, entity, partialTicks);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTicks, prev, rot)));
        anotherPoseRotN(pose, entity, partialTicks);
        pose.mulPose(Axis.YN.rotationDegrees(180.0F - body));
        return new org.joml.Matrix4f(pose.last().pose());
    }

'''+s[b:]
s=s.replace('Mth.rotLerp(partialTicks,rot,rotPrev)', 'Mth.rotLerp(partialTicks,rotPrev,rot)')
p.write_text(s,encoding='utf-8')
p=j/'event/client/SneakingMotionCanceller.java'
p.write_text('''package mods.flammpfeil.slashblade.event.client;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/** Applied during extraction so model pose and render offset use the same crouching state. */
public final class SneakingMotionCanceller {
    public static void apply(AvatarRenderState state) { state.isCrouching = false; }
    private SneakingMotionCanceller() {}
}
''',encoding='utf-8')
p=j/'client/SlashBladeClient.java'
s=p.read_text(encoding='utf-8')
s=s.replace('        bus.addListener(SlashBladeClient::setup);', '''        bus.addListener(SlashBladeClient::setup);
        bus.addListener(mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState::registerPipelines);
        bus.addListener(mods.flammpfeil.slashblade.client.renderer.layers.LivingBladeLayer::register);
        bus.addListener(mods.flammpfeil.slashblade.client.renderer.layers.LivingBladeLayer::addLayers);
        bus.addListener((net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent event) -> event.register(SlashBlade.id("blade"), BladeSpecialRenderer.Unbaked.CODEC));
        bus.addListener((net.neoforged.neoforge.client.event.AddClientReloadListenersEvent event) -> {
            event.addListener(SlashBlade.id("models"), (net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources -> BladeModelManager.getInstance().reload(resources));
            event.addListener(SlashBlade.id("motions"), (net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources -> BladeMotionManager.getInstance().reload(resources));
        });''')
s=s.replace('        NeoForge.EVENT_BUS.register(BladeModelManager.getInstance());\n','').replace('        NeoForge.EVENT_BUS.register(BladeMotionManager.getInstance());\n','').replace('        SneakingMotionCanceller.getInstance().register();\n','')
p.write_text(s,encoding='utf-8')
print('Registered deferred blade layers and resource reload listeners')
