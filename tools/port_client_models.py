from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
j=root/'src/main/java/mods/flammpfeil/slashblade'
for name in ('SummonedSwordRenderer','JudgementCutRenderer','SlashEffectRenderer'):
    p=j/f'client/renderer/entity/{name}.java'
    s=p.read_text(encoding='utf-8').replace('extends EntityRenderer<T>', 'extends BladeEntityRenderer<T>')
    s=s.replace('    @Override\n    public Identifier getTextureLocation', '    public Identifier getTextureLocation')
    p.write_text(s,encoding='utf-8')
p=j/'client/renderer/entity/BladeItemEntityRenderer.java'
s=p.read_text(encoding='utf-8').replace('import net.minecraft.client.renderer.entity.ItemEntityRenderer;','')
s=s.replace('extends ItemEntityRenderer', 'extends BladeEntityRenderer<ItemEntity>')
s=s.replace('    @Override\n    public boolean should', '    public boolean should')
s=s.replace('            super.render(itemIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);','')
p.write_text(s,encoding='utf-8')
p=j/'client/renderer/SlashBladeTEISR.java'
s=p.read_text(encoding='utf-8')
s=s.replace('import net.minecraft.client.renderer.block.model.ItemTransforms;','')
s=s.replace('public class SlashBladeTEISR extends BlockEntityWithoutLevelRenderer {', 'public class SlashBladeTEISR {')
a=s.index('    public SlashBladeTEISR('); b=s.index('    public void renderByItem',a)
s=s[:a]+'    public SlashBladeTEISR() {}\n\n'+s[b:]
a=s.index('        if(SBItemData.hasTag(itemStackIn)'); b=s.index('        renderBlade(itemStackIn',a)
s=s[:a]+s[b:]
a=s.index('                if (stack.isFramed()'); b=s.index('            }else{',a)
s=s[:a]+'''                matrixStack.mulPose(Axis.YP.rotationDegrees(180.0f));
                renderIcon(stack, matrixStack, bufferIn, combinedLightIn,0.0095f);
'''+s[b:]
s=s.replace('    private void renderModel(ItemStack stack, PoseStack matrixStack, MultiBufferSource bufferIn, int lightIn){', '    public void renderStand(ItemStack stack, PoseStack matrixStack, MultiBufferSource bufferIn, int lightIn, BladeStandEntity stand){')
s=s.replace('        if(stack.isFramed()){\n            if(stack.getFrame() instanceof BladeStandEntity){\n                BladeStandEntity stand = (BladeStandEntity) stack.getFrame();', '        {\n            if(stand != null){')
s=s.replace('            if(!types.contains(SwordType.NoScabbard)) {', '            if(BladeModel.user != null && !types.contains(SwordType.NoScabbard)) {')
s=s.replace('Mth.lerp(aCol.getRed(), bCol.getRed(),durability)', 'Mth.lerp(durability, aCol.getRed(), bCol.getRed())').replace('Mth.lerp(aCol.getGreen(), bCol.getGreen(),durability)', 'Mth.lerp(durability, aCol.getGreen(), bCol.getGreen())').replace('Mth.lerp(aCol.getBlue(), bCol.getBlue(),durability)', 'Mth.lerp(durability, aCol.getBlue(), bCol.getBlue())')
p.write_text(s,encoding='utf-8')
# The old BakedModel adapter is replaced by SpecialModelRenderer. The current holder remains for first-person math.
p=j/'client/renderer/model/BladeModel.java'
p.write_text('''package mods.flammpfeil.slashblade.client.renderer.model;
import net.minecraft.world.entity.LivingEntity;
public final class BladeModel { public static LivingEntity user; private BladeModel() {} }
''',encoding='utf-8')
p=j/'client/renderer/layers/LayerMainBlade.java'
s=p.read_text(encoding='utf-8')
s=re.sub(r'import dev\.kosmx[^;]+;\n','',s)
s=s.replace('public class LayerMainBlade<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {', 'public class LayerMainBlade {')
a=s.index('    public LayerMainBlade('); b=s.index('    final LazyOptional',a)
s=s[:a]+'    public LayerMainBlade() {}\n\n'+s[b:]
s=s.replace('    @Override\n    public void render', '    public void render').replace('int lightIn, T entity,', 'int lightIn, LivingEntity entity,')
s=s.replace('MobEffects.DIG_SLOWDOWN', 'MobEffects.MINING_FATIGUE')
a=s.index('                    if(!UserPoseOverrider.UsePoseOverrider'); b=s.index('                    //minecraft model neckPoint',a)
s=s[:a]+'                    UserPoseOverrider.invertRot(matrixStack,entity,partialTicks);\n\n'+s[b:]
# Avoid modulo by zero for a missing or empty motion.
s=s.replace('if (combo.getLoop()) {', 'if (combo.getLoop() && span > 0) {')
p.write_text(s,encoding='utf-8')
p=j/'client/renderer/model/BladeFirstPersonRender.java'
s=p.read_text(encoding='utf-8')
a=s.index('    private LayerMainBlade layer'); b=s.index('    private static final class SingletonHolder',a)
s=s[:a]+'    private final LayerMainBlade layer = new LayerMainBlade();\n    private BladeFirstPersonRender() {}\n'+s[b:]
s=s.replace('mc.gameMode.isAlwaysFlying()', 'mc.player.isSpectator()').replace('mc.getFrameTime()', 'mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)')
p.write_text(s,encoding='utf-8')
# Resource reload uses the new mod-bus reload listener hook, and models are first loaded when resources exist.
for name in ('BladeModelManager','BladeMotionManager'):
    p=j/f'client/renderer/model/{name}.java'
    s=p.read_text(encoding='utf-8').replace('import net.neoforged.neoforge.client.event.TextureStitchEvent;', 'import net.minecraft.server.packs.resources.ResourceManager;')
    s=s.replace('    @SubscribeEvent\n    public void reload(TextureStitchEvent.Post event){', '    public void reload(ResourceManager resources){')
    if name=='BladeModelManager':
        s=s.replace('        defaultModel = new WavefrontObject(resourceDefaultModel);\n\n        cache', '        cache')
        s=s.replace('        if(loc != null){', '        if(defaultModel == null) defaultModel = new WavefrontObject(resourceDefaultModel);\n        if(loc != null){')
    else:
        a=s.index('        try {\n            defaultMotion ='); b=s.index('        cache =',a)
        s=s[:a]+s[b:]
        s=s.replace('        if(loc != null){', '        if(defaultMotion == null) reload(net.minecraft.client.Minecraft.getInstance().getResourceManager());\n        if(loc != null){')
    p.write_text(s,encoding='utf-8')
