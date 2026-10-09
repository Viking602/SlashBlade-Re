package mods.flammpfeil.slashblade.event;

import mods.flammpfeil.slashblade.compat.SBItemData;
import mods.flammpfeil.slashblade.compat.SBData;
import com.google.common.collect.Maps;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.NBTHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Map;

public class AnvilCraftingRecipe {
    int level;
    int killcount;
    int refine;
    boolean broken;
    boolean noScabbard;
    String translationKey;
    Map<net.minecraft.core.Holder<Enchantment>, Integer> Enchantments;

    ItemStack result;

    CompoundTag overwriteTag;

    public AnvilCraftingRecipe() {
        this.level = 1;
        this.killcount = 0;
        this.refine = 0;
        this.broken = false;
        this.noScabbard = false;
        this.translationKey = "";
        this.Enchantments = Maps.newHashMap();
        this.result = ItemStack.EMPTY;
        this.overwriteTag = null;
    }

    static public AnvilCraftingRecipe getRecipe(ItemStack material){

        if(!SBItemData.hasTag(material))
            return null;

        CompoundTag tag = SBItemData.tag(material);


        if(!tag.contains("RequiredBlade"))
            return null;

        AnvilCraftingRecipe recipe = new AnvilCraftingRecipe();

        recipe.readNBT(tag.getCompoundOrEmpty("RequiredBlade"));

        recipe.setEnchantments(mods.flammpfeil.slashblade.compat.SBEnchantments.map(material));

        return recipe;
    }

    public void readNBT(CompoundTag tag){
        NBTHelper.getNBTCoupler(tag)
                .get("level", this::setLevel)
                .get("killCount", this::setKillcount)
                .get("refine", this::setRefine)
                .get("broken", this::setBroken)
                .get("noScabbard", this::setNoScabbard)
                .get("translationKey", this::setTranslationKey)
                .get("result", this::setResultWithNBT)
                .get("overwriteTag", this::setOverwriteTag);
    }

    public Tag writeNBT(){
        CompoundTag tag = new CompoundTag();

        NBTHelper.getNBTCoupler(tag)
                .put("level", this.getLevel())
                .put("killCount", this.getKillcount())
                .put("refine", this.getRefine())
                .put("broken", this.isBroken())
                .put("noScabbard", this.isNoScabbard())
                .put("translationKey", this.getTranslationKey())
                .put("result", SBItemData.save(this.getResult()))
                .put("overwriteTag", this.getOverwriteTag());

        return tag;
    }

    public boolean matches(ItemStack base){
        if(base.isEmpty()) return false;

        if(!this.translationKey.isEmpty()){
            if(!SBItemData.descriptionId(base).equals(this.translationKey))
                return false;
        }

        if(needBlade()){
            if(!(base.getItem() instanceof ItemSlashBlade))
                return false;

            /**
             * isBroken check
             * this=true  :match
             * this=false :any
             *
             * this  = base  = result
             * true  = ture  = ture
             * false = true  = true
             * true  = false = false
             * false = false = ture
             *
             * not((this xor base ) and this)
             */

            boolean stateMatches = SBData.get(base, ItemSlashBlade.BLADESTATE).filter(
                    (state)->(this.getKillcount() <= state.getKillCount())
                    && (this.getRefine() <= state.getRefine())
                    && (this.isNoScabbard() == state.isNoScabbard())
                    && !((this.isBroken() ^ state.isBroken()) & this.isBroken())
            ).isPresent();

            if(!stateMatches)
                return false;
        }

        if(!this.getEnchantments().isEmpty()){
            for (Map.Entry<net.minecraft.core.Holder<Enchantment>,Integer> entry : this.getEnchantments().entrySet()) {
                if(mods.flammpfeil.slashblade.compat.SBEnchantments.level(entry.getKey(), base) < entry.getValue())
                    return false;
            }
        }

        return true;
    }

    public boolean needBlade(){
        return this.getKillcount() != 0
                        || this.getRefine() != 0
                        || this.broken == true
                        || this.noScabbard == true;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getKillcount() {
        return killcount;
    }

    public void setKillcount(int killcount) {
        this.killcount = killcount;
    }

    public int getRefine() {
        return refine;
    }

    public void setRefine(int refine) {
        this.refine = refine;
    }

    public boolean isBroken() {
        return broken;
    }

    public void setBroken(boolean broken) {
        this.broken = broken;
    }

    public boolean isNoScabbard() {
        return noScabbard;
    }

    public void setNoScabbard(boolean noScabbard) {
        this.noScabbard = noScabbard;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public void setTranslationKey(String translationKey) {
        this.translationKey = translationKey;
    }

    public Map<net.minecraft.core.Holder<Enchantment>, Integer> getEnchantments() {
        return Enchantments;
    }

    public void setEnchantments(Map<net.minecraft.core.Holder<Enchantment>, Integer> enchantments) {
        Enchantments = enchantments;
    }

    public ItemStack getResult() {
        return result.copy();
    }

    public ItemStack getResult(ItemStack base){

        ItemStack result;

        if(isOnlyTagOverwrite()){
            //refine item

            CompoundTag tag = SBItemData.save(base);
            tag.merge(this.getOverwriteTag().copy());

            result = SBItemData.load(tag);

        }else{
            //reforge item

            result = this.getResult();

            SBData.get(base, ItemSlashBlade.BLADESTATE).ifPresent((oldState)->{
                SBData.get(result, ItemSlashBlade.BLADESTATE).ifPresent((newState)->{
                    newState.setProudSoulCount(oldState.getProudSoulCount());
                    newState.setKillCount(oldState.getKillCount());
                    newState.setRefine(oldState.getRefine());
                });
            });


            Map<net.minecraft.core.Holder<Enchantment>, Integer> destMap = mods.flammpfeil.slashblade.compat.SBEnchantments.map(result);
            Map<net.minecraft.core.Holder<Enchantment>, Integer> srcMap = mods.flammpfeil.slashblade.compat.SBEnchantments.map(base);

            for (Map.Entry<net.minecraft.core.Holder<Enchantment>, Integer> srcEntry : srcMap.entrySet()) {
                net.minecraft.core.Holder<Enchantment> key = srcEntry.getKey();
                int srcLevel = srcEntry.getValue();

                if(destMap.containsKey(key)){
                    //Overwrite with higher
                    int destLevel = destMap.get(key);
                    if(destLevel < srcLevel)
                        destMap.put(key, Math.min(srcLevel,key.value().getMaxLevel()));
                }else{
                    if (destMap.keySet().stream().allMatch(existing -> net.minecraft.world.item.enchantment.Enchantment.areCompatible(existing,key)))
                        destMap.put(key, Math.min(srcLevel,key.value().getMaxLevel()));
                }
            }

            mods.flammpfeil.slashblade.compat.SBEnchantments.set(destMap, result);
        }

        //update tag state
        SBItemData.tag(result);

        return result;
    }

    public void setResult(ItemStack result) {
        this.result = result;
    }
    public void setResultWithNBT(CompoundTag tag){
        this.setResult(SBItemData.load(tag));
    }

    public boolean isOnlyTagOverwrite() {
        return overwriteTag != null;
    }

    public CompoundTag getOverwriteTag() {
        return overwriteTag;
    }

    public void setOverwriteTag(CompoundTag overwriteTag) {
        this.overwriteTag = overwriteTag;
    }
}
