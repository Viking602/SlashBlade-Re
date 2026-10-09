package mods.flammpfeil.slashblade.compat;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

/** Netherite Rodai keeps progress while replacing the blade definition, not its item component wholesale. */
public final class BladeSmithingRecipe extends SmithingTransformRecipe {
    public static final MapCodec<BladeSmithingRecipe> CODEC=RecordCodecBuilder.mapCodec(i -> i.group(
        Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.info),Ingredient.CODEC.optionalFieldOf("template").forGetter(BladeSmithingRecipe::templateIngredient),
        Ingredient.CODEC.fieldOf("base").forGetter(BladeSmithingRecipe::baseIngredient),Ingredient.CODEC.optionalFieldOf("addition").forGetter(BladeSmithingRecipe::additionIngredient),
        ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.output)
    ).apply(i,BladeSmithingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,BladeSmithingRecipe> STREAM_CODEC=StreamCodec.composite(
        Recipe.CommonInfo.STREAM_CODEC,r -> r.info,Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC,BladeSmithingRecipe::templateIngredient,
        Ingredient.CONTENTS_STREAM_CODEC,BladeSmithingRecipe::baseIngredient,Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC,BladeSmithingRecipe::additionIngredient,
        ItemStackTemplate.STREAM_CODEC,r -> r.output,BladeSmithingRecipe::new);
    private final Recipe.CommonInfo info;
    private final ItemStackTemplate output;
    public BladeSmithingRecipe(Recipe.CommonInfo info,Optional<Ingredient> template,Ingredient base,Optional<Ingredient> addition,ItemStackTemplate output) {
        super(info,template,base,addition,output);this.info=info;this.output=output;
    }
    @Override public ItemStack assemble(SmithingRecipeInput input) { return BladeUpgradeRecipe.transfer(input.base(),output.create()); }
    @SuppressWarnings({"unchecked","rawtypes"})
    @Override public RecipeSerializer<SmithingTransformRecipe> getSerializer() { return (RecipeSerializer)SBRecipes.BLADE_SMITHING.get(); }
}
