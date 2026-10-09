package mods.flammpfeil.slashblade.event;

import mods.flammpfeil.slashblade.compat.SBItemData;
import mods.flammpfeil.slashblade.util.NBTHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;

public class BladeMaterialTooltips {
    private static final class SingletonHolder {
        private static final BladeMaterialTooltips instance = new BladeMaterialTooltips();
    }
    public static BladeMaterialTooltips getInstance() {
        return SingletonHolder.instance;
    }
    private BladeMaterialTooltips(){}
    public void register(){
        NeoForge.EVENT_BUS.register(this);
    }

    static public final String BLADE_DATA = "BladeData";

    @SubscribeEvent
    public void onItemTooltipEvent(ItemTooltipEvent event) {
        List<Component> tooltip = event.getToolTip();

        ItemStack stack = event.getItemStack();

        if(SBItemData.hasTag(stack) && SBItemData.tag(stack).contains(BLADE_DATA)){
            CompoundTag bladeData = SBItemData.tag(stack).getCompoundOrEmpty(BLADE_DATA);

            ItemStack recovered = SBItemData.load(bladeData);
            String translationKey = recovered.isEmpty() ? "" : SBItemData.descriptionId(recovered);

            event.getToolTip().add(Component.translatable(translationKey));
        }
    }

}
