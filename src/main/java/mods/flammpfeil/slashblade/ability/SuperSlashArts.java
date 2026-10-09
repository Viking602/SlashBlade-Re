package mods.flammpfeil.slashblade.ability;

import com.google.common.collect.MapMaker;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.compat.FreezeState;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.event.InputCommandEvent;
import mods.flammpfeil.slashblade.item.*;
import mods.flammpfeil.slashblade.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-authoritative Super SA, based on the author's documented thousand-kill art. */
public final class SuperSlashArts {
    public static final int CHARGE_TICKS=20;
    public static final int STRIKE_DELAY_TICKS=25;
    public static final int STUN_TICKS=40;
    public static final double HORIZONTAL_REACH=32, VERTICAL_REACH=16;
    private record Charge(UUID blade,long began) {}
    private record Cast(UUID blade,ServerLevel level,Vec3 origin,long fireAt) {}
    private final ConcurrentMap<ServerPlayer,Charge> charges=new MapMaker().weakKeys().makeMap();
    private final ConcurrentMap<ServerPlayer,Cast> casts=new MapMaker().weakKeys().makeMap();
    private static final SuperSlashArts INSTANCE=new SuperSlashArts();
    public static SuperSlashArts getInstance() { return INSTANCE; }
    private static final TargetingConditions TARGETS=TargetingConditions.forCombat().range(64)
            .ignoreLineOfSight().ignoreInvisibilityTesting().selector((entity,level)->new TargetSelector.AttackablePredicate().test(entity));

    public static boolean eligible(ItemStack stack) {
        return SBData.get(stack,ItemSlashBlade.BLADESTATE).filter(s -> s.getKillCount()>=1000
                && s.getDamage()==0 && !s.isBroken() && !s.isSealed()
                && SwordType.from(stack).contains(SwordType.Bewitched)).isPresent();
    }
    @SubscribeEvent public void onInputChange(InputCommandEvent event) {
        var player=event.getEntity(); long now=player.level().getGameTime();
        boolean old=event.getOld().contains(InputCommand.STYLE), current=event.getCurrent().contains(InputCommand.STYLE);
        if(!old && current) {
            var stack=player.getMainHandItem();
            if(!player.isSpectator() && eligible(stack) && !casts.containsKey(player))
                charges.put(player,new Charge(SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(()->new IllegalStateException("Missing blade state")).getUniqueId(),now));
        } else if(old && !current) {
            Charge charge=charges.remove(player);
            if(charge==null || now-charge.began()<CHARGE_TICKS || player.isSpectator())return;
            var stack=player.getMainHandItem();
            if(!eligible(stack))return;
            var state=SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(()->new IllegalStateException("Missing blade state"));
            if(!state.getUniqueId().equals(charge.blade()))return;
            // Pay once at release, even on empty space and with Unbreakable.
            // Reserving the cost also prevents a second release during the delay.
            state.setDamage(.5f);
            state.updateComboSeq(player,Extra.EX_SUPER_SA);
            var level=(ServerLevel)player.level(); var origin=player.position();
            casts.put(player,new Cast(charge.blade(),level,origin,now+STRIKE_DELAY_TICKS));
            for(var target:targets(level,player,origin)) {
                StunManager.setStun(target,STUN_TICKS);
                if(target instanceof net.minecraft.world.entity.player.Player)
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,STUN_TICKS,30,false,false));
                else freezeUntil(target,now+STUN_TICKS);
                target.setDeltaMovement(Vec3.ZERO);
            }
            level.sendParticles(ParticleTypes.PORTAL,origin.x,origin.y+.5,origin.z,60,2.5,.5,2.5,.04);
            level.playSound(null,origin.x,origin.y,origin.z,SoundEvents.ENDERMAN_TELEPORT,SoundSource.PLAYERS,.8f,.65f);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }
    /** Capture before the first frozen tick; independent of other mods' tick ordering. */
    public static void freezeUntil(LivingEntity entity, long until) {
        entity.setData(SBData.SUPER_FREEZE,FreezeState.capture(entity,until));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void freeze(EntityTickEvent.Pre event) {
        var entity=event.getEntity();
        if(!(entity instanceof LivingEntity) || entity instanceof net.minecraft.world.entity.player.Player)return;
        FreezeState frozen=entity.getExistingDataOrNull(SBData.SUPER_FREEZE);
        if(frozen==null)return;
        if(entity.level().getGameTime()>=frozen.until()) { entity.removeData(SBData.SUPER_FREEZE); return; }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.tickCount=frozen.age();
        event.setCanceled(true);
    }
    @SubscribeEvent public void onTick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        long now=player.level().getGameTime();
        Charge charge=charges.get(player);
        if(charge!=null) {
            var stack=player.getMainHandItem();
            boolean held=player.getData(SBData.INPUT).getCommands().contains(InputCommand.STYLE);
            if(!held || !eligible(stack) || !SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(()->new IllegalStateException("Missing blade state")).getUniqueId().equals(charge.blade()))
                charges.remove(player);
            else if((now-charge.began())%2==0)
                ((ServerLevel)player.level()).sendParticles(ParticleTypes.PORTAL,player.getX(),player.getY()+1,player.getZ(),3,.7,.8,.7,.02);
        }
        Cast cast=casts.get(player);
        if(cast==null)return;
        if(!player.isAlive() || player.isSpectator() || player.level()!=cast.level()
                || !SBData.get(player.getMainHandItem(),ItemSlashBlade.BLADESTATE).map(s->s.getUniqueId().equals(cast.blade())).orElse(false)) {
            casts.remove(player); return;
        }
        if(now<cast.fireAt())return;
        casts.remove(player);
        var stack=player.getMainHandItem(); var state=SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(()->new IllegalStateException("Missing blade state"));
        float rank=player.getData(SBData.RANK).getRankLevel(now);
        // One short-lived cut per target supplies five 2-tick damage pulses;
        // no global entity scan per frame or five independent drive entities.
        for(var target:targets(cast.level(),player,cast.origin())) {
            Vec3 position=target.getEyePosition().subtract(0,target.getEyeHeight()*.5,0);
            AttackManager.doMeleeAttack(player,target,true,true);
            var cut=new EntityJudgementCut(SlashBlade.RegistryEvents.JudgementCut,cast.level());
            cut.setOwner(player); cut.setPos(position); cut.setColor(state.getColorCode()); cut.setRank(rank);
            cut.setDamage(1); cut.setCycleHit(true); cut.setLifetime(10);
            cast.level().addFreshEntity(cut);
        }
    }
    private static java.util.List<LivingEntity> targets(ServerLevel level,ServerPlayer player,Vec3 origin) {
        var area=new AABB(origin.x-HORIZONTAL_REACH,origin.y-VERTICAL_REACH,origin.z-HORIZONTAL_REACH,
                origin.x+HORIZONTAL_REACH,origin.y+VERTICAL_REACH,origin.z+HORIZONTAL_REACH);
        return level.getEntitiesOfClass(LivingEntity.class,area,e -> e!=player && !player.hasPassenger(e) && e!=player.getVehicle() && TARGETS.test(level,player,e));
    }
    private SuperSlashArts() {}
}
