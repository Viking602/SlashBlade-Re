package mods.flammpfeil.slashblade.compat;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.crafting.*;
import java.util.stream.Stream;

/** Partial component matching and an explicit displayed stack replace the old forge:nbt ingredient. */
public record BladeIngredient(Holder<Item> item, CompoundTag blade, CompoundTag custom, ItemStackTemplate shown) implements ICustomIngredient {
    public static final MapCodec<BladeIngredient> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Item.CODEC.fieldOf("item").forGetter(BladeIngredient::item),
        CompoundTag.CODEC.optionalFieldOf("blade", new CompoundTag()).forGetter(BladeIngredient::blade),
        CompoundTag.CODEC.optionalFieldOf("custom", new CompoundTag()).forGetter(BladeIngredient::custom),
        ItemStackTemplate.CODEC.fieldOf("display").forGetter(BladeIngredient::shown)
    ).apply(i, BladeIngredient::new));
    @Override public boolean test(ItemStack stack) {
        return stack.is(item) && SBItemData.matches(blade, stack.getOrDefault(SBData.BLADE_STATE.get(), CustomData.EMPTY).copyTag())
            && SBItemData.matches(custom, SBItemData.tag(stack));
    }
    @Override public Stream<Holder<Item>> items() { return Stream.of(item); }
    @Override public boolean isSimple() { return false; }
    @Override public IngredientType<?> getType() { return SBRecipes.BLADE_INGREDIENT.get(); }
    @Override public SlotDisplay display() { return new SlotDisplay.ItemStackSlotDisplay(shown); }
}
