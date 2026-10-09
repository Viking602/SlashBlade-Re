package mods.flammpfeil.slashblade.capability.slashblade;

import mods.flammpfeil.slashblade.compat.StateKey;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.client.renderer.CarryType;
import mods.flammpfeil.slashblade.util.EnumSetConverter;
import mods.flammpfeil.slashblade.util.NBTHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;

import mods.flammpfeil.slashblade.compat.LazyOptional;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Created by Furia on 2017/01/10.
 */
public class BladeStateCapabilityProvider {

    public static final StateKey<ISlashBladeState> CAP = StateKey.of(ISlashBladeState.class);

    public BladeStateCapabilityProvider(ISlashBladeState instance) { this.state = LazyOptional.of(() -> instance); }
    public ISlashBladeState getState() { return state.orElseThrow(() -> new IllegalStateException("Missing state")); }

    protected LazyOptional<ISlashBladeState> state = LazyOptional.of(SlashBladeState::new);


    public BladeStateCapabilityProvider(){
    }

    public Tag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        state.ifPresent(instance -> {
            //action state
            tag.putLong("lastActionTime" , instance.getLastActionTime());
            tag.putInt("TargetEntity", instance.getTargetEntityId());
            tag.putBoolean("_onClick", instance.onClick());
            tag.putFloat("fallDecreaseRate", instance.getFallDecreaseRate());
            tag.putBoolean("isCharged", instance.isCharged());
            tag.putFloat("AttackAmplifier", instance.getAttackAmplifier());
            tag.putString("currentCombo", instance.getComboSeq().getName());
            tag.putString("lastPosHash", instance.getLastPosHash());
            tag.putBoolean("HasShield", instance.hasShield());

            tag.putFloat("Damage", instance.getDamage());

            tag.putBoolean("isBroken", instance.isBroken());

            //passive state
            tag.putBoolean("isNoScabbard", instance.isNoScabbard());
            tag.putBoolean("isSealed", instance.isSealed());

            tag.putFloat("baseAttackModifier", instance.getBaseAttackModifier());

            tag.putInt("ProudSoul", instance.getProudSoulCount());
            tag.putInt("MaxDamage", instance.getMaxDamage());
            tag.putInt("killCount", instance.getKillCount());
            tag.putInt("RepairCounter", instance.getRefine());

            UUID id = instance.getOwner();
            if(id != null)
                tag.store("Owner", net.minecraft.core.UUIDUtil.CODEC, id);


            UUID bladeId = instance.getUniqueId();
            tag.store("BladeUniqueId", net.minecraft.core.UUIDUtil.CODEC, bladeId);


            //performance setting
            tag.putString("RangeAttackType", instance.getRangeAttackType().getName());

            tag.putString("SpecialAttackType", Optional.ofNullable(instance.getSlashArtsKey()).orElse("none"));
            tag.putBoolean("isDestructable", instance.isDestructable());
            tag.putBoolean("isDefaultBewitched", instance.isDefaultBewitched());
            tag.putByte("rarityType", (byte)instance.getRarity().ordinal());
            tag.putString("translationKey", instance.getTranslationKey());

            //render info
            tag.putByte("StandbyRenderType", (byte)instance.getCarryType().ordinal());
            tag.putInt("SummonedSwordColor", instance.getColorCode());
            tag.putBoolean("SummonedSwordColorInverse", instance.isEffectColorInverse());
            tag.put("adjustXYZ" , NBTHelper.newDoubleNBTList(instance.getAdjust()));

            instance.getTexture()
                    .ifPresent(loc ->  tag.putString("TextureName", loc.toString()));
            instance.getModel()
                    .ifPresent(loc ->  tag.putString("ModelName", loc.toString()));

            tag.putString("ComboRoot", Optional.ofNullable(instance.getComboRoot()).map((c)->c.getName()).orElseGet(()-> Extra.STANDBY_EX.getName()));
            tag.putString("ComboRootAir", Optional.ofNullable(instance.getComboRootAir()).map((c)->c.getName()).orElseGet(()-> Extra.STANDBY_INAIR.getName()));
        });

