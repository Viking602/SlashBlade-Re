package mods.flammpfeil.slashblade.mixin;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The modern component-based ItemStack no longer calls Forge's old Item.getRarity(stack) hook. */
@Mixin(ItemStack.class)
public abstract class BladeRarityMixin {
    @Inject(method="getRarity",at=@At("HEAD"),cancellable=true)
    private void slashblade$bladeRarity(CallbackInfoReturnable<Rarity> ci) {
        var stack=(ItemStack)(Object)this;
        if(stack.getItem() instanceof ItemSlashBlade blade)ci.setReturnValue(blade.getRarity(stack));
    }
}
