package mods.flammpfeil.slashblade.init;
import java.util.*;
import mods.flammpfeil.slashblade.compat.SBItemData;
import mods.flammpfeil.slashblade.registry.*;
import mods.flammpfeil.slashblade.specialattack.SlashArts;
import net.minecraft.world.item.*;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
public final class ArtsItems {
    public static ItemStack art(Identifier id){var item=new ItemStack(SBItems.proudsoul_sphere);SBItemData.put(item,"SpecialAttackType",StringTag.valueOf(id.toString()));return item;}
    public static ItemStack effect(Identifier id){var item=new ItemStack(SBItems.proudsoul_crystal);SBItemData.put(item,"SpecialEffectType",StringTag.valueOf(id.toString()));return item;}
    public static List<ItemStack> all(){
        var result=new ArrayList<ItemStack>();
        SlashArtsRegistry.VALUES.entrySet().stream().sorted(Comparator.comparing(e->e.getKey().identifier().toString())).filter(e->e.getValue()!=SlashArts.NONE).forEach(e->result.add(art(e.getKey().identifier())));
        SpecialEffectsRegistry.VALUES.keySet().stream().sorted().forEach(id->result.add(effect(id)));
        SBItemData.registries().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).listElements().filter(e->new ItemStack(SBItems.slashblade).supportsEnchantment(e)).forEach(e->{
            var soul=new ItemStack(SBItems.proudsoul_tiny);soul.enchant(e,1);result.add(soul);
        });
        return result;
    }
}
