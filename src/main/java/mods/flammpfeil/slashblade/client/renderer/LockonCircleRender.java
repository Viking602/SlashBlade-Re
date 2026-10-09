package mods.flammpfeil.slashblade.client.renderer;

import mods.flammpfeil.slashblade.compat.SBData;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.bus.api.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.CallbackI;

import java.awt.*;
import java.util.Optional;

public class LockonCircleRender {
    private static final class SingletonHolder {
        private static final LockonCircleRender instance = new LockonCircleRender();
    }

    public static LockonCircleRender getInstance() {
        return SingletonHolder.instance;
    }

    private LockonCircleRender() {
    }

    public void register() {
        NeoForge.EVENT_BUS.register(this);
    }


    static final Identifier modelLoc = Identifier.fromNamespaceAndPath("slashblade", "model/util/lockon.obj");
    static final Identifier textureLoc = Identifier.fromNamespaceAndPath("slashblade", "model/util/lockon.png");

    public static final net.minecraft.util.context.ContextKey<mods.flammpfeil.slashblade.client.renderer.util.GeometryBuffer> RING = new net.minecraft.util.context.ContextKey<>(mods.flammpfeil.slashblade.SlashBlade.id("lockon_ring"));
    @SubscribeEvent
    public void onRenderLiving(RenderLivingEvent.Pre<?, ?, ?> event) {
        var geometry = event.getRenderState().getRenderData(RING);
        if (geometry != null) geometry.submit(event.getPoseStack(), event.getSubmitNodeCollector());
    }
    public static void extract(LivingEntity livingEntity, net.minecraft.client.renderer.entity.state.LivingEntityRenderState renderState) {
        Player player = Minecraft.getInstance().player;
        if(player == null) return;
        if(!SBData.get(player, CapabilityInputState.INPUT_STATE).filter(input->input.getCommands().contains(InputCommand.SNEAK)).isPresent()) return;

        ItemStack stack = player.getMainHandItem();

        Optional<Color> effectColor = SBData.get(stack, ItemSlashBlade.BLADESTATE)
                .filter(s->livingEntity.equals(s.getTargetEntity(player.level())))
                .map(s->s.getEffectColor());

        if(effectColor.isEmpty()) return;





        if(!livingEntity.isAlive()) return;

        float health = livingEntity.getHealth() / livingEntity.getMaxHealth();

        Color col = new Color(effectColor.get().getRGB() & 0xFFFFFF | 0xAA000000, true);


        PoseStack poseStack = new PoseStack();

        float f = livingEntity.getBbHeight() * 0.5f;
        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);

        poseStack.pushPose();
        poseStack.translate(0.0D, (double)f, 0.0D);

        Vec3 offset = Minecraft.getInstance().gameRenderer.getMainCamera().position()
                .subtract(livingEntity.getPosition(partialTicks).add(0,f,0));
        offset = offset.scale(0.5f);
        poseStack.translate(offset.x(), offset.y(), offset.z());

        poseStack.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        //poseStack.scale(-0.025F, -0.025F, 0.025F);

        float scale = 0.0025f;
        poseStack.scale(scale, -scale, scale);

        WavefrontObject model = BladeModelManager.getInstance().getModel(modelLoc);
        Identifier resourceTexture = textureLoc;

        var buffer = new mods.flammpfeil.slashblade.client.renderer.util.GeometryBuffer();

        final String base = "lockonBase";
        final String mask = "lockonHealthMask";
        final String value = "lockonHealth";

        BladeRenderState.setCol(col);
        BladeRenderState.renderOverridedLuminous(ItemStack.EMPTY, model, base, resourceTexture, poseStack, buffer, BladeRenderState.MAX_LIGHT );
        {
            poseStack.pushPose();
            poseStack.translate(0,0, health * 10.0f);
            BladeRenderState.setCol(new Color(0x20000000, true));
            BladeRenderState.renderOverridedLuminousDepthWrite(ItemStack.EMPTY, model, mask, resourceTexture, poseStack, buffer, BladeRenderState.MAX_LIGHT );
            poseStack.popPose();
        }
        BladeRenderState.setCol(col);
        BladeRenderState.renderOverridedLuminousDepthWrite(ItemStack.EMPTY, model, value, resourceTexture, poseStack, buffer, BladeRenderState.MAX_LIGHT );

        poseStack.popPose();
        renderState.setRenderData(RING, buffer);
    }
}
