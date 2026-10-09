package mods.flammpfeil.slashblade.ability;

import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.capability.mobeffect.CapabilityMobEffect;
import mods.flammpfeil.slashblade.entity.ai.StunGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * Created by Furia on 15/06/20.
 */
public class StunManager {

    static final int DEFAULT_STUN_TICKS = 10;


    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoinWorldEvent(EntityJoinLevelEvent event){
        if(!(event.getEntity() instanceof PathfinderMob)) return;
        PathfinderMob entity = (PathfinderMob) event.getEntity();

        entity.goalSelector.addGoal(-1,new StunGoal(entity));
    }

    @SubscribeEvent
    public void onEntityLivingUpdate(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event){
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if(!(target instanceof PathfinderMob)) return;
        if(target == null) return;
        if(target.level() == null) return;

        boolean onStun = SBData.get(target, CapabilityMobEffect.MOB_EFFECT)
                .filter((state)->state.isStun(target.level().getGameTime()))
                .isPresent();

        if(onStun){
            Vec3 motion = target.getDeltaMovement();
            if(5 < target.fallDistance)
                target.setDeltaMovement(motion.x, motion.y - 2.0f, motion.z);
            else if(motion.y < 0)
                target.setDeltaMovement(motion.x, motion.y * 0.25f, motion.z);
        }

    }

    public static void setStun(LivingEntity target, LivingEntity attacker){
        setStun(target);
    }
    public static void setStun(LivingEntity target){
        setStun(target, DEFAULT_STUN_TICKS);
    }
    public static void setStun(LivingEntity target, long duration){
        if(!(target instanceof PathfinderMob)) return;
        if(target.level() == null) return;

        SBData.get(target, CapabilityMobEffect.MOB_EFFECT).ifPresent((state)->{
            state.setManagedStun(target.level().getGameTime() , duration);
        });
    }

    public static void removeStun(LivingEntity target){
        if(target.level() == null) return;
        if(!(target instanceof LivingEntity)) return;


        SBData.get(target, CapabilityMobEffect.MOB_EFFECT).ifPresent((state)->{
            state.clearStunTimeOut();
        });
    }
}
