package mods.flammpfeil.slashblade.event;
import mods.flammpfeil.slashblade.compat.SBEnchantments;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
public final class ResharpedEnchantmentHooks {
    @SubscribeEvent public static void fireProtection(LivingIncomingDamageEvent event) {
        var stack=event.getEntity().getMainHandItem();
        if(stack.getItem() instanceof ItemSlashBlade && event.getSource().is(DamageTypeTags.IS_FIRE) && SBEnchantments.level(Enchantments.FIRE_PROTECTION,stack)>0)event.setCanceled(true);
    }
    @SubscribeEvent public static void charge(SlashBladeEvent.ChargeActionEvent event) {
        var state=event.getSlashBladeState();
        if(state.isBroken() || state.isSealed() || !event.getEntityLiving().getMainHandItem().isEnchanted())event.setCanceled(true);
    }
}
