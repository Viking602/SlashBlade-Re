package mods.flammpfeil.slashblade.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mods.flammpfeil.slashblade.SlashBlade;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Keep the original item name so vanilla can apply Resharped's rarity color.
 * RarityCore also recolors getHoverName directly, outside its tooltip event. */
@Mixin(value=ItemStack.class,priority=500)
public abstract class ResharpedItemNameMixin {
    @WrapMethod(method="getHoverName")
    private Component slashblade$originalName(Operation<Component> original) {
        var stack=(ItemStack)(Object)this;
        if(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(SlashBlade.modid)) {
            var custom=stack.getCustomName();return custom!=null?custom:stack.getItemName();
        }
        return original.call();
    }
}
