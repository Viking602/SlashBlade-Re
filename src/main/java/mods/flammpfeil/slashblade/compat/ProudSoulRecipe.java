package mods.flammpfeil.slashblade.compat;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.HashSet;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/** Resharped's material conversion preserves a single level-I enchantment. */
public final class ProudSoulRecipe extends ShapelessRecipe {
    public static final MapCodec<ProudSoulRecipe> CODEC=RecordCodecBuilder.mapCodec(i -> i.group(
        Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.info),CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> r.book),
        ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.output),Ingredient.CODEC.listOf(1,9).fieldOf("ingredients").forGetter(r -> r.inputs)
    ).apply(i,ProudSoulRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,ProudSoulRecipe> STREAM_CODEC=StreamCodec.composite(
        Recipe.CommonInfo.STREAM_CODEC,r -> r.info,CraftingRecipe.CraftingBookInfo.STREAM_CODEC,r -> r.book,
        ItemStackTemplate.STREAM_CODEC,r -> r.output,Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),r -> r.inputs,ProudSoulRecipe::new);
    private final Recipe.CommonInfo info;
    private final CraftingRecipe.CraftingBookInfo book;
    private final ItemStackTemplate output;
    private final List<Ingredient> inputs;
    public ProudSoulRecipe(Recipe.CommonInfo info,CraftingRecipe.CraftingBookInfo book,ItemStackTemplate output,List<Ingredient> inputs) {
        super(info,book,output,inputs);this.info=info;this.book=book;this.output=output;this.inputs=inputs;
    }
    public static boolean validEnchantments(CraftingInput input) {
        var kinds=new HashSet<Holder<Enchantment>>();
        for(var stack:input.items()) for(var e:SBEnchantments.map(stack).entrySet()) {
            if(e.getValue()!=1) return false;
            kinds.add(e.getKey());
        }
        return kinds.size()<=1;
    }
    @Override public boolean matches(CraftingInput input,Level level) { return validEnchantments(input) && super.matches(input,level); }
    @Override public ItemStack assemble(CraftingInput input) {
        if(!validEnchantments(input)) return ItemStack.EMPTY;
        var result=output.create();
        for(var stack:input.items()) if(stack.isEnchanted()) { SBEnchantments.set(SBEnchantments.map(stack),result);break; }
        return result;
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @Override public RecipeSerializer<ShapelessRecipe> getSerializer() { return (RecipeSerializer)SBRecipes.PROUD_SOUL.get(); }
}
