package mods.flammpfeil.slashblade.compat;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;

public record BladePredicate(CompoundTag expected) implements DataComponentPredicate {
    public static final Codec<BladePredicate> CODEC = CompoundTag.CODEC.xmap(BladePredicate::new, BladePredicate::expected);
    @Override public boolean matches(DataComponentGetter components) {
        return SBItemData.matches(expected, components.getOrDefault(SBData.BLADE_STATE.get(), CustomData.EMPTY).copyTag());
    }
}
