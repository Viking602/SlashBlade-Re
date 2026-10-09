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
public record BladeRequirementIngredient(String name, int kills, int refine, boolean broken, int proudSoul, boolean sealed, String item,
                                          Map<Identifier,Integer> enchantments, ItemStackTemplate shown) implements ICustomIngredient {
    public BladeRequirementIngredient(String name,int kills,int refine,boolean broken,Map<Identifier,Integer> enchantments,ItemStackTemplate shown) {
        this(name,kills,refine,broken,0,false,"slashblade:slashblade",enchantments,shown);
    }
    public static final MapCodec<BladeRequirementIngredient> CODEC=RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(BladeRequirementIngredient::name),
            Codec.intRange(0,Integer.MAX_VALUE).optionalFieldOf("kills",0).forGetter(BladeRequirementIngredient::kills),
            Codec.intRange(0,Integer.MAX_VALUE).optionalFieldOf("refine",0).forGetter(BladeRequirementIngredient::refine),
            Codec.BOOL.optionalFieldOf("broken",false).forGetter(BladeRequirementIngredient::broken),
            Codec.intRange(0,Integer.MAX_VALUE).optionalFieldOf("proud_soul",0).forGetter(BladeRequirementIngredient::proudSoul),
            Codec.BOOL.optionalFieldOf("sealed",false).forGetter(BladeRequirementIngredient::sealed),
            Codec.STRING.optionalFieldOf("item","slashblade:slashblade").forGetter(BladeRequirementIngredient::item),
            Codec.unboundedMap(Identifier.CODEC,Codec.intRange(1,255)).optionalFieldOf("enchantments",Map.of()).forGetter(BladeRequirementIngredient::enchantments),
            ItemStackTemplate.CODEC.fieldOf("display").forGetter(BladeRequirementIngredient::shown)
    ).apply(i,BladeRequirementIngredient::new));
    @Override public boolean test(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemSlashBlade)) return false;
        var state=SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        return mods.flammpfeil.slashblade.init.BladeCatalog.baseItemIdentity(stack).equals(item)
                && mods.flammpfeil.slashblade.init.BladeCatalog.canonicalName(state.getTranslationKey()).equals(mods.flammpfeil.slashblade.init.BladeCatalog.canonicalName(name)) && state.getKillCount()>=kills
                && state.getRefine()>=refine && state.getProudSoulCount()>=proudSoul && (!broken || state.isBroken()) && (!sealed || state.isSealed())
                && enchantments.entrySet().stream().allMatch(e -> SBEnchantments.level(ResourceKey.create(Registries.ENCHANTMENT,e.getKey()),stack)>=e.getValue());
    }
    @Override public Stream<Holder<Item>> items() { return Stream.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.parse(item)),SBItems.slashblade).distinct().map(Item::builtInRegistryHolder); }
    @Override public boolean isSimple() { return false; }
    @Override public IngredientType<?> getType() { return SBRecipes.BLADE_REQUIREMENT.get(); }
    @Override public SlotDisplay display() { return new SlotDisplay.ItemStackSlotDisplay(shown); }
}
