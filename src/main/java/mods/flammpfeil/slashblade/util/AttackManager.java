package mods.flammpfeil.slashblade.util;

import mods.flammpfeil.slashblade.compat.SBData;
import com.google.common.collect.Lists;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.ability.ArrowReflector;
import mods.flammpfeil.slashblade.ability.TNTExtinguisher;
import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRankCapabilityProvider;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.entity.IShootable;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

public class AttackManager {
    public static boolean isPowered(LivingEntity entity) {
        boolean powered=entity.hasEffect(net.minecraft.world.effect.MobEffects.STRENGTH) || entity.hasEffect(net.minecraft.world.effect.MobEffects.HUNGER);
        var state=SBData.get(entity.getMainHandItem(),ItemSlashBlade.BLADESTATE);
        if(!state.isPresent())return powered;
        var event=new mods.flammpfeil.slashblade.event.SlashBladeEvent.PowerBladeEvent(entity.getMainHandItem(),state.orElseThrow(IllegalStateException::new),entity,powered);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);return event.isPowered();
    }

    static public void areaAttack(LivingEntity playerIn, Consumer<LivingEntity> beforeHit){
        areaAttack(playerIn, beforeHit, 1.0f, true, true, false);
    }

    static public EntitySlashEffect doSlash(LivingEntity playerIn, float roll) {
        return doSlash(playerIn,roll, false);
    }
    static public EntitySlashEffect doSlash(LivingEntity playerIn, float roll, boolean mute) {
        return doSlash(playerIn,roll, mute, false);
    }
    static public EntitySlashEffect doSlash(LivingEntity playerIn, float roll, boolean mute, boolean critical) {
        return doSlash(playerIn,roll,  mute, critical, 1.0);
    }
    static public EntitySlashEffect doSlash(LivingEntity playerIn, float roll, boolean mute, boolean critical, double damage) {
        return doSlash(playerIn,roll, Vec3.ZERO, mute, critical, damage);
    }
    static public EntitySlashEffect doSlash(LivingEntity playerIn, float roll, Vec3 centerOffset, boolean mute, boolean critical, double damage) {
        return doSlash(playerIn,roll, centerOffset, mute, critical, damage, KnockBacks.cancel);
    }
    static public EntitySlashEffect doSlash(LivingEntity playerIn, float roll, Vec3 centerOffset, boolean mute, boolean critical, double damage, KnockBacks knockback) {

        int colorCode = SBData.get(playerIn.getMainHandItem(), ItemSlashBlade.BLADESTATE)
                .map(state->state.getColorCode())
                .orElseGet(()->0xFFFFFF);

        return doSlash(playerIn,roll,colorCode, centerOffset, mute, critical, damage, knockback);
    }
    static public EntitySlashEffect doSlash(LivingEntity playerIn, float roll, int colorCode, Vec3 centerOffset, boolean mute, boolean critical, double damage, KnockBacks knockback) {

        if(playerIn.level().isClientSide()) return null;
        var state=SBData.get(playerIn.getMainHandItem(),ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        var event=new mods.flammpfeil.slashblade.event.SlashBladeEvent.DoSlashEvent(playerIn.getMainHandItem(),state,playerIn,roll,critical,damage,knockback);
        if(net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled())return null;
        roll=event.getRoll();critical=event.isCritical();damage=event.getDamage();knockback=event.getKnockback();

        Vec3 pos = playerIn.position()
                .add(0.0D, (double)playerIn.getEyeHeight() * 0.75D, 0.0D)
                .add(playerIn.getLookAngle().scale(0.3f));

        pos = pos.add(VectorHelper.getVectorForRotation( -90.0F, playerIn.getViewYRot(0)).scale(centerOffset.y))
                .add(VectorHelper.getVectorForRotation( 0, playerIn.getViewYRot(0) + 90).scale(centerOffset.z))
                .add(playerIn.getLookAngle().scale(centerOffset.z));

        EntitySlashEffect jc = new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect, playerIn.level());
        jc.setPos(pos.x ,pos.y, pos.z);
        jc.setOwner(playerIn);

        jc.setRotationRoll(roll);
        jc.setYRot(playerIn.getYRot()+event.getYRot());
        jc.setXRot(0);

        jc.setColor(colorCode);

        jc.setMute(mute);
        jc.setIsCritical(critical);

        jc.setDamage(damage);

        jc.setKnockBack(knockback);

        if(playerIn != null)
            SBData.get(playerIn, ConcentrationRankCapabilityProvider.RANK_POINT)
                    .ifPresent(rank->jc.setRank(rank.getRankLevel(playerIn.level().getGameTime())));

        playerIn.level().addFreshEntity(jc);

        return jc;
    }

    static public List<Entity> areaAttack(LivingEntity playerIn, Consumer<LivingEntity> beforeHit, float ratio, boolean forceHit, boolean resetHit , boolean mute) {
        return areaAttack(playerIn, beforeHit, ratio, forceHit, resetHit, mute,null);
    }
    static public List<Entity> areaAttack(LivingEntity playerIn, Consumer<LivingEntity> beforeHit, float ratio, boolean forceHit, boolean resetHit , boolean mute, List<Entity> exclude) {
        List<Entity> founds = Lists.newArrayList();
        if (!playerIn.level().isClientSide()) {
            founds=TargetSelector.getTargettableEntitiesWithinAABB(playerIn.level(),playerIn);
            if(exclude!=null) founds.removeAll(exclude);
            founds.removeIf(e->!TargetSelector.canAttack(playerIn,e));
            for(var entity:founds){if(entity instanceof LivingEntity living)beforeHit.accept(living);doMeleeAttack(playerIn,entity,forceHit,resetHit,ratio);}
        }

        if(!mute)
            playerIn.level().playSound((Player)null, playerIn.getX(), playerIn.getY(), playerIn.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 0.4F / (playerIn.getRandom().nextFloat() * 0.4F + 0.8F));

        return founds;
    }

    static public <E extends Entity & IShootable> List<Entity> areaAttack(E owner, Consumer<LivingEntity> beforeHit, double reach, boolean forceHit, boolean resetHit) {
        return areaAttack(owner, beforeHit, reach, forceHit, resetHit, null);
    }
    static public <E extends Entity & IShootable> List<Entity> areaAttack(E owner, Consumer<LivingEntity> beforeHit, double reach, boolean forceHit, boolean resetHit, List<Entity> exclude) {
        return areaAttack(owner, beforeHit, reach, forceHit, resetHit, 1, exclude);
    }
    static public <E extends Entity & IShootable> List<Entity> areaAttack(E owner, Consumer<LivingEntity> beforeHit, double reach, boolean forceHit, boolean resetHit, float comboRatio, List<Entity> exclude) {
        List<Entity> founds = Lists.newArrayList();

        AABB bb = owner.getBoundingBox();
        //bb = bb.grow(3.0D, 3D, 3.0D);

        if (!owner.level().isClientSide()) {

            founds = TargetSelector.getTargettableEntitiesWithinAABB(owner.level(),
                    reach,
                    owner);

            if(exclude != null)
                founds.removeAll(exclude);

            for (Entity entity : founds) {

                if(entity instanceof LivingEntity)
                    beforeHit.accept((LivingEntity)entity);

                if(owner.getShooter() instanceof LivingEntity living && !TargetSelector.canAttack(living,entity))continue;
                float baseAmount = (float) owner.getDamage();
                if(owner.getShooter() instanceof LivingEntity living) {
                    if (!(owner instanceof EntitySlashEffect))
                        baseAmount += mods.flammpfeil.slashblade.compat.SBEnchantments.level(
                                net.minecraft.world.item.enchantment.Enchantments.POWER, living.getMainHandItem()) * .1;
                    baseAmount *= (float)living.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    baseAmount += AttackHelper.getRankBonus(living);
                    baseAmount *= comboRatio*getSlashBladeDamageScale(living)*mods.flammpfeil.slashblade.SlashBladeConfig.SLASHBLADE_DAMAGE_MULTIPLIER.get();
                }
                doAttackWith(owner.damageSources().indirectMagic(owner, owner.getShooter()), baseAmount,entity, forceHit, resetHit);
            }
        }

        return founds;
    }

    static public void doManagedAttack(Consumer<Entity> attack, Entity target, boolean forceHit, boolean resetHit){
        if(forceHit)
            target.invulnerableTime = 0;

        attack.accept(target);

        if(resetHit)
            target.invulnerableTime = 0;
    }

    static public void doAttackWith(DamageSource src, float amount , Entity target, boolean forceHit, boolean resetHit){




        if(target instanceof EntityAbstractSummonedSword)
            return;

        doManagedAttack((t)->{
            t.hurt(src, amount);
        },target, forceHit, resetHit);
    }

    public static float getSlashBladeDamageScale(LivingEntity entity) {
        var attribute=entity.getAttribute(mods.flammpfeil.slashblade.registry.ModAttributes.SLASHBLADE_DAMAGE);
        return attribute==null?1:(float)attribute.getValue();
    }
    public static void doMeleeAttack(LivingEntity attacker,Entity target,boolean force,boolean reset){doMeleeAttack(attacker,target,force,reset,1);}
    public static void doMeleeAttack(LivingEntity attacker,Entity target,boolean force,boolean reset,float ratio){
        if(!TargetSelector.canAttack(attacker,target))return;
        doManagedAttack(t->SBData.get(attacker.getMainHandItem(),ItemSlashBlade.BLADESTATE).ifPresent(state->{
            try{state.setOnClick(true);AttackHelper.attack(attacker,t,ratio);}finally{state.setOnClick(false);}
        }),target,force,reset);
        ArrowReflector.doReflect(target,attacker);TNTExtinguisher.doExtinguishing(target,attacker);
    }
    public static void playPiercingSoundAction(LivingEntity entity){
        if(!entity.level().isClientSide())entity.level().playSound(null,entity.getX(),entity.getY(),entity.getZ(),SoundEvents.TRIDENT_THROW.value(),SoundSource.PLAYERS,1,1);
    }
    public static void doVoidSlashAttack(LivingEntity living) {
        if (living.level().isClientSide()) {
            return;
        }

        Vec3 pos = living.position().add(0.0D, (double) living.getEyeHeight() * 0.75D, 0.0D)
                .add(living.getLookAngle().scale(0.3f));

        pos = pos.add(VectorHelper.getVectorForRotation(-90.0F, living.getViewYRot(0)).scale(Vec3.ZERO.y))
                .add(VectorHelper.getVectorForRotation(0, living.getViewYRot(0) + 90).scale(Vec3.ZERO.z))
                .add(living.getLookAngle().scale(Vec3.ZERO.z));

        EntitySlashEffect jc = newVoidSlashEffect(living, pos);

        jc.setDamage(0D);

        jc.setKnockBack(KnockBacks.cancel);

        SBData.get(living,ConcentrationRankCapabilityProvider.RANK_POINT)
                .ifPresent(rank -> jc.setRank(rank.getRankLevel(living.level().getGameTime())));

        jc.setLifetime(36);

        living.level().addFreshEntity(jc);
    }

    public static EntitySlashEffect newVoidSlashEffect(LivingEntity living, Vec3 pos) {
        EntitySlashEffect jc = new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect, living.level()) {

            @Override
            public double getDamage() {
                return 0;
            }

            @Override
            public SoundEvent getSlashSound() {
                return SoundEvents.BLAZE_HURT;
            }

            @Override
            protected void tryDespawn() {
                if (!this.level().isClientSide()) {
                    if (this.getLifetime() < this.tickCount) {
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F,
                                0.625F + 0.1f * this.random.nextFloat());
                        ((ServerLevel) this.level()).sendParticles(ParticleTypes.ENCHANTED_HIT, this.getX(),
                                this.getY(), this.getZ(), 16, 0.7, 0.7, 0.7, 0.02);
                        this.getAlreadyHits().forEach(entity -> {
                            if (entity.isAlive()) {
                                float yRot = this.getOwner() != null ? this.getOwner().getYRot() : 0;
                                entity.addDeltaMovement(new Vec3(
                                        -Math.sin(yRot * (float) Math.PI / 180.0F) * 0.5,
                                        0.05D,
                                        Math.cos(yRot * (float) Math.PI / 180.0F) * 0.5));
                                double baseAmount = living.getAttributeValue(Attributes.ATTACK_DAMAGE);
                                int powerLevel = mods.flammpfeil.slashblade.compat.SBEnchantments.level(net.minecraft.world.item.enchantment.Enchantments.POWER,living.getMainHandItem());
                                baseAmount *= 1 + powerLevel * 0.1;
                                baseAmount += AttackHelper.getRankBonus(living);
                                if (this.getShooter() instanceof LivingEntity shooter) {
                                    baseAmount *= getSlashBladeDamageScale(shooter) * mods.flammpfeil.slashblade.SlashBladeConfig.SLASHBLADE_DAMAGE_MULTIPLIER.get();
                                }
                                doAttackWith(this.damageSources().indirectMagic(this, this.getShooter()),
                                        ((float) (baseAmount) * 5.1f), entity, true, true);
                            }
                        });
                        this.remove(RemovalReason.DISCARDED);
                    }
                }
            }
        };

        jc.setPos(pos.x, pos.y, pos.z);
        jc.setOwner(living);

        jc.setRotationRoll(180);
        jc.setYRot(living.getYRot() - 22.5F);
        jc.setXRot(0);

        int colorCode = SBData.get(living.getMainHandItem(),ItemSlashBlade.BLADESTATE)
                .map(s->s.getColorCode()).orElse(0xFFFFFF);
        jc.setColor(colorCode);

        jc.setMute(false);
        jc.setIsCritical(false);

        return jc;
    }

}
