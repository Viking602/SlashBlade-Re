package mods.flammpfeil.slashblade.capability.mobeffect;

import mods.flammpfeil.slashblade.compat.StateKey;
import mods.flammpfeil.slashblade.util.NBTHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;

import mods.flammpfeil.slashblade.compat.LazyOptional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MobEffectCapabilityProvider {

    public static final StateKey<IMobEffectState> MOB_EFFECT = StateKey.of(IMobEffectState.class);

    public MobEffectCapabilityProvider() {}

    public MobEffectCapabilityProvider(IMobEffectState instance) { this.state = LazyOptional.of(() -> instance); }
    public IMobEffectState getState() { return state.orElseThrow(() -> new IllegalStateException("Missing state")); }

    protected LazyOptional<IMobEffectState> state = LazyOptional.of(()->new MobEffectState());

    public CompoundTag serializeNBT() {
        CompoundTag baseTag = new CompoundTag();

        state.ifPresent(instance -> NBTHelper.getNBTCoupler(baseTag)
                .put("StunTimeout", instance.getStunTimeOut()));

        return baseTag;
    }

    public void deserializeNBT(CompoundTag nbt) {
        state.ifPresent(instance ->
                NBTHelper.getNBTCoupler(nbt)
                .get("StunTimeout", instance::setStunTimeOut));
    }
}
