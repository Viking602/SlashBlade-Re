package mods.flammpfeil.slashblade.compat;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mods.flammpfeil.slashblade.event.AnvilCraftingRecipe;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

/** Resharped-style direct upgrades, retaining the player's blade progress on 26.1.2. */
public final class BladeUpgradeRecipe extends ShapedRecipe {
    public static final MapCodec<BladeUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.info),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> r.book),
            ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.shape),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.output)
    ).apply(i, BladeUpgradeRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, BladeUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, r -> r.info,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, r -> r.book,
            ShapedRecipePattern.STREAM_CODEC, r -> r.shape,
            ItemStackTemplate.STREAM_CODEC, r -> r.output, BladeUpgradeRecipe::new);
    private final Recipe.CommonInfo info;
    private final CraftingRecipe.CraftingBookInfo book;
    private final ShapedRecipePattern shape;
    private final ItemStackTemplate output;

    public BladeUpgradeRecipe(Recipe.CommonInfo info, CraftingRecipe.CraftingBookInfo book,
                              ShapedRecipePattern shape, ItemStackTemplate output) {
        super(info, book, shape, output);
        this.info=info; this.book=book; this.shape=shape; this.output=output;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override public RecipeSerializer<ShapedRecipe> getSerializer() { return (RecipeSerializer)SBRecipes.BLADE_UPGRADE.get(); }
    @Override public ItemStack assemble(CraftingInput input) {
        ItemStack base=ItemStack.EMPTY;
        for (int slot=0;slot<input.size();slot++) {
            var candidate=input.getItem(slot);
            if (candidate.getItem() instanceof ItemSlashBlade) {
                // Upgrades consume exactly one existing blade, never silently discard another.
                if (!base.isEmpty()) return ItemStack.EMPTY;
                base=candidate;
            }
        }
        if (base.isEmpty()) return output.create();
        var upgrade=new AnvilCraftingRecipe(); upgrade.setResult(output.create());
        var result=upgrade.getResult(base);
        var oldState=SBData.get(base,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        var state=SBData.get(result,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        state.setUniqueId(oldState.getUniqueId());
        state.setOwner(oldState.getOwner());
        if (base.has(DataComponents.CUSTOM_NAME)) result.set(DataComponents.CUSTOM_NAME,base.get(DataComponents.CUSTOM_NAME));
        return result;
    }
}
