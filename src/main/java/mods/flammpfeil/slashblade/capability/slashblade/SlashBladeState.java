/*
 * Minecraft Forge
 * Copyright (c) 2016-2019.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package mods.flammpfeil.slashblade.capability.slashblade;

import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.client.renderer.CarryType;
import mods.flammpfeil.slashblade.util.EnumSetConverter;
import mods.flammpfeil.slashblade.util.NBTHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Rarity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import mods.flammpfeil.slashblade.compat.LazyOptional;

import org.jetbrains.annotations.NotNull;
import java.awt.*;
import java.util.Optional;
import java.util.UUID;

/**
 * Reference implementation of {@link ISlashBladeState}. Use/extend this or implement your own.
 *
 * Derived from the Redstone Flux power system designed by King Lemming and originally utilized in Thermal Expansion and related mods.
 * Created with consent and permission of King Lemming and Team CoFH. Released with permission under LGPL 2.1 when bundled with Forge.
 */
public class SlashBladeState implements ISlashBladeState{

    private Runnable changeListener = () -> {};
    public void setChangeListener(Runnable listener) { changeListener = listener; }

    //action state
    protected long lastActionTime; //lastActionTime
    protected int targetEntityId = -1; //TargetEntity; a fresh blade has no lock-on target.
    protected boolean _onClick; //_onClick
    protected float fallDecreaseRate;
    protected boolean isCharged; //isCharged
    protected float attackAmplifier; //AttackAmplifier
    protected ComboState comboSeq; //comboSeq
    protected String lastPosHash; //lastPosHash
    protected boolean _hasShield;//HasShield
    protected boolean isBroken; //isBroken

    //protected int lumbmanager; //lumbmanager EntityID


    //passive state
    protected boolean isNoScabbard; //isNoScabbard
    protected boolean isSealed; //isSealed

    protected float baseAttackModifier; //BaseAttackModifier

    protected int killCount; //killCount
    protected int refine; //RepairCounter

    protected UUID owner; //Owner

    protected UUID uniqueId = UUID.randomUUID(); //Owner

    protected String translationKey = "";


    //performance setting
    protected RangeAttack rangeAttackType; //RangeAttackType
    protected String slashArtsKey; //SpecialAttackType
    protected boolean isDestructable; //isDestructable
    protected boolean isDefaultBewitched; //isDefaultBewitched
    protected Optional<Rarity> rarity = Optional.empty();
    ; //rarityType

    protected String comboRootName;
    protected String comboRootAirName;

    //render info
    protected Optional<CarryType> carryType = Optional.empty(); //StandbyRenderType
    protected Optional<Color> effectColor = Optional.empty(); //SummonedSwordColor
    protected boolean effectColorInverse;//SummonedSwordColorInverse
    protected Optional<Vec3> adjust = Optional.empty();//adjustXYZ

    protected Optional<Identifier> texture = Optional.empty(); //TextureName
    protected Optional<Identifier> model = Optional.empty();//ModelName

    private CompoundTag shareTag = null;

    public SlashBladeState() {
    }

    @Override
    public long getLastActionTime() {
        return lastActionTime;
    }

    @Override
    public void setLastActionTime(long lastActionTime) {
        this.lastActionTime = lastActionTime;

        setHasChangedActiveState(true);
        changeListener.run();
    }

    @Override
    public boolean onClick() {
        return _onClick;
    }

    @Override
    public void setOnClick(boolean onClick) {
        this._onClick = onClick;

        setHasChangedActiveState(true);
        changeListener.run();
    }

    @Override
    public float getFallDecreaseRate() {
        return fallDecreaseRate;
    }

    @Override
    public void setFallDecreaseRate(float fallDecreaseRate) {
        this.fallDecreaseRate = fallDecreaseRate;

        setHasChangedActiveState(true);
        changeListener.run();
    }

    @Override
    public boolean isCharged() {
        return isCharged;
    }

    @Override
    public void setCharged(boolean charged) {
        isCharged = charged;

        setHasChangedActiveState(true);
        changeListener.run();
    }