# A complete PMD material draw path, replacing removed immediate GL state in the embedded NyMmd renderer.
p=root/'src/main/java/jp/nyatla/nymmd/MmdMotionPlayerGL2.java'
s=p.read_text(encoding='utf-8')
a=s.index('\tpublic void render()'); b=s.index('    private static FloatBuffer',a)
s=s[:a]+'''    public void render(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        int normalOffset = this._ref_pmd_model.getNumberOfVertex() * 3;
        for (Material material : this._materials) {
            Identifier texture = material.texture_id != null ? material.texture_id : mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager.resourceDefaultTexture;
            var output = buffers.getBuffer(mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState.getSlashBladeBlend(texture));
            for (short index : material.indices) {
                int vertex = Short.toUnsignedInt(index), pos = vertex * 3, normal = normalOffset + pos;
                output.addVertex(pose.last(), _fbuf[pos], _fbuf[pos+1], -_fbuf[pos+2])
                    .setColor(material.color[0], material.color[1], material.color[2], material.color[3])
                    .setUv(_tex_array[vertex].u, _tex_array[vertex].v).setLight(light)
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setNormal(pose.last(), _fbuf[normal], _fbuf[normal+1], -_fbuf[normal+2]);
            }
        }
    }

'''+s[b:]
p.write_text(s,encoding='utf-8')
# Optional 1.20 player-animation integration is archived; vanilla pose and the bundled VMD animator are active.
source=j/'optional/playerAnim'
dest=root/'src/legacy/java/mods/flammpfeil/slashblade/optional/playerAnim'
dest.mkdir(parents=True,exist_ok=True)
for p in source.glob('*.java'): p.rename(dest/p.name)
p=root/'build.gradle'
s=p.read_text(encoding='utf-8')
s=re.sub(r'    compileOnly[^\n]*player-animation[^\n]*\n','',s)
p.write_text(s,encoding='utf-8')
print('Ported client item and entity model entry points')
