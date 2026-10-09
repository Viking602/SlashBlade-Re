package mods.flammpfeil.slashblade.event.bladestand;
import java.util.*;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.compat.*;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.*;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
/** The source's stand operations, using components and authoritative server events. */
public final class BladeStandActions {
    private static boolean tagged(ItemStack stack,String tag){return stack.is(ItemTags.create(SlashBlade.id(tag)));}
    @SubscribeEvent public static void attack(SlashBladeEvent.BladeStandAttackEvent event){
        if(!(event.getDamageSource().getEntity() instanceof Player player) || !(player.level() instanceof ServerLevel))return;
        var material=player.getMainHandItem();var blade=event.getBlade();var state=event.getSlashBladeState();
        if(blade.isEmpty())return;
        var data=SBItemData.tag(material);int cost=player.getAbilities().instabuild?0:1;
        var sa=data.getStringOr("SpecialAttackType","").isBlank()?null:Identifier.tryParse(data.getStringOr("SpecialAttackType",""));
        var se=data.getStringOr("SpecialEffectType","").isBlank()?null:Identifier.tryParse(data.getStringOr("SpecialEffectType",""));
        if(tagged(material,"can_change_sa") && sa!=null && SlashArtsRegistry.VALUES.containsKey(sa)){
            event.setCanceled(true);
            if(sa.toString().equals(state.getSlashArtsKey()))return;
            var change=new BladeChangeSpecialAttackEvent(blade,state,sa,event);change.setShrinkCount(cost);NeoForge.EVENT_BUS.post(change);
            if(change.isCanceled() || material.getCount()<change.getShrinkCount())return;
            state.setSlashArtsKey(change.getSAKey().toString());material.shrink(change.getShrinkCount());success(event);return;
        }
        if(tagged(material,"can_change_se") && se!=null && SpecialEffectsRegistry.VALUES.containsKey(se)){
            event.setCanceled(true);if(state.hasSpecialEffect(se))return;
            var change=new BladeChangeSpecialEffectEvent(blade,state,se,event);change.setShrinkCount(cost);NeoForge.EVENT_BUS.post(change);
            if(change.isCanceled() || material.getCount()<change.getShrinkCount())return;
            state.addSpecialEffect(change.getSEKey());material.shrink(change.getShrinkCount());success(event);return;
        }
        if(tagged(material,"can_copy_se") && se==null){
            for(var effect:state.getSpecialEffects()){
                var definition=SpecialEffectsRegistry.VALUES.getValue(effect);if(definition==null)continue;
                var pre=new PreCopySpecialEffectFromBladeEvent(blade,state,effect,event,definition.isRemovable(),definition.isCopiable());pre.setShrinkCount(cost);NeoForge.EVENT_BUS.post(pre);
                if(pre.isCanceled() || !pre.isCopiable() || material.getCount()<pre.getShrinkCount())continue;
                var orb=new ItemStack(SBItems.proudsoul_crystal);SBItemData.put(orb,"SpecialEffectType",StringTag.valueOf(effect.toString()));
                material.shrink(pre.getShrinkCount());if(pre.isRemovable())state.removeSpecialEffect(effect);
                var dropped=player.drop(orb,true);NeoForge.EVENT_BUS.post(new CopySpecialEffectFromBladeEvent(pre,orb,dropped));event.setCanceled(true);success(event);return;
            }
        }
        if(tagged(material,"can_copy_sa") && material.isEnchanted() && state.getSlashArts()!=mods.flammpfeil.slashblade.specialattack.SlashArts.NONE){
            var current=SBEnchantments.map(blade);
            boolean maximum=SBEnchantments.map(material).keySet().stream().anyMatch(e->current.getOrDefault(e,0)>=e.value().getMaxLevel());
            if(maximum){
                var key=SlashArtsRegistry.VALUES.getKey(state.getSlashArts());
                if(key!=null){
                    var pre=new PreCopySpecialAttackFromBladeEvent(blade,state,key,event);pre.setShrinkCount(cost);NeoForge.EVENT_BUS.post(pre);
                    if(!pre.isCanceled() && material.getCount()>=pre.getShrinkCount()){
                        var orb=new ItemStack(SBItems.proudsoul_sphere);SBItemData.put(orb,"SpecialAttackType",StringTag.valueOf(key.toString()));material.shrink(pre.getShrinkCount());
                        var dropped=player.drop(orb,true);NeoForge.EVENT_BUS.post(new CopySpecialAttackFromBladeEvent(pre,orb,dropped));event.setCanceled(true);success(event);return;
                    }
                }
            }
        }
        if(tagged(material,"proudsouls") && material.isEnchanted()){
            var current=SBEnchantments.map(blade);int consumed=cost;boolean changed=false,attempted=false;
            for(var enchant:SBEnchantments.map(material).keySet()){
                if(!blade.supportsEnchantment(enchant) || current.keySet().stream().anyMatch(e->!e.equals(enchant) && !Enchantment.areCompatible(e,enchant)))continue;
                float chance=material.is(SBItems.proudsoul_tiny)?.25f:material.is(SBItems.proudsoul)?.5f:material.is(SBItems.proudsoul_ingot)?.75f:1;
                int level=Math.min(enchant.value().getMaxLevel(),current.getOrDefault(enchant,0)+1);
                var attempt=new ProudSoulEnchantmentEvent(blade,state,enchant,level,false,chance,consumed,event);NeoForge.EVENT_BUS.post(attempt);
                if(attempt.isCanceled())continue;
                attempted=true;
                consumed=Math.max(0,attempt.getTotalShrinkCount());
                if(material.getCount()<consumed)return;
                if(player.getRandom().nextFloat()<=attempt.getProbability()){current.put(attempt.getEnchantment(),attempt.getEnchantLevel());changed=true;}
                if(!attempt.willTryNextEnchant())break;
            }
            if(!attempted)return;
            material.shrink(consumed);SBEnchantments.set(current,blade);event.setCanceled(true);if(changed)success(event);
        }
    }
    private static void success(SlashBladeEvent.BladeStandAttackEvent event){
        var stand=event.getBladeStand();stand.setItem(event.getBlade());
        if(stand.level() instanceof ServerLevel level){
            level.playSound(null,stand.getX(),stand.getY(),stand.getZ(),net.minecraft.sounds.SoundEvents.WITHER_SPAWN,net.minecraft.sounds.SoundSource.BLOCKS,.5f,.8f);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,stand.getX(),stand.getY()+.5,stand.getZ(),32,.3,.3,.3,.2);
        }
    }
}
