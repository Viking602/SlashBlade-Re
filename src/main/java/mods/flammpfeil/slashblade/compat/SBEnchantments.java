package mods.flammpfeil.slashblade.compat;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;

/** Bridges gameplay's enchantment requirements to registry holders and components. */
public final class SBEnchantments {
    public static Map<Holder<Enchantment>, Integer> map(ItemStack stack) {
        var result = new HashMap<Holder<Enchantment>, Integer>();
        stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()
            .forEach(entry -> result.put(entry.getKey(), entry.getIntValue()));
        return result;
    }
    public static void set(Map<Holder<Enchantment>, Integer> enchantments, ItemStack stack) {
        var mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.forEach(mutable::set);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }
    public static int level(Holder<Enchantment> enchantment, ItemStack stack) { return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment); }
    public static int level(ResourceKey<Enchantment> enchantment, ItemStack stack) {
        return map(stack).entrySet().stream().filter(entry -> entry.getKey().is(enchantment)).mapToInt(Map.Entry::getValue).findFirst().orElse(0);
    }
    private SBEnchantments() {}
    public static int livingLevel(net.minecraft.resources.ResourceKey<Enchantment> key, net.minecraft.world.entity.LivingEntity entity) {
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentLevel(entity.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(key), entity);
    }
}
