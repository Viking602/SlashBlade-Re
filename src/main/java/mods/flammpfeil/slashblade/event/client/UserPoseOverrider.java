package mods.flammpfeil.slashblade.event.client;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Axis;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

public class UserPoseOverrider {

    static public boolean UsePoseOverrider = false;

    private static final class SingletonHolder {
        private static final UserPoseOverrider instance = new UserPoseOverrider();
    }
    public static UserPoseOverrider getInstance() {
        return SingletonHolder.instance;
    }
    private UserPoseOverrider(){}
    public void register(){
        NeoForge.EVENT_BUS.register(this);
        UsePoseOverrider = true;
    }

    private static final String TAG_ROT = "sb_yrot";
    private static final String TAG_ROT_PREV = "sb_yrot_prev";

    @SubscribeEvent
    public void onRenderPlayerEventPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        org.joml.Matrix4f rotation = event.getRenderState().getRenderData(mods.flammpfeil.slashblade.client.renderer.layers.LivingBladeLayer.ROTATION);
        if (rotation != null) event.getPoseStack().mulPose(rotation);
        // Player VMD root motion belongs to the humanoid model's root. Applying
        // it here also transforms the already animated sword a second time.
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

    static public void anotherPoseRotP(PoseStack matrixStackIn, LivingEntity entityLiving, float partialTicks){
        final boolean isPositive = true;
        final float np = isPositive ? 1 : -1;

        float f = entityLiving.getSwimAmount(partialTicks);
        if (entityLiving.isFallFlying()) {
            float f1 = (float)entityLiving.getFallFlyingTicks() + partialTicks;
            float f2 = Mth.clamp(f1 * f1 / 100.0F, 0.0F, 1.0F);
            if (!entityLiving.isAutoSpinAttack()) {
                matrixStackIn.mulPose(Axis.XP.rotationDegrees(np * f2 * (-90.0F - entityLiving.getXRot())));
            }

            Vec3 vector3d = entityLiving.getViewVector(partialTicks);
            Vec3 vector3d1 = entityLiving.getDeltaMovement();
            double d0 = vector3d1.horizontalDistanceSqr();
            double d1 = vector3d.horizontalDistanceSqr();
            if (d0 > 0.0D && d1 > 0.0D) {
                double d2 = (vector3d1.x * vector3d.x + vector3d1.z * vector3d.z) / Math.sqrt(d0 * d1);
                double d3 = vector3d1.x * vector3d.z - vector3d1.z * vector3d.x;
                matrixStackIn.mulPose(Axis.YP.rotation((float)(np * Math.signum(d3) * Math.acos(d2))));
            }
        } else if (f > 0.0F) {
            float f3 = entityLiving.isInWater() ? -90.0F - entityLiving.getXRot() : -90.0F;
            float f4 = Mth.lerp(f, 0.0F, f3);
            matrixStackIn.mulPose(Axis.XP.rotationDegrees(np * f4));
            if (entityLiving.isVisuallySwimming()) {
                matrixStackIn.translate(0.0D, np * -1.0D, (double) np * 0.3F);
            }
        }
    }
    static public void anotherPoseRotN(PoseStack matrixStackIn, LivingEntity entityLiving, float partialTicks){
        final boolean isPositive = false;
        final float np = isPositive ? 1 : -1;

        float f = entityLiving.getSwimAmount(partialTicks);
        if (entityLiving.isFallFlying()) {
            Vec3 vector3d = entityLiving.getViewVector(partialTicks);
            Vec3 vector3d1 = entityLiving.getDeltaMovement();
            double d0 = vector3d1.horizontalDistanceSqr();
            double d1 = vector3d.horizontalDistanceSqr();
            if (d0 > 0.0D && d1 > 0.0D) {
                double d2 = (vector3d1.x * vector3d.x + vector3d1.z * vector3d.z) / Math.sqrt(d0 * d1);
                double d3 = vector3d1.x * vector3d.z - vector3d1.z * vector3d.x;
                matrixStackIn.mulPose(Axis.YP.rotation((float)(np * Math.signum(d3) * Math.acos(d2))));
            }

            float f1 = (float)entityLiving.getFallFlyingTicks() + partialTicks;
            float f2 = Mth.clamp(f1 * f1 / 100.0F, 0.0F, 1.0F);
            if (!entityLiving.isAutoSpinAttack()) {
                matrixStackIn.mulPose(Axis.XP.rotationDegrees(np * f2 * (-90.0F - entityLiving.getXRot())));
            }
        } else if (f > 0.0F) {
            if (entityLiving.isVisuallySwimming()) {
                matrixStackIn.translate(0.0D, np * -1.0D, (double) np * 0.3F);
            }

            float f3 = entityLiving.isInWater() ? -90.0F - entityLiving.getXRot() : -90.0F;
            float f4 = Mth.lerp(f, 0.0F, f3);
            matrixStackIn.mulPose(Axis.XP.rotationDegrees(np * f4));
        }
    }

    static public void setRot(Entity target, float rotYaw, boolean isOffset){
        CompoundTag tag = target.getPersistentData();

        float prevRot = tag.getFloatOr(TAG_ROT, 0.0F);
        tag.putFloat(TAG_ROT_PREV, prevRot);

        if(isOffset)
            rotYaw += prevRot;

        tag.putFloat(TAG_ROT, rotYaw);
    }

    static public void resetRot(Entity target){
        CompoundTag tag = target.getPersistentData();
        tag.putFloat(TAG_ROT_PREV, 0);
        tag.putFloat(TAG_ROT, 0);
    }

    static public void invertRot(PoseStack matrixStack, Entity entity, float partialTicks){
        float rot = entity.getPersistentData().getFloatOr(TAG_ROT, 0.0F);
        float rotPrev = entity.getPersistentData().getFloatOr(TAG_ROT_PREV, 0.0F);
        matrixStack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTicks,rotPrev,rot)));
    }
}
