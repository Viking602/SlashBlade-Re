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
    public static int soulGain(int experience,int rank) { return Math.min(mods.flammpfeil.slashblade.SlashBladeConfig.MAX_PROUD_SOUL_GOT.get(),Math.max(0,(int)Math.floor(experience*(1+rank*.1)))); }
    public static int addClamped(int value,long addition) { return (int)Math.clamp((long)value+addition,0L,Integer.MAX_VALUE); }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void experience(LivingExperienceDropEvent event) {
        var player=event.getAttackingPlayer();
        if(player==null || player.level().isClientSide()) return;
        SBData.get(player.getMainHandItem(),ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            int rank=SBData.get(player,CapabilityConcentrationRank.RANK_POINT).map(r -> r.getRank(player.level().getGameTime()).level).orElse(0);
            int gain=soulGain(event.getDroppedExperience(),rank);
            var gained=new SlashBladeEvent.AddProudSoulEvent(player.getMainHandItem(),state,gain);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(gained);
            gain=Math.max(0,gained.getNewCount());
            state.setProudSoulCount(addClamped(state.getProudSoulCount(),gain));
            if(state.getProudSoulCount()>=10000 && gain>0)
                state.setDamage(state.getDamage()-Math.max(1,gain/4)/(float)state.getMaxDamage());
        });
    }
    public static String zombieBlade(float roll,float difficulty) {
        if(roll>=mods.flammpfeil.slashblade.SlashBladeConfig.BROKEN_SABIGATANA_SPAWN_CHANCE.get()*difficulty) return "";
        return roll<mods.flammpfeil.slashblade.SlashBladeConfig.SABIGATANA_SPAWN_CHANCE.get()*difficulty ? "sabigatana" : "sabigatana_broken";
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
        var registry=victim.level().registryAccess().lookupOrThrow(mods.flammpfeil.slashblade.event.drop.EntityDropEntry.REGISTRY_KEY);
        var blades=victim.level().registryAccess().lookupOrThrow(mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition.REGISTRY_KEY);
        int looting=SBEnchantments.level(Enchantments.LOOTING,attacker.getMainHandItem());
        registry.listElements().map(net.minecraft.core.Holder::value).filter(r->r.entityType().equals(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()))).forEach(rule->{
            if(rule.requestSlashBladeKill() && !(attacker.getMainHandItem().getItem() instanceof ItemSlashBlade))return;
            if(victim.getRandom().nextFloat()>Math.min(1,rule.dropRate()+looting*.1f))return;
            var definition=blades.get(net.minecraft.resources.ResourceKey.create(mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition.REGISTRY_KEY,rule.bladeName()));if(definition.isEmpty())return;
            var position=rule.dropFixedPoint()?rule.dropPoint():victim.position();
            var item=new ItemEntity(victim.level(),position.x,position.y,position.z,definition.get().value().getBlade());
            var drop=new BladeItemEntity(SlashBlade.RegistryEvents.BladeItem,victim.level());
            drop.restoreFrom(item);drop.init();drop.push(0,.4,0);drop.setPickUpDelay(40);drop.setGlowingTag(true);drop.setAirSupply(-1);event.getDrops().add(drop);
        });
    }
}