        return tag;
    }


    @Deprecated
    private final String tagState = "State";

    public void deserializeNBT(Tag inTag) {

        Tag baseTag;
        if(inTag instanceof CompoundTag && ((CompoundTag) inTag).contains(tagState)){
            //old
            baseTag = ((CompoundTag) inTag).get(tagState);
        }else{
            baseTag = inTag;
        }

        state.ifPresent(instance->{
            CompoundTag tag = (CompoundTag)baseTag;

            //action state
            instance.setLastActionTime(tag.getLongOr("lastActionTime", 0L));
            instance.setTargetEntityId(tag.getIntOr("TargetEntity", -1));
            instance.setOnClick(tag.getBooleanOr("_onClick", false));
            instance.setFallDecreaseRate(tag.getFloatOr("fallDecreaseRate", 0.0F));
            instance.setCharged(tag.getBooleanOr("isCharged", false));
            instance.setAttackAmplifier(tag.getFloatOr("AttackAmplifier", 0.0F));
            instance.setComboSeq(ComboState.NONE.valueOf(tag.getStringOr("currentCombo", "")));
            instance.setLastPosHash(tag.getStringOr("lastPosHash", ""));
            instance.setHasShield(tag.getBooleanOr("HasShield", false));

            instance.setDamage(tag.getFloatOr("Damage", 0.0F));

            instance.setBroken(tag.getBooleanOr("isBroken", false));

            instance.setHasChangedActiveState(true);


            //passive state
            instance.setNoScabbard(tag.getBooleanOr("isNoScabbard", false));
            instance.setSealed(tag.getBooleanOr("isSealed", false));

            instance.setBaseAttackModifier(tag.getFloatOr("baseAttackModifier", 0.0F));

            instance.setProudSoulCount(tag.getIntOr("ProudSoul", 0));
            instance.setMaxDamage(tag.getIntOr("MaxDamage", instance.getMaxDamage()));
            instance.setKillCount(tag.getIntOr("killCount", 0));
            instance.setRefine(tag.getIntOr("RepairCounter", 0));

            instance.setOwner(tag.read("Owner", net.minecraft.core.UUIDUtil.CODEC).isPresent() ? tag.read("Owner", net.minecraft.core.UUIDUtil.CODEC).orElse(new java.util.UUID(0L,0L)) : null);

            instance.setUniqueId(tag.read("BladeUniqueId", net.minecraft.core.UUIDUtil.CODEC).isPresent() ? tag.read("BladeUniqueId", net.minecraft.core.UUIDUtil.CODEC).orElse(new java.util.UUID(0L,0L)) : UUID.randomUUID());

            //performance setting
            instance.setRangeAttackType(RangeAttack.NONE.valueOf(tag.getStringOr("RangeAttackType", "")));

            instance.setSlashArtsKey(tag.getStringOr("SpecialAttackType", ""));
            instance.setDestructable(tag.getBooleanOr("isDestructable", false));
            instance.setDefaultBewitched(tag.getBooleanOr("isDefaultBewitched", false));

            instance.setRarity(EnumSetConverter.fromOrdinal(Rarity.values(), tag.getByteOr("rarityType", (byte)0), Rarity.COMMON));

            instance.setTranslationKey(tag.getStringOr("translationKey", ""));

            //render info
            instance.setCarryType(EnumSetConverter.fromOrdinal(CarryType.values(), tag.getByteOr("StandbyRenderType", (byte)0), CarryType.DEFAULT));
            instance.setColorCode(tag.getIntOr("SummonedSwordColor", 0));
            instance.setEffectColorInverse(tag.getBooleanOr("SummonedSwordColorInverse", false));
            instance.setAdjust(NBTHelper.getVector3d(tag, "adjustXYZ"));

            if(tag.contains("TextureName"))
                instance.setTexture(Identifier.parse(tag.getStringOr("TextureName", "")));
            else
                instance.setTexture(null);

            if(tag.contains("ModelName"))
                instance.setModel(Identifier.parse(tag.getStringOr("ModelName", "")));
            else
                instance.setModel(null);

            instance.setComboRootName(tag.getStringOr("ComboRoot", ""));
            instance.setComboRootAirName(tag.getStringOr("ComboRootAir", ""));
        });
    }
}
