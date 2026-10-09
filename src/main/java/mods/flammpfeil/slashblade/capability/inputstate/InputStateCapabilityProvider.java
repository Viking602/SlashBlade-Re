package mods.flammpfeil.slashblade.capability.inputstate;

import mods.flammpfeil.slashblade.compat.StateKey;
import mods.flammpfeil.slashblade.util.EnumSetConverter;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;

import mods.flammpfeil.slashblade.compat.LazyOptional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InputStateCapabilityProvider {

    public static final StateKey<IInputState> INPUT_STATE = StateKey.of(IInputState.class);

    public InputStateCapabilityProvider() {}

    public InputStateCapabilityProvider(IInputState instance) { this.state = LazyOptional.of(() -> instance); }
    public IInputState getState() { return state.orElseThrow(() -> new IllegalStateException("Missing state")); }

    protected LazyOptional<IInputState> state = LazyOptional.of(()->new InputState());

    static final String KEY = "Command";

    public CompoundTag serializeNBT() {
        CompoundTag baseTag = new CompoundTag();

        state.ifPresent(instance -> {
            baseTag.putInt(KEY, EnumSetConverter.convertToInt(instance.getCommands()));
        });

        return baseTag;
    }

    public void deserializeNBT(CompoundTag baseTag) {
        state.ifPresent(instance ->{
            instance.getCommands().addAll(
                    EnumSetConverter.convertToEnumSet(InputCommand.class, baseTag.getIntOr(KEY, 0)));
        });
    }
}
