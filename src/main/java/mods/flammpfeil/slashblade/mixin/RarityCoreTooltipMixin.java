package mods.flammpfeil.slashblade.mixin;

import mods.flammpfeil.slashblade.SlashBlade;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep Resharped's own item labels/colors when the optional rarity overlay is installed. */
@Pseudo
@Mixin(targets="org.yanbwe.raritycore.client.RarityTooltipHandler",remap=false)
public abstract class RarityCoreTooltipMixin {
    @Inject(method="onItemTooltip",at=@At("HEAD"),cancellable=true,require=0)
    private static void slashblade$originalTooltip(ItemTooltipEvent event,CallbackInfo ci) {
        if(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).getNamespace().equals(SlashBlade.modid))ci.cancel();
    }
}
