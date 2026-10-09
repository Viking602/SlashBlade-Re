package mods.flammpfeil.slashblade.entity;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.ability.StunManager;
import mods.flammpfeil.slashblade.capability.inputstate.InputStateCapabilityProvider;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.util.KnockBacks;
import mods.flammpfeil.slashblade.util.RayTraceHelper;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.stream.Stream;

public class EntityHeavyRainSwords extends EntityAbstractSummonedSword{
    private static final EntityDataAccessor<Boolean> IT_FIRED = SynchedEntityData.defineId(EntityHeavyRainSwords.class, EntityDataSerializers.BOOLEAN);

    public EntityHeavyRainSwords(EntityType<? extends Projectile> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);

        this.setPierce((byte)5);

        CompoundTag compoundtag = this.getPersistentData();
        ListTag listtag = compoundtag.getListOrEmpty("CustomPotionEffects");
        MobEffectInstance mobeffectinstance = new MobEffectInstance(MobEffects.SLOWNESS, 20, 10);
        listtag.add(mods.flammpfeil.slashblade.compat.SBEffects.saveEffect(mobeffectinstance, this.level().registryAccess()));
        this.getPersistentData().put("CustomPotionEffects", listtag);

    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);

        builder.define(IT_FIRED, false);
    }

    public void doFire(){
        this.getEntityData().set(IT_FIRED,true);
    }
    public boolean itFired(){
        return this.getEntityData().get(IT_FIRED);
    }

    public static EntityHeavyRainSwords createInstance(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet, Level worldIn){
        return new EntityHeavyRainSwords(SlashBlade.RegistryEvents.HeavyRainSwords, worldIn);
    }

    long fireTime = -1;

    @Override
    public void tick() {
        if(!itFired()){
            if(level().isClientSide()){
                if(getFormationHost() == null){
                    startFormation(this.getOwner());
                }
            }
        }

        super.tick();
    }

    @Override
    public void rideTick(){
        if(itFired() && fireTime <= tickCount){
            faceEntityStandby();

            this.stopFormation();

            Vec3 dir = new Vec3(0,-1,0);
            this.shoot(dir.x,dir.y,dir.z, 4.0f, 2.0f);

            this.tickCount = 0;

            return;
        }

        //this.startRiding()
        this.setDeltaMovement(Vec3.ZERO);
        if (!isRemoved())
            this.baseTick();

        faceEntityStandby();
        //this.getFormationHost().positionRider(this);

        //lifetime check
        if(!itFired()){
            int basedelay = 10;
            fireTime = tickCount + basedelay + getDelay();
            doFire();
        }

        /*
        if(!level().isClientSide())
            hitCheck();
        */
    }

    private void hitCheck(){
        Vec3 positionVec = this.position();
        Vec3 dirVec = this.getViewVector(1.0f);
        EntityHitResult raytraceresult = null;


        //todo : replace TargetSelector
        EntityHitResult entityraytraceresult = this.getRayTrace(positionVec, dirVec);
        if (entityraytraceresult != null) {
            raytraceresult = entityraytraceresult;
        }

        if (raytraceresult != null && raytraceresult.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult)raytraceresult).getEntity();
            Entity entity1 = this.getShooter();
            if (entity instanceof Player && entity1 instanceof Player && !((Player)entity1).canHarmPlayer((Player)entity)) {
                raytraceresult = null;
                entityraytraceresult = null;
            }
        }

        if (raytraceresult != null && raytraceresult.getType() == HitResult.Type.ENTITY && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, raytraceresult)) {
            this.onHit(raytraceresult);
            this.resetAlreadyHits();
            this.hurtMarked = true;
        }
    }

    private void faceEntityStandby() {
        setPos(this.position());

        setRot(this.getYRot(),-90);

    }

    public void setSpread(Vec3 basePos) {
        double areaSize = 2.5;

        double offsetX = (this.random.nextDouble()*2.0-1.0) * areaSize;
        double offsetZ = (this.random.nextDouble()*2.0-1.0) * areaSize;

        setPos(basePos.x + offsetX,basePos.y,basePos.z + offsetZ);
    }

    @Override
    protected void onHitEntity(EntityHitResult p_213868_1_) {

        Entity targetEntity = p_213868_1_.getEntity();
        if(targetEntity instanceof LivingEntity) {
            KnockBacks.cancel.action.accept((LivingEntity) targetEntity);
            StunManager.setStun((LivingEntity) targetEntity);
        }

        super.onHitEntity(p_213868_1_);
    }

    int ON_GROUND_LIFE_TIME = 20;
    int ticksInGround = 0;
    protected void tryDespawn() {
        ++this.ticksInGround;
        if (ON_GROUND_LIFE_TIME <= this.ticksInGround) {
            this.burst();
        }

    }
}
