package mods.flammpfeil.slashblade.verification;

import java.util.*;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.init.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantments;

/** Checks reference tooltip conditions and the final tooltip after other mods' events. */
public final class ResharpedTooltipClientProbe {
    public static Map<String,Object> verify() {
        var mc=Minecraft.getInstance();var stack=new ItemStack(SBItems.slashblade);
        var state=SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        expect(stack,List.of("slashblade.sword_type.noname"));
        require(stack.getRarity()==Rarity.COMMON,"plain rarity");
        stack.enchant(mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING),1);
        expect(stack,List.of("slashblade.sword_type.enchanted"));
        require(stack.getRarity()==Rarity.RARE,"enchanted rarity");
        state.setDefaultBewitched(true);state.setProudSoulCount(162);state.setKillCount(200);state.setRefine(3);
        var keys=List.of("slashblade.sword_type.bewitched","slashblade.tooltip.proud_soul","slashblade.tooltip.killcount","slashblade.tooltip.slash_art","slashblade.tooltip.refine");
        var lines=expect(stack,keys);
        require(stack.getRarity()==Rarity.EPIC,"bewitched rarity");
        require(lines.get(0).getStyle().getColor().getValue()==ChatFormatting.DARK_PURPLE.getColor(),"bewitched color");
        require(lines.get(3).getStyle().getColor().getValue()==ChatFormatting.GRAY.getColor(),"SA color");
        require(lines.get(4).getStyle().getColor().getValue()==ChatFormatting.GRAY.getColor(),"initial refine color");
        state.setProudSoulCount(10001);state.setKillCount(1001);
        lines=expect(stack,keys);
        require(lines.get(0).getStyle().getColor().getValue()==ChatFormatting.GOLD.getColor(),"mastered type color");
        for(int i=1;i<=2;i++)require(lines.get(i).getStyle().getColor().getValue()==ChatFormatting.DARK_PURPLE.getColor(),"growth color");
        state.setSealed(true);
        expect(stack,List.of("slashblade.tooltip.proud_soul","slashblade.tooltip.killcount","slashblade.tooltip.refine"));
        state.setSealed(false);
        for(var item:List.of(SBItems.slashblade_wood,SBItems.slashblade_bamboo,SBItems.slashblade_silverbamboo,SBItems.slashblade_white)) {
            var simple=new ItemStack(item);
            simple.enchant(mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING),1);
            expect(simple,List.of());require(simple.getRarity()==Rarity.COMMON,"detuned blade rarity");
        }
        var sphere=new ItemStack(SBItems.proudsoul_sphere);
        mods.flammpfeil.slashblade.compat.SBItemData.update(sphere,t -> t.putString("SpecialAttackType","slashblade:judgement_cut"));
        expect(sphere,List.of("slashblade.tooltip.slash_art"));
        var crystal=new ItemStack(SBItems.proudsoul_crystal);
        mods.flammpfeil.slashblade.compat.SBItemData.update(crystal,t -> t.putString("SpecialEffectType","slashblade:wither_edge"));
        int previousLevel=mc.player.experienceLevel;
        try {
            for(int level:new int[]{0,20}) {
                mc.player.experienceLevel=level;
                var effect=expect(crystal,List.of("slashblade.tooltip.special_effect")).getFirst();
                var args=((TranslatableContents)effect.getContents()).getArgs();
                var required=(Component)args[1];
                require(required.getString().equals("20") && required.getStyle().getColor().getValue()==(level>=20?ChatFormatting.RED:ChatFormatting.DARK_GRAY).getColor(),"SE required level color");
            }
        } finally {mc.player.experienceLevel=previousLevel;}
        int checked=0;var rendered=new LinkedHashMap<String,Object>();
        var items=new ArrayList<ItemStack>(BladeCatalog.items());
        for(var item:List.of(SBItems.proudsoul,SBItems.proudsoul_tiny,SBItems.proudsoul_ingot,SBItems.proudsoul_sphere,SBItems.proudsoul_crystal,SBItems.proudsoul_trapezohedron)) {
            var soul=new ItemStack(item);expect(soul,List.of());items.add(soul);
        }
        for(var item:items) {
            var name=item.getCustomName()!=null?item.getCustomName():item.getItemName();
            require(item.getHoverName().equals(name),"external mod recolored item name: "+name.getString());
            var text=item.getTooltipLines(Item.TooltipContext.of(mc.level,mc.player),mc.player,TooltipFlag.NORMAL).stream().map(Component::getString).toList();
            require(text.stream().noneMatch(s -> s.contains("★") || s.contains("☆") || s.contains("超级 SA") || s.contains("Super SA") || s.contains("slashblade.tooltip.")),"foreign or untranslated description: "+text);
            if(checked++<3)rendered.put(item.getHoverName().getString(),text);
        }
        if(net.neoforged.fml.ModList.get().isLoaded("raritycore")) {
            var other=new ItemStack(Items.DIAMOND_SWORD).getTooltipLines(Item.TooltipContext.of(mc.level,mc.player),mc.player,TooltipFlag.NORMAL);
            require(other.stream().anyMatch(c -> c.getString().contains("★") || c.getString().contains("☆")),"compatibility suppressed unrelated item rarity");
        }
        return Map.of("status","passed","finalTooltips",checked,"samples",rendered,"scope","reference field order, visibility, rarity, growth colors, empty souls; no extra hints or RarityCore stars on SlashBlade items");
    }
    private static List<Component> expect(ItemStack stack,List<String> expected) {
        var mc=Minecraft.getInstance();var lines=new ArrayList<Component>();
        stack.getItem().appendHoverText(stack,Item.TooltipContext.of(mc.level,mc.player),TooltipDisplay.DEFAULT,lines::add,TooltipFlag.NORMAL);
        var keys=lines.stream().map(c -> c.getContents() instanceof TranslatableContents t?t.getKey():c.getString()).toList();
        require(keys.equals(expected),"fields: "+keys+" expected "+expected);return lines;
    }
    private static void require(boolean b,String text) {if(!b)throw new IllegalStateException("Resharped tooltip: "+text);}
    private ResharpedTooltipClientProbe() {}
}