    @Override
    public float getAttackAmplifier() {
        return attackAmplifier;
    }

    @Override
    public void setAttackAmplifier(float attackAmplifier) {
        this.attackAmplifier = attackAmplifier;

        setHasChangedActiveState(true);
        changeListener.run();
    }

    @Override
    @NotNull
    public ComboState getComboSeq() {
        return ComboState.NONE.orNone(comboSeq);
    }

    @Override
    public void setComboSeq(ComboState comboSeq) {
        this.comboSeq = comboSeq;

        setHasChangedActiveState(true);
        changeListener.run();
    }

    @Override
    public String getLastPosHash() {
        return lastPosHash != null ? lastPosHash : "";
    }

    @Override
    public void setLastPosHash(String lastPosHash) {
        this.lastPosHash = lastPosHash;

        setHasChangedActiveState(true);
        changeListener.run();
    }

    @Override
    public boolean hasShield() {
        return _hasShield;
    }

    @Override
    public void setHasShield(boolean hasShield) {
        this._hasShield = hasShield;

        setHasChangedActiveState(true);
        changeListener.run();
    }

    @Override
    public boolean isBroken() {
        return isBroken;
    }

    @Override
    public void setBroken(boolean broken) {
        isBroken = broken;
        setHasChangedActiveState(true);
        changeListener.run();
    }

    @Override
    public boolean isNoScabbard() {
        return isNoScabbard;
    }

    @Override
    public void setNoScabbard(boolean noScabbard) {
        isNoScabbard = noScabbard;
        changeListener.run();
    }

    @Override
    public boolean isSealed() {
        return isSealed;
    }

    @Override
    public void setSealed(boolean sealed) {
        isSealed = sealed;
        changeListener.run();
    }

    @Override
    public float getBaseAttackModifier() {
        return baseAttackModifier;
    }

    @Override
    public void setBaseAttackModifier(float baseAttackModifier) {
        this.baseAttackModifier = baseAttackModifier;
        changeListener.run();
    }

    @Override
    public int getKillCount() {
        return killCount;
    }

    @Override
    public void setKillCount(int killCount) {
        this.killCount = killCount;

        setHasChangedActiveState(true);
        changeListener.run();
    }

    @Override
    public int getRefine() {
        return refine;
    }

