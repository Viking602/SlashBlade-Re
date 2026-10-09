package mods.flammpfeil.slashblade.capability.mobeffect;

import mods.flammpfeil.slashblade.compat.StateKey;
import mods.flammpfeil.slashblade.util.NBTHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class CapabilityMobEffect {

    public static final StateKey<IMobEffectState> MOB_EFFECT = StateKey.of(IMobEffectState.class);
}
