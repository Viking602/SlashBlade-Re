package mods.flammpfeil.slashblade.slasharts;

import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.KnockBacks;
import mods.flammpfeil.slashblade.util.VectorHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

public class SakuraEnd {
    public static EntitySlashEffect doSlash(LivingEntity playerIn, float roll, Vec3 centerOffset, boolean mute,
                                            boolean critical, double damage) {
        return doSlash(playerIn, roll, centerOffset, mute, critical, damage, KnockBacks.cancel);
    }

    public static EntitySlashEffect doSlash(LivingEntity playerIn, float roll, Vec3 centerOffset, boolean mute,
                                            boolean critical, double damage, KnockBacks knockback) {

        int colorCode = SBData.get(playerIn.getMainHandItem(),ItemSlashBlade.BLADESTATE)
                .map(ISlashBladeState::getColorCode).orElse(0xFFFFFF);

        return doSlash(playerIn, roll, colorCode, centerOffset, mute, critical, damage, knockback);
    }

    public static EntitySlashEffect doSlash(LivingEntity playerIn, float roll, int colorCode, Vec3 centerOffset,
                                            boolean mute, boolean critical, double damage, KnockBacks knockback) {

        if (playerIn.level().isClientSide()) {
            return null;
        }

        ItemStack blade = playerIn.getMainHandItem();
        if (!SBData.get(blade,ItemSlashBlade.BLADESTATE).isPresent()) {
            return null;
        }
        SlashBladeEvent.DoSlashEvent event = new SlashBladeEvent.DoSlashEvent(blade,
                SBData.get(blade,ItemSlashBlade.BLADESTATE).orElseThrow(NullPointerException::new),
                playerIn, roll, critical, damage, knockback);

        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            return null;
        }

        Vec3 pos = playerIn.position().add(0.0D, (double) playerIn.getEyeHeight() * 0.75D, 0.0D)
                .add(playerIn.getLookAngle().scale(0.3f));

        pos = pos.add(VectorHelper.getVectorForRotation(-90.0F, playerIn.getViewYRot(0)).scale(centerOffset.y))
                .add(VectorHelper.getVectorForRotation(0, playerIn.getViewYRot(0) + 90).scale(centerOffset.z))
                .add(playerIn.getLookAngle().scale(centerOffset.z));

        EntitySlashEffect jc = new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect, playerIn.level());

        jc.setPos(pos.x, pos.y, pos.z);
        jc.setOwner(event.getUser());
        jc.setRotationRoll(event.getRoll());
        jc.setYRot(playerIn.getYRot());
        jc.setXRot(0);

        jc.setColor(colorCode);

        jc.setMute(mute);
        jc.setIsCritical(event.isCritical());

        jc.setDamage(event.getDamage());

        jc.setKnockBack(event.getKnockback());

        SBData.get(playerIn,CapabilityConcentrationRank.RANK_POINT)
                .ifPresent(rank -> jc.setRank(rank.getRankLevel(playerIn.level().getGameTime())));

        playerIn.level().addFreshEntity(jc);

        return jc;
    }
}
