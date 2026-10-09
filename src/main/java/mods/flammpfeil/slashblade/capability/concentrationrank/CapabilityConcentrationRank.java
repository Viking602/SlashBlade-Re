package mods.flammpfeil.slashblade.capability.concentrationrank;

import mods.flammpfeil.slashblade.compat.StateKey;
import mods.flammpfeil.slashblade.util.NBTHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class CapabilityConcentrationRank {

    public static final StateKey<IConcentrationRank> RANK_POINT = StateKey.of(IConcentrationRank.class);
}
