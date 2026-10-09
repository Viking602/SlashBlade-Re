package mods.flammpfeil.slashblade.client.renderer.layers;

import mods.flammpfeil.slashblade.compat.SBData;
import com.mojang.blaze3d.vertex.PoseStack;
import jp.nyatla.nymmd.*;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.BladeMotionManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.client.renderer.util.MSAutoCloser;
import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
import mods.flammpfeil.slashblade.util.TimeValueHelper;
import mods.flammpfeil.slashblade.util.VectorHelper;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import com.mojang.math.Axis;
import mods.flammpfeil.slashblade.compat.LazyOptional;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import mods.flammpfeil.slashblade.client.animation.BladeMotionState;
import mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline;

import java.io.FileNotFoundException;
import java.io.IOException;

public class LayerMainBlade {

    public LayerMainBlade() {}

    final LazyOptional<MmdPmdModelMc> bladeholder =
            LazyOptional.of(() -> {
                try {
                    return new MmdPmdModelMc(Identifier.fromNamespaceAndPath(SlashBlade.modid, "model/bladeholder.pmd"));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (MmdException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            });

    final LazyOptional<MmdMotionPlayerGL2> motionPlayer =
            LazyOptional.of(() -> {
                MmdMotionPlayerGL2 mmp = new MmdMotionPlayerGL2();;

                bladeholder.ifPresent(pmd -> {
                    try {
                        mmp.setPmd(pmd);
                    } catch (MmdException e) {
                        e.printStackTrace();
                    }
                });

                return mmp;
            });


    private float modifiedSpeed(float baseSpeed, LivingEntity entity) {
        float modif = 6.0f;
        if (MobEffectUtil.hasDigSpeed(entity)) {
            modif = 6 - (1 + MobEffectUtil.getDigSpeedAmplification(entity));
        } else if(entity.hasEffect(MobEffects.MINING_FATIGUE)) {
            modif = 6 + (1 + entity.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) * 2;
        }

        modif /= 6.0f;

        return baseSpeed / modif;
    }

    /** Immutable snapshots of the authored blade and sheath bones, shared with regression probes. */
    public Matrix4f[] sampleHardpoints(BladeMotionState.Sample sample) {
        return sampleHardpoints(motionPlayer.orElseThrow(() -> new IllegalStateException("Missing blade animation skeleton")), sample);
    }

    private Matrix4f[] sampleHardpoints(MmdMotionPlayerGL2 player, BladeMotionState.Sample sample) {
        if (sample.blending() && sample.alpha() == 0) return sampleHardpoints(player, sample.previous());
        Matrix4f[] target = sampleHardpoints(player, sample.current());
        if (!sample.blending()) return target;
        Matrix4f[] source = sampleHardpoints(player, sample.previous());
        for (int i = 0; i < target.length; i++) {
            Vector3f position = source[i].getTranslation(new Vector3f()).lerp(target[i].getTranslation(new Vector3f()), sample.alpha());
            Quaternionf rotation = source[i].getUnnormalizedRotation(new Quaternionf()).normalize()
                    .slerp(target[i].getUnnormalizedRotation(new Quaternionf()).normalize(), sample.alpha());
            // Matrix element interpolation can collapse a rotating blade and shear
            // its normals. Interpolate rigid translation and rotation instead.
            target[i] = new Matrix4f().translationRotate(position, rotation);
        }
        return target;
    }

    private Matrix4f[] sampleHardpoints(MmdMotionPlayerGL2 player, BladeAnimationTimeline timeline) {
        var motion = BladeMotionManager.getInstance().getMotion(timeline.combo().getMotionLoc());
        try {
            player.setVmd(motion);
            player.updateMotion((float)TimeValueHelper.getMSecFromFrames(Math.min(timeline.frame(), motion.getMaxFrame())));
        } catch (MmdException error) { throw new IllegalStateException("Could not sample blade animation", error); }
        Matrix4f[] result = new Matrix4f[2];
        float[] values = new float[16];
        for (int i = 0; i < result.length; i++) {
            int index = player.getBoneIndexByName(i == 0 ? "hardpointA" : "hardpointB");
            if (index < 0) throw new IllegalStateException("Missing blade animation hardpoint " + i);
            player._skinning_mat[index].getValue(values);
            result[i] = VectorHelper.matrix4fFromArray(values);
        }
        return result;
    }

    public void render(PoseStack matrixStack, MultiBufferSource bufferIn, int lightIn, LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {

        float motionYOffset = 1.5f;
        double motionScale = 1.5 / 12.0;
        double modelScaleBase = 0.0078125F; //0.5^7

        ItemStack stack = entity.getItemInHand(InteractionHand.MAIN_HAND);

        if(stack.isEmpty()) return;

        LazyOptional<ISlashBladeState> state = SBData.get(stack, CapabilitySlashBlade.BLADESTATE);
        state.ifPresent(s -> {

            motionPlayer.ifPresent(mmp ->
            {
                var sample = BladeMotionState.sample(entity, s, partialTicks);
                Matrix4f[] hardpoints = sampleHardpoints(mmp, sample);


                try(MSAutoCloser msacA = MSAutoCloser.pushMatrix(matrixStack)){

                    UserPoseOverrider.invertRot(matrixStack,entity,partialTicks);

                    //minecraft model neckPoint height = 1.5f
                    //mmd model neckPoint height = 12.0f
                    matrixStack.translate(0, motionYOffset, 0);

                    matrixStack.scale((float)motionScale, (float)motionScale, (float)motionScale);


                    //transpoze mmd to mc
                    matrixStack.mulPose(Axis.ZP.rotationDegrees(180));


                    Identifier textureLocation = s.getTexture().orElseGet(() -> BladeModelManager.resourceDefaultTexture);
                    //bindTexture(textureLocation);

                    WavefrontObject obj = BladeModelManager.getInstance().getModel(s.getModel().orElse(null));

                    try(MSAutoCloser msac = MSAutoCloser.pushMatrix(matrixStack)){
                        matrixStack.scale(-1, 1, 1);
                        matrixStack.mulPose(hardpoints[0]);
                        matrixStack.scale(-1, 1, 1);

                        float modelScale = (float)(modelScaleBase * (1.0f / motionScale));
                        matrixStack.scale(modelScale, modelScale, modelScale);

                        //matrixStack.rotate(Axis.YP.rotationDegrees(180));


                        String part;
                        if(s.isBroken()){
                            part = "blade_damaged";
                        }else{
                            part = "blade";
                        }

                        BladeRenderState.renderOverrided(stack, obj, part, textureLocation, matrixStack, bufferIn, lightIn);
                        BladeRenderState.renderOverridedLuminous(stack, obj, part + "_luminous", textureLocation, matrixStack, bufferIn, lightIn);
                    }
                    try(MSAutoCloser msac = MSAutoCloser.pushMatrix(matrixStack)){
                        matrixStack.scale(-1, 1, 1);
                        matrixStack.mulPose(hardpoints[1]);
                        matrixStack.scale(-1, 1, 1);


                        float modelScale = (float)(modelScaleBase * (1.0f / motionScale));
                        matrixStack.scale(modelScale, modelScale, modelScale);

                        //matrixStack.rotate(Axis.YP.rotationDegrees(180));

                        BladeRenderState.renderOverrided(stack, obj, "sheath", textureLocation, matrixStack, bufferIn, lightIn);
                        BladeRenderState.renderOverridedLuminous(stack, obj, "sheath_luminous", textureLocation, matrixStack, bufferIn, lightIn);

                        if(s.isCharged(entity)){
                            //todo : charge effect
                        }
                    }
                    /*
                    try(MSAutoCloser msac = MSAutoCloser.pushMatrix(matrixStack)){
                        matrixStack.scale(1,1,-1);
                        //mmp.render();
                    }
                    */

                }

            });

        });
    }
}
