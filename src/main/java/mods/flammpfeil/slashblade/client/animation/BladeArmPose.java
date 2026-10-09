package mods.flammpfeil.slashblade.client.animation;

import net.minecraft.client.model.HumanoidModel;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/** Separate enum parameter holder: no registry or resource access during enum initialization. */
public final class BladeArmPose {
    public static final EnumProxy<HumanoidModel.ArmPose> BLADE = new EnumProxy<>(
            HumanoidModel.ArmPose.class, true, true,
            (IArmPoseTransformer) (model, state, arm) -> {});
    private BladeArmPose() {}
}