    @Override
    public void setRefine(int refine) {
        this.refine = refine;
        changeListener.run();
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    @Override
    public void setOwner(UUID owner) {
        this.owner = owner;
        changeListener.run();
    }

    @Override
    @NotNull
    public RangeAttack getRangeAttackType() {
        return RangeAttack.NONE.orNone(rangeAttackType);
    }

    @Override
    public void setRangeAttackType(RangeAttack rangeAttackType) {
        this.rangeAttackType = rangeAttackType;
        changeListener.run();
    }

    @Override
    public String getSlashArtsKey() {
        return this.slashArtsKey;
    }

    @Override
    public void setSlashArtsKey(String key) {
        this.slashArtsKey = key;
        changeListener.run();
    }

    @Override
    public boolean isDestructable() {
        return isDestructable;
    }

    @Override
    public void setDestructable(boolean destructable) {
        isDestructable = destructable;
        changeListener.run();
    }

    @Override
    public boolean isDefaultBewitched() {
        return isDefaultBewitched;
    }

    @Override
    public void setDefaultBewitched(boolean defaultBewitched) {
        isDefaultBewitched = defaultBewitched;
        changeListener.run();
    }

    @Override
    @NotNull
    public Rarity getRarity() {
        return rarity.orElse(Rarity.COMMON);
    }

    @Override
    public void setRarity(Rarity rarity) {
        this.rarity = Optional.ofNullable(rarity);
        changeListener.run();
    }

    @Override
    public String getTranslationKey() {
        return translationKey;
    }

    @Override
    public void setTranslationKey(String translationKey) {
        this.translationKey = Optional.ofNullable(translationKey).orElse("");
        changeListener.run();
    }

    @Override
    @NotNull
    public CarryType getCarryType() {
        return carryType.orElse(CarryType.NONE);
    }

    @Override
    public void setCarryType(CarryType carryType) {
        this.carryType = Optional.ofNullable(carryType);
        changeListener.run();
    }

    @Override
    public Color getEffectColor() {
        return effectColor.orElseGet(() -> new Color(0x3333FF));
    }

    @Override
    public void setEffectColor(Color effectColor) {
        this.effectColor = Optional.ofNullable(effectColor);
        changeListener.run();
    }

    @Override
    public boolean isEffectColorInverse() {
        return effectColorInverse;
    }

    @Override
    public void setEffectColorInverse(boolean effectColorInverse) {
        this.effectColorInverse = effectColorInverse;
        changeListener.run();
    }

    @Override
    public Vec3 getAdjust() {
        return adjust.orElseGet(() -> Vec3.ZERO);
    }

    @Override
    public void setAdjust(Vec3 adjust) {
        this.adjust = Optional.ofNullable(adjust);
        changeListener.run();
    }

    @Override
    public Optional<Identifier> getTexture() {
        return texture;
    }

    @Override
    public void setTexture(Identifier texture) {
        this.texture = Optional.ofNullable(texture);
        changeListener.run();
    }

    @Override
    public Optional<Identifier> getModel() {
        return model;
    }

    @Override
    public void setModel(Identifier model) {
        this.model = Optional.ofNullable(model);
        changeListener.run();
    }

    @Override
    public int getTargetEntityId() {
        return targetEntityId;
    }

    @Override
    public void setTargetEntityId(int id) {
        targetEntityId = id;

        setHasChangedActiveState(true);
        changeListener.run();
    }

    LazyOptional<ComboState> rootCombo = instantiateRootComboHolder();
    @Override
    public String getComboRootName() {
        return this.comboRootName;
    }

    @Override
    public void setComboRootName(String comboRootName) {
        this.comboRootName = comboRootName;
        this.rootCombo = instantiateRootComboHolder();
        changeListener.run();
    }

    private LazyOptional<ComboState> instantiateRootComboHolder(){
        return LazyOptional.of(()->{
            if(ComboState.NONE.valueOf(getComboRootName()) == null){
                return Extra.STANDBY_EX;
            }else{
                return ComboState.NONE.valueOf(getComboRootName());
            }
        });
    }

    LazyOptional<ComboState> rootComboAir = instantiateRootComboAirHolder();
    @Override
    public String getComboRootAirName() {
        return this.comboRootAirName;
    }

    @Override
    public void setComboRootAirName(String comboRootName) {
        this.comboRootAirName = comboRootName;
        this.rootComboAir = instantiateRootComboAirHolder();
        changeListener.run();
    }

    private LazyOptional<ComboState> instantiateRootComboAirHolder(){
        return LazyOptional.of(()->{
            if(ComboState.NONE.valueOf(getComboRootAirName()) == null){
                return Extra.STANDBY_INAIR;
            }else{
                return ComboState.NONE.valueOf(getComboRootAirName());
            }
        });
    }


    @Override
    public CompoundTag getShareTag() {
        return this.shareTag;
    }

    @Override
    public void setShareTag(CompoundTag shareTag) {
        this.shareTag = shareTag;
    }

    private float damage = 0;
    @Override
    public float getDamage() {
        return this.damage;
    }

    @Override
    public void setDamage(float damage) {
        if(!this.isSealed() && damage <= 0.0f)
            this.setBroken(false);

        this.damage = Math.max(0.0f,Math.min(damage,1.0f));

        setHasChangedActiveState(true);
        changeListener.run();
    }

    boolean isChangedActiveState = false;
    @Override
    public boolean hasChangedActiveState() {
        return this.isChangedActiveState;
    }

    @Override
    public void setHasChangedActiveState(boolean isChanged) {
        this.isChangedActiveState = isChanged;
    }

    @Override
    public UUID getUniqueId() {
        return uniqueId;
    }

    @Override
    public void setUniqueId(UUID uniqueId) {
        this.uniqueId = uniqueId;
        changeListener.run();
    }
}
