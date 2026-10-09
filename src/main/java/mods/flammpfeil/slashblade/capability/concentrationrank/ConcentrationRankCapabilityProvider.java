package mods.flammpfeil.slashblade.capability.concentrationrank;

import mods.flammpfeil.slashblade.compat.StateKey;
import mods.flammpfeil.slashblade.util.NBTHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;

import mods.flammpfeil.slashblade.compat.LazyOptional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConcentrationRankCapabilityProvider {

    public static final StateKey<IConcentrationRank> RANK_POINT = StateKey.of(IConcentrationRank.class);

    public ConcentrationRankCapabilityProvider() {}

    public ConcentrationRankCapabilityProvider(IConcentrationRank instance) { this.state = LazyOptional.of(() -> instance); }
    public IConcentrationRank getState() { return state.orElseThrow(() -> new IllegalStateException("Missing state")); }

    protected LazyOptional<IConcentrationRank> state = LazyOptional.of(()->new ConcentrationRank());

    public CompoundTag serializeNBT() {
        CompoundTag baseTag = new CompoundTag();

        state.ifPresent(instance -> {
            NBTHelper.getNBTCoupler(baseTag)
                    .put("rawPoint", instance.getRawRankPoint())
                    .put("lastupdate", instance.getLastUpdate());
        });

        return baseTag;
    }

    public void deserializeNBT(CompoundTag baseTag) {
        state.ifPresent(instance -> NBTHelper.getNBTCoupler((CompoundTag) baseTag)
                .get("rawPoint", instance::setRawRankPoint)
                .get("lastupdate", instance::setLastUpdte));
        ;
    }
}
