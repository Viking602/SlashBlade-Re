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
                if (base.isEmpty()) base=candidate;
            }
        }
        var result=base.isEmpty() ? output.create() : transfer(base,output.create());
        if(!base.isEmpty()) {
            var progress=SBData.get(result,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            long souls=0,kills=0,refine=0;var effects=new java.util.LinkedHashSet<>(progress.getSpecialEffects());
            for(var ingredient:input.items())if(ingredient.getItem() instanceof ItemSlashBlade){
                var s=SBData.get(ingredient,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
                souls+=s.getProudSoulCount();kills+=s.getKillCount();
                refine=mods.flammpfeil.slashblade.SlashBladeConfig.DO_CRAFTING_SUM_REFINE.get()?refine+s.getRefine():Math.max(refine,s.getRefine());
                effects.addAll(s.getSpecialEffects());mergeEnchantments(ingredient,result);
            }
            progress.setProudSoulCount((int)Math.min(Integer.MAX_VALUE,souls));progress.setKillCount((int)Math.min(Integer.MAX_VALUE,kills));progress.setRefine((int)Math.min(Integer.MAX_VALUE,refine));progress.setSpecialEffects(effects);
        }
        // Vanilla swords in the wooden starter and Rodai routes also carry enchantments.
        for(var source:input.items()) if(!(source.getItem() instanceof ItemSlashBlade)) mergeEnchantments(source,result);
        return result;
    }
    public static ItemStack transfer(ItemStack base, ItemStack result) {
        var id=SBItemData.tag(result).getStringOr("resharped_definition","");
        if(!id.isEmpty()) { var resolved=mods.flammpfeil.slashblade.init.BladeCatalog.blade(id);if(!resolved.isEmpty())result=resolved; }
        var upgrade=new AnvilCraftingRecipe(); upgrade.setResult(result);
        result=upgrade.getResult(base);
        var oldState=SBData.get(base,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        var state=SBData.get(result,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        state.setProudSoulCount(oldState.getProudSoulCount());
        state.setUniqueId(oldState.getUniqueId());
        state.setOwner(oldState.getOwner());
        var effects=new java.util.LinkedHashSet<>(state.getSpecialEffects());effects.addAll(oldState.getSpecialEffects());state.setSpecialEffects(effects);
        if (base.has(DataComponents.CUSTOM_NAME)) result.set(DataComponents.CUSTOM_NAME,base.get(DataComponents.CUSTOM_NAME));
        return result;
    }
    public static void mergeEnchantments(ItemStack source,ItemStack target) {
        var dest=SBEnchantments.map(target);
        SBEnchantments.map(source).forEach((enchantment,level) -> {
            int clamped=Math.min(level,enchantment.value().getMaxLevel());
            if(dest.containsKey(enchantment)) dest.put(enchantment,Math.max(dest.get(enchantment),clamped));
            else if(dest.keySet().stream().allMatch(e -> net.minecraft.world.item.enchantment.Enchantment.areCompatible(e,enchantment))) dest.put(enchantment,clamped);
        });
        SBEnchantments.set(dest,target);
    }
}
