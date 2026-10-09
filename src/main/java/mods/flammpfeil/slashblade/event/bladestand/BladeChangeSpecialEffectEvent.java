package mods.flammpfeil.slashblade.event.bladestand;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;

import javax.annotation.Nullable;

public class BladeChangeSpecialEffectEvent extends SlashBladeEvent implements ICancellableEvent {
    private Identifier SEKey;
    private int shrinkCount = 0;
    private final SlashBladeEvent.BladeStandAttackEvent originalEvent;

    public BladeChangeSpecialEffectEvent(ItemStack blade, ISlashBladeState state, Identifier SEKey,
                                         SlashBladeEvent.BladeStandAttackEvent originalEvent) {
        super(blade, state);
        this.SEKey = SEKey;
        this.originalEvent = originalEvent;
    }

    public Identifier getSEKey() {
        return SEKey;
    }

    public Identifier setSEKey(Identifier SEKey) {
        this.SEKey = SEKey;
        return SEKey;
    }

    public int getShrinkCount() {
        return shrinkCount;
    }

    public int setShrinkCount(int shrinkCount) {
        this.shrinkCount = shrinkCount;
        return this.shrinkCount;
    }

    public @Nullable SlashBladeEvent.BladeStandAttackEvent getOriginalEvent() {
        return originalEvent;
    }
}
