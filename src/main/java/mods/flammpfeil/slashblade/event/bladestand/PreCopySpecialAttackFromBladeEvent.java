package mods.flammpfeil.slashblade.event.bladestand;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;

import javax.annotation.Nullable;

public class PreCopySpecialAttackFromBladeEvent extends SlashBladeEvent implements ICancellableEvent {
    private Identifier SAKey;
    private int shrinkCount = 0;
    private final SlashBladeEvent.BladeStandAttackEvent originalEvent;

    public PreCopySpecialAttackFromBladeEvent(ItemStack blade, ISlashBladeState state, Identifier SAKey,
                                              SlashBladeEvent.BladeStandAttackEvent originalEvent) {
        super(blade, state);
        this.SAKey = SAKey;
        this.originalEvent = originalEvent;
    }

    public Identifier getSAKey() {
        return SAKey;
    }

    public Identifier setSAKey(Identifier SAKey) {
        this.SAKey = SAKey;
        return SAKey;
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
