package mods.flammpfeil.slashblade.capability.inputstate;

import mods.flammpfeil.slashblade.compat.StateKey;
import mods.flammpfeil.slashblade.capability.mobeffect.IMobEffectState;
import mods.flammpfeil.slashblade.util.EnumSetConverter;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class CapabilityInputState {

    public static final StateKey<IInputState> INPUT_STATE = StateKey.of(IInputState.class);
}
