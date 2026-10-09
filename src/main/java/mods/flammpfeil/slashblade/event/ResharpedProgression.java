package mods.flammpfeil.slashblade.event;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.compat.*;
import mods.flammpfeil.slashblade.init.BladeCatalog;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.entity.BladeItemEntity;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.entity.living.*;

/** Acquisition and growth rules from Resharped 6e2a0a0 (default configuration). */
public final class ResharpedProgression {
    public static int soulGain(int experience,int rank) { return Math.min(100,Math.max(0,(int)Math.floor(experience*(1+rank*.1)))); }
    public static int addClamped(int value,long addition) { return (int)Math.clamp((long)value+addition,0L,Integer.MAX_VALUE); }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void experience(LivingExperienceDropEvent event) {
        var player=event.getAttackingPlayer();
        if(player==null || player.level().isClientSide()) return;
        SBData.get(player.getMainHandItem(),ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            int rank=SBData.get(player,CapabilityConcentrationRank.RANK_POINT).map(r -> r.getRank(player.level().getGameTime()).level).orElse(0);
            int gain=soulGain(event.getDroppedExperience(),rank);
            state.setProudSoulCount(addClamped(state.getProudSoulCount(),gain));
            if(state.getProudSoulCount()>=10000 && gain>0)
                state.setDamage(state.getDamage()-Math.max(1,gain/4)/(float)state.getMaxDamage());
        });
    }
    public static String zombieBlade(float roll,float difficulty) {
        if(roll>=.15f*difficulty) return "";
        return roll<.05f*difficulty ? "sabigatana" : "sabigatana_broken";
    }
    @SubscribeEvent
    public void spawn(FinalizeSpawnEvent event) {
        var mob=event.getEntity();
        if(!(mob instanceof Zombie) || mob instanceof Drowned || mob instanceof ZombifiedPiglin || !mob.getMainHandItem().isEmpty()) return;
        String blade=zombieBlade(mob.getRandom().nextFloat(),event.getDifficulty().getSpecialMultiplier());
        if(!blade.isEmpty()) mob.setItemSlot(EquipmentSlot.MAINHAND,BladeCatalog.blade(blade));
    }
    public record Drop(String blade,float chance,boolean requiresBlade,boolean fixed) {}
    public static Drop dropFor(String entity) {
        return switch(entity) {
            case "minecraft:ender_dragon" -> new Drop("yamato_broken",1,false,true);
            case "minecraft:wither" -> new Drop("sange",.3f,true,false);
            case "twilightforest:naga" -> new Drop("agito_rust",.3f,false,false);
            case "twilightforest:hydra" -> new Drop("orotiagito_rust",.3f,false,false);
            case "twilightforest:minotaur" -> new Drop("yasha",.05f,true,false);
            case "twilightforest:minoshroom" -> new Drop("yasha_true",.2f,true,false);
            default -> null;
        };
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void drops(LivingDropsEvent event) {
        var victim=event.getEntity();
        if(victim.level().isClientSide() || !(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        var rule=dropFor(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString());
        if(rule==null || rule.requiresBlade() && !(attacker.getMainHandItem().getItem() instanceof ItemSlashBlade)) return;
        int looting=SBEnchantments.level(Enchantments.LOOTING,attacker.getMainHandItem());
        if(victim.getRandom().nextFloat()>Math.min(1,rule.chance()+looting*.1f)) return;
        var item=new ItemEntity(victim.level(),rule.fixed()?0:victim.getX(),rule.fixed()?60:victim.getY(),rule.fixed()?0:victim.getZ(),BladeCatalog.blade(rule.blade()));
        var drop=new BladeItemEntity(SlashBlade.RegistryEvents.BladeItem,victim.level());
        drop.restoreFrom(item);drop.init();drop.push(0,.4,0);drop.setPickUpDelay(40);drop.setGlowingTag(true);drop.setAirSupply(-1);
        event.getDrops().add(drop);
    }
}
