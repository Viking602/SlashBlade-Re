package mods.flammpfeil.slashblade.util;
import mods.flammpfeil.slashblade.SlashBladeConfig;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.capability.concentrationrank.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.entity.PartEntity;
/** Resharped's per-hit formula, using modern enchantment and damage hooks. */
public final class AttackHelper {
    public static float getRankBonus(LivingEntity attacker) {
        var rank=SBData.get(attacker,CapabilityConcentrationRank.RANK_POINT).map(r->r.getRank(attacker.level().getGameTime())).orElse(IConcentrationRank.ConcentrationRanks.NONE);
        double bonus=rank.level/2.0;
        if(rank.level>=IConcentrationRank.ConcentrationRanks.S.level && attacker instanceof Player player) {
            int refine=SBData.get(attacker.getMainHandItem(),ItemSlashBlade.BLADESTATE).map(s->s.getRefine()).orElse(0);
            bonus=Math.max(bonus,Math.min(player.experienceLevel,refine)*SlashBladeConfig.REFINE_DAMAGE_MULTIPLIER.get());
        }
        return (float)bonus;
    }
    public static double calculateTotalDamage(LivingEntity attacker,Entity target,float ratio,boolean critical) {
        var source=attacker instanceof Player p?attacker.damageSources().playerAttack(p):attacker.damageSources().mobAttack(attacker);
        float base=(float)attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if(attacker.level() instanceof ServerLevel level)base=EnchantmentHelper.modifyDamage(level,attacker.getMainHandItem(),target,source,base);
        double damage=(base+5*attacker.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO)+getRankBonus(attacker))*ratio*AttackManager.getSlashBladeDamageScale(attacker)*SlashBladeConfig.SLASHBLADE_DAMAGE_MULTIPLIER.get();
        return damage*(critical?1.5:1);
    }
    public static void attack(LivingEntity attacker,Entity target,float ratio) {
        if(!(attacker.level() instanceof ServerLevel level) || !TargetSelector.canAttack(attacker,target))return;
        if(attacker instanceof Player p && !CommonHooks.onPlayerAttackTarget(p,target))return;
        if(!target.isAttackable() || target.skipAttackInteraction(attacker))return;
        boolean critical=attacker.fallDistance>0 && !attacker.onGround() && !attacker.onClimbable() && !attacker.isInWater() && !attacker.hasEffect(MobEffects.BLINDNESS) && !attacker.isPassenger() && !attacker.isSprinting() && target instanceof LivingEntity;
        double amount=calculateTotalDamage(attacker,target,ratio,false);
        if(attacker instanceof Player player) {
            var event=CommonHooks.fireCriticalHit(player,target,critical,critical?1.5f:1);
            critical=event.isCriticalHit();
            if(critical)amount*=event.getDamageMultiplier();
        } else if(critical)amount*=1.5;
        if(amount<=0)return;
        var source=attacker instanceof Player p?attacker.damageSources().playerAttack(p):attacker.damageSources().mobAttack(attacker);
        float health=target instanceof LivingEntity living?living.getHealth():0;
        if(!target.hurtServer(level,source,(float)amount))return;
        float knockback=EnchantmentHelper.modifyKnockback(level,attacker.getMainHandItem(),target,source,(float)attacker.getAttributeValue(Attributes.ATTACK_KNOCKBACK));
        if(attacker.isSprinting())knockback++;
        if(knockback>0 && target instanceof LivingEntity living) {
            living.knockback(knockback*.5,Math.sin(Math.toRadians(attacker.getYRot())),-Math.cos(Math.toRadians(attacker.getYRot())));
            attacker.setDeltaMovement(attacker.getDeltaMovement().multiply(.6,1,.6));attacker.setSprinting(false);
        }
        attacker.setLastHurtMob(target);
        EnchantmentHelper.doPostAttackEffects(level,target,source);
        Entity parent=target instanceof PartEntity<?> part?part.getParent():target;
        if(parent instanceof LivingEntity living && !attacker.getMainHandItem().isEmpty()) attacker.getMainHandItem().hurtEnemy(living,attacker);
        level.playSound(null,attacker.getX(),attacker.getY(),attacker.getZ(),critical?SoundEvents.PLAYER_ATTACK_CRIT:SoundEvents.PLAYER_ATTACK_STRONG,attacker.getSoundSource(),1,1);
        if(attacker instanceof Player p) {
            if(critical)p.crit(target);
            if(target instanceof LivingEntity living)p.awardStat(Stats.DAMAGE_DEALT,Math.round(Math.max(0,health-living.getHealth())*10));
            p.causeFoodExhaustion(.1f);
        }
    }
}
