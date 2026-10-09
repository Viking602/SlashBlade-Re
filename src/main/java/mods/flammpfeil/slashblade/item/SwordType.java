package mods.flammpfeil.slashblade.item;

import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.world.item.ItemStack;
import mods.flammpfeil.slashblade.compat.LazyOptional;

import java.util.EnumSet;

public enum SwordType{
    None,
    EdgeFragment,
    Broken,
    Perfect,
    Enchanted,
    Bewitched,
    SoulEeater,
    FiercerEdge,
    NoScabbard,
    Sealed,
    Cursed,
    ;

    static public EnumSet<SwordType> from(ItemStack itemStackIn){
        EnumSet<SwordType> types = EnumSet.noneOf(SwordType.class);

        LazyOptional<ISlashBladeState> state = SBData.get(itemStackIn, ItemSlashBlade.BLADESTATE);

        if(state.isPresent()){
            SBData.get(itemStackIn, ItemSlashBlade.BLADESTATE).ifPresent(s->{
                if(s.isBroken())
                    types.add(Broken);

                if(s.isNoScabbard())
                    types.add(NoScabbard);

                if(s.isSealed())
                    types.add(Cursed);

                if(!s.isSealed() && itemStackIn.isEnchanted() && (itemStackIn.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) || s.isDefaultBewitched()))
                    types.add(Bewitched);
            });
        }else{
            types.add(NoScabbard);
            types.add(EdgeFragment);
        }


        if(itemStackIn.isEnchanted())
            types.add(Enchanted);

        return types;
    }
}
