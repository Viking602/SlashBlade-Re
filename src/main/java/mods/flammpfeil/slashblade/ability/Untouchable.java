package mods.flammpfeil.slashblade.ability;

import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.capability.mobeffect.CapabilityMobEffect;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;
import java.util.Optional;

public class Untouchable {
    private static final class SingletonHolder {
        private static final Untouchable instance = new Untouchable();
    }

    public static Untouchable getInstance() {
        return Untouchable.SingletonHolder.instance;
    }

    private Untouchable() {
    }

    public void register() {
        NeoForge.EVENT_BUS.register(this);
    }

    public static void setUntouchable(LivingEntity entity, int ticks){
        SBData.get(entity, CapabilityMobEffect.MOB_EFFECT).ifPresent(ef->{
            ef.setManagedUntouchable(entity.level().getGameTime(), ticks);
            ef.storeEffects(entity.getActiveEffectsMap().keySet());
            ef.storeHealth(entity.getHealth());
        });
    }

    private boolean checkUntouchable(LivingEntity entity){
        Optional<Boolean> isUntouchable = SBData.get(entity, CapabilityMobEffect.MOB_EFFECT)
                .map(ef->ef.isUntouchable(entity.level().getGameTime()));

        return isUntouchable.orElseGet(()->false);
    }

    private void doWitchTime(Entity entity){
        if(entity == null) return;

        if(!(entity instanceof LivingEntity)) return;

        StunManager.setStun((LivingEntity) entity);
    }

    @SubscribeEvent
    public void onLivingHurt(LivingIncomingDamageEvent event){
        if(checkUntouchable(event.getEntity())) {
            event.setCanceled(true);
            doWitchTime(event.getSource().getEntity());
        }
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Pre event){
        if(checkUntouchable(event.getEntity())) {
            event.setNewDamage(0);
            doWitchTime(event.getSource().getEntity());
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event){
        if(checkUntouchable(event.getEntity())) {
            event.setCanceled(true);
            doWitchTime(event.getSource().getEntity());

            if (!(event.getEntity() instanceof LivingEntity entity)) return;

            SBData.get(entity, CapabilityMobEffect.MOB_EFFECT).ifPresent(ef->{
                if(ef.hasUntouchableWorked()) {
                    List<Holder<MobEffect>> filterd = entity.getActiveEffectsMap().keySet().stream()
                            .filter(p -> !(ef.getEffectSet().contains(p) || p.value().isBeneficial()))
                            .toList();

                    filterd.forEach(p -> entity.removeEffect(p));

                    float storedHealth = ef.getStoredHealth();
                    if(entity.getHealth() < storedHealth)
                        entity.setHealth(ef.getStoredHealth());
                }
            });
        }
    }

    @SubscribeEvent
    public void onLivingTicks(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event){
        if (!(event.getEntity() instanceof LivingEntity entity)) return;

        if(entity.level().isClientSide()) return;

        SBData.get(entity, CapabilityMobEffect.MOB_EFFECT).ifPresent(ef->{
            if(ef.hasUntouchableWorked()) {
                ef.setUntouchableWorked(false);
                List<Holder<MobEffect>> filterd = entity.getActiveEffectsMap().keySet().stream()
                        .filter(p -> !(ef.getEffectSet().contains(p) || p.value().isBeneficial()))
                        .toList();

                filterd.forEach(p -> entity.removeEffect(p));

                float storedHealth = ef.getStoredHealth();
                if(entity.getHealth() < storedHealth)
                    entity.setHealth(ef.getStoredHealth());
            }
        });
    }


    final static int JUMP_TICKS = 10;

    @SubscribeEvent
    public void onPlayerJump(LivingEvent.LivingJumpEvent event){
        if(!SBData.get(event.getEntity().getMainHandItem(), ItemSlashBlade.BLADESTATE).isPresent())
            return;

        Untouchable.setUntouchable(event.getEntity(), JUMP_TICKS);
    }
}
