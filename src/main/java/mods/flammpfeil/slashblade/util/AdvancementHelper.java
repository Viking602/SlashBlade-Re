package mods.flammpfeil.slashblade.util;

import mods.flammpfeil.slashblade.SlashBlade;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class AdvancementHelper {

    public static void grantCriterion(LivingEntity entity, Identifier resourcelocation){
        if(entity instanceof ServerPlayer)
            grantCriterion((ServerPlayer) entity, resourcelocation);
    }

    public static void grantCriterion(ServerPlayer player, Identifier resourcelocation){
        AdvancementHolder adv = player.level().getServer().getAdvancements().get(resourcelocation);
        if(adv == null) return;

        AdvancementProgress advancementprogress = player.getAdvancements().getOrStartProgress(adv);
        if (advancementprogress.isDone()) return;

        for(String s : advancementprogress.getRemainingCriteria()) {
            player.getAdvancements().award(adv, s);
        }
    }

    static final Identifier EXEFFECT_ENCHANTMENT = Identifier.fromNamespaceAndPath(SlashBlade.modid, "enchantment/");
    static public void grantedIf(net.minecraft.resources.ResourceKey<Enchantment> enchantment, LivingEntity owner){
        int level = mods.flammpfeil.slashblade.compat.SBEnchantments.livingLevel(enchantment, owner);
        if(0 < level) {
            grantCriterion(owner, EXEFFECT_ENCHANTMENT.withSuffix("root"));
            grantCriterion(owner, EXEFFECT_ENCHANTMENT.withSuffix(enchantment.identifier().getPath()));
        }
    }
}
