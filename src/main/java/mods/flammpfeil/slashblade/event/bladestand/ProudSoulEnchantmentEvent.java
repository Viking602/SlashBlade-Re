package mods.flammpfeil.slashblade.event.bladestand;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.ICancellableEvent;

import javax.annotation.Nullable;

public class ProudSoulEnchantmentEvent extends SlashBladeEvent implements ICancellableEvent {
    private int totalShrinkCount;
    private float probability;
    private net.minecraft.core.Holder<Enchantment> enchantment;
    private int enchantLevel;
    private boolean tryNextEnchant;
    private final SlashBladeEvent.BladeStandAttackEvent originalEvent;

    public ProudSoulEnchantmentEvent(ItemStack blade, ISlashBladeState state,
                                     net.minecraft.core.Holder<Enchantment> enchantment, int enchantLevel, boolean tryNextEnchant, float probability,
                                     int totalShrinkCount, SlashBladeEvent.BladeStandAttackEvent originalEvent) {
        super(blade, state);
        this.enchantment = enchantment;
        this.enchantLevel = enchantLevel;
        this.tryNextEnchant = tryNextEnchant;
        this.probability = probability;
        this.totalShrinkCount = totalShrinkCount;
        this.originalEvent = originalEvent;
    }

    public net.minecraft.core.Holder<Enchantment> getEnchantment() {
        return enchantment;
    }

    public net.minecraft.core.Holder<Enchantment> setEnchantment(net.minecraft.core.Holder<Enchantment> enchantment) {
        this.enchantment = enchantment;
        return enchantment;
    }

    public int getEnchantLevel() {
        return enchantLevel;
    }

    public int setEnchantLevel(int enchantLevel) {
        this.enchantLevel = enchantLevel;
        return this.enchantLevel;
    }

    public boolean willTryNextEnchant() {
        return tryNextEnchant;
    }

    public boolean setWillTryNextEnchant(boolean tryNextEnchant) {
        this.tryNextEnchant = tryNextEnchant;
        return tryNextEnchant;
    }

    public int getTotalShrinkCount() {
        return totalShrinkCount;
    }

    public int setTotalShrinkCount(int totalShrinkCount) {
        this.totalShrinkCount = totalShrinkCount;
        return this.totalShrinkCount;
    }

    public float getProbability() {
        return probability;
    }

    public float setProbability(float probability) {
        this.probability = probability;
        return this.probability;
    }

    public @Nullable SlashBladeEvent.BladeStandAttackEvent getOriginalEvent() {
        return originalEvent;
    }
}
