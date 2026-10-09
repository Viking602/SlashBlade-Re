package mods.flammpfeil.slashblade.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.stream.Stream;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.crafting.*;

/** Named blade identity plus minimum progress, independent of volatile animation components. */
public record BladeRequirementIngredient(String name, int kills, int refine, boolean broken,
                                          Map<Identifier,Integer> enchantments, ItemStackTemplate shown) implements ICustomIngredient {
    public static final MapCodec<BladeRequirementIngredient> CODEC=RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(BladeRequirementIngredient::name),
            Codec.intRange(0,Integer.MAX_VALUE).optionalFieldOf("kills",0).forGetter(BladeRequirementIngredient::kills),
            Codec.intRange(0,Integer.MAX_VALUE).optionalFieldOf("refine",0).forGetter(BladeRequirementIngredient::refine),
            Codec.BOOL.optionalFieldOf("broken",false).forGetter(BladeRequirementIngredient::broken),
            Codec.unboundedMap(Identifier.CODEC,Codec.intRange(1,255)).optionalFieldOf("enchantments",Map.of()).forGetter(BladeRequirementIngredient::enchantments),
            ItemStackTemplate.CODEC.fieldOf("display").forGetter(BladeRequirementIngredient::shown)
    ).apply(i,BladeRequirementIngredient::new));
    @Override public boolean test(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemSlashBlade)) return false;
        var state=SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        return (name.isEmpty() || state.getTranslationKey().equals(name)) && state.getKillCount()>=kills
                && state.getRefine()>=refine && (!broken || state.isBroken())
                && enchantments.entrySet().stream().allMatch(e -> SBEnchantments.level(ResourceKey.create(Registries.ENCHANTMENT,e.getKey()),stack)>=e.getValue());
    }
    @Override public Stream<Holder<Item>> items() { return Stream.of(SBItems.slashblade.builtInRegistryHolder()); }
    @Override public boolean isSimple() { return false; }
    @Override public IngredientType<?> getType() { return SBRecipes.BLADE_REQUIREMENT.get(); }
    @Override public SlotDisplay display() { return new SlotDisplay.ItemStackSlotDisplay(shown); }
}
