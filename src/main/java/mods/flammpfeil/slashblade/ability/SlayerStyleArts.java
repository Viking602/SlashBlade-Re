package mods.flammpfeil.slashblade.ability;

import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.mobeffect.CapabilityMobEffect;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.event.InputCommandEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.network.NetworkManager;
import mods.flammpfeil.slashblade.util.AdvancementHelper;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.util.NBTHelper;
import mods.flammpfeil.slashblade.util.VectorHelper;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.neoforged.neoforge.common.NeoForge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class SlayerStyleArts {
    private static final class SingletonHolder {
        private static final SlayerStyleArts instance = new SlayerStyleArts();
    }

    public static SlayerStyleArts getInstance() {
        return SlayerStyleArts.SingletonHolder.instance;
    }

    private SlayerStyleArts() {
    }

    public void register() {
        NeoForge.EVENT_BUS.register(this);
    }

    final static EnumSet<InputCommand> fowerd_sprint_sneak = EnumSet.of(InputCommand.FORWARD, InputCommand.SPRINT, InputCommand.SNEAK);
    final static EnumSet<InputCommand> back_sprint_sneak = EnumSet.of(InputCommand.BACK, InputCommand.SPRINT, InputCommand.SNEAK);
    final static EnumSet<InputCommand> move = EnumSet.of(InputCommand.FORWARD, InputCommand.BACK, InputCommand.LEFT, InputCommand.RIGHT);


    static public final Identifier ADVANCEMENT_AIR_TRICK = Identifier.fromNamespaceAndPath(SlashBlade.modid, "abilities/air_trick");
    static public final Identifier ADVANCEMENT_TRICK_DOWN = Identifier.fromNamespaceAndPath(SlashBlade.modid, "abilities/trick_down");
    static public final Identifier ADVANCEMENT_TRICK_DODGE = Identifier.fromNamespaceAndPath(SlashBlade.modid, "abilities/trick_dodge");
    static public final Identifier ADVANCEMENT_TRICK_UP = Identifier.fromNamespaceAndPath(SlashBlade.modid, "abilities/trick_up");

    final static int TRICKACTION_UNTOUCHABLE_TIME = 10;

    @SubscribeEvent
    public void onInputChange(InputCommandEvent event) {

        EnumSet<InputCommand> old = event.getOld();
        EnumSet<InputCommand> current = event.getCurrent();
        ServerPlayer sender = event.getEntity();
        Level worldIn = sender.level();

        if(!old.contains(InputCommand.SPRINT)){
            if(current.contains(InputCommand.SPRINT) && NeoForge.EVENT_BUS.post(new mods.flammpfeil.slashblade.event.ability.SprintMoveEvent(sender,current)).isCanceled())return;

            boolean isHandled = false;

            if(current.containsAll(fowerd_sprint_sneak)){
                //air trick Or trick up
                isHandled = SBData.get(sender.getMainHandItem(), ItemSlashBlade.BLADESTATE).map(state->{
                    Entity tmpTarget = state.getTargetEntity(worldIn);

                    Entity target;

                    if(tmpTarget != null && tmpTarget.getParts() != null && 0 < tmpTarget.getParts().length){
                        target = tmpTarget.getParts()[0];
                    }else{
                        target = tmpTarget;
                    }

                    if(target == null && 0 == sender.getPersistentData().getIntOr("sb.avoid.trickup", 0)) {
                        //trick up
                        Untouchable.setUntouchable(sender, TRICKACTION_UNTOUCHABLE_TIME);

                        Vec3 motion = new Vec3(0, +0.8, 0);

                        sender.move(MoverType.SELF, motion);
                        sender.isChangingDimension = true;

                        sender.connection.send(new ClientboundSetEntityMotionPacket(sender.getId(), motion.scale(0.75f)));

                        sender.getPersistentData().putInt("sb.avoid.trickup",2);
                        sender.setOnGround(false);

                        sender.getPersistentData().putInt("sb.avoid.counter",2);
                        NBTHelper.putVector3d(sender.getPersistentData(),"sb.avoid.vec", sender.position());

                        AdvancementHelper.grantCriterion(sender,ADVANCEMENT_TRICK_UP);
                        mods.flammpfeil.slashblade.compat.SBEffects.notifySound(sender, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.2f);

                        return true;
                    }else if(target != null){
                        //air trick
                        if(target == sender.getLastHurtMob() && sender.tickCount < sender.getLastHurtMobTimestamp() + 100){
                            LivingEntity hitEntity = sender.getLastHurtMob();
                            if(hitEntity != null){
                                SlayerStyleArts.doTeleport(sender, hitEntity);
                            }
                        }else{
                            EntityAbstractSummonedSword ss = new EntityAbstractSummonedSword(SlashBlade.RegistryEvents.SummonedSword, worldIn){
                                @Override
                                protected void onHitEntity(EntityHitResult p_213868_1_) {
                                    super.onHitEntity(p_213868_1_);

                                    LivingEntity target = sender.getLastHurtMob();
                                    if(target != null && this.getHitEntity() == target){
                                        SlayerStyleArts.doTeleport(sender, target);
                                    }
                                }

                                @Override
                                public void tick() {
                                    if(this.getPersistentData().getBooleanOr("doForceHit", false)) {
                                        this.doForceHitEntity(target);
                                        this.getPersistentData().remove("doForceHit");
                                    }
                                    super.tick();
                                }
                            };

                            Vec3 lastPos = sender.getEyePosition(1.0f);
                            ss.xOld = lastPos.x;
                            ss.yOld = lastPos.y;
                            ss.zOld = lastPos.z;

                            Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2.0, 0).add(sender.getLookAngle().scale(-2.0));
                            ss.setPos(targetPos.x, targetPos.y, targetPos.z);

                            Vec3 dir = sender.getLookAngle();
                            ss.shoot(dir.x, dir.y, dir.z, 1.0f, 0);

                            ss.setOwner(sender);

                            ss.setDamage(0.01f);

                            ss.setColor(state.getColorCode());

                            ss.getPersistentData().putBoolean("doForceHit",true);

                            worldIn.addFreshEntity(ss);
                            mods.flammpfeil.slashblade.compat.SBEffects.notifySound(sender, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.2F, 1.45F);

                            //ss.doForceHitEntity(target);
                        }
                        return true;
                    }

                    return false;

                }).orElse(false);
            }

            //trick down
            if(!isHandled && !sender.onGround() && current.containsAll(back_sprint_sneak)){
                Vec3 oldpos = sender.position();

                Vec3 motion = new Vec3(0, -5, 0);

                sender.move(MoverType.SELF, motion);
                if(sender.onGround()){
                    Untouchable.setUntouchable(sender, TRICKACTION_UNTOUCHABLE_TIME);

                    sender.isChangingDimension = true;

                    sender.connection.send(new ClientboundSetEntityMotionPacket(sender.getId(), motion.scale(0.75f)));

                    sender.getPersistentData().putInt("sb.avoid.counter",2);
                    NBTHelper.putVector3d(sender.getPersistentData(),"sb.avoid.vec", sender.position());

                    AdvancementHelper.grantCriterion(sender,ADVANCEMENT_TRICK_DOWN);
                    mods.flammpfeil.slashblade.compat.SBEffects.notifySound(sender, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.2f);

                    isHandled = true;
                }else{
                    sender.setPos(oldpos);
                }

            }


            if(!isHandled && sender.onGround() && current.contains(InputCommand.SPRINT) && current.stream().anyMatch(cc->move.contains(cc))){
                //quick avoid ground

                int count = SBData.get(sender, CapabilityMobEffect.MOB_EFFECT)
                        .map(ef->ef.doAvoid(sender.level().getGameTime()))
                        .orElse(0);

                if(0 < count){
                    Untouchable.setUntouchable(sender, TRICKACTION_UNTOUCHABLE_TIME);

                    float moveForward = current.contains(InputCommand.FORWARD) == current.contains(InputCommand.BACK) ? 0.0F : (current.contains(InputCommand.FORWARD) ? 1.0F : -1.0F);
                    float moveStrafe = current.contains(InputCommand.LEFT) == current.contains(InputCommand.RIGHT) ? 0.0F : (current.contains(InputCommand.LEFT) ? 1.0F : -1.0F);
                    Vec3 input = new Vec3(moveStrafe,0,moveForward);

                    sender.moveRelative(3.0f, input);

                    Vec3 motion = this.maybeBackOffFromEdge(sender.getDeltaMovement(), sender);

                    mods.flammpfeil.slashblade.compat.SBEffects.notifySound(sender, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.2f);

                    sender.move(MoverType.SELF, motion);
                    sender.isChangingDimension = true;

                    //sender.snapTo(sender.position());

                    sender.connection.send(new ClientboundSetEntityMotionPacket(sender.getId(), motion.scale(0.5f)));

                    sender.getPersistentData().putInt("sb.avoid.counter",2);
                    NBTHelper.putVector3d(sender.getPersistentData(),"sb.avoid.vec", sender.position());

                    AdvancementHelper.grantCriterion(sender,ADVANCEMENT_TRICK_DODGE);

                    SBData.get(sender.getMainHandItem(), ItemSlashBlade.BLADESTATE)
                            .ifPresent(state->state.updateComboSeq(sender, state.getComboRootAir()));
                }

                isHandled = true;
            }
            //slow avoid ground
            //move double tap

            /**
             //relativeList : pos -> convertflag -> motion
             sender.connection.setPlayerLocation(sender.getPosX(), sender.getPosY(), sender.getPosZ()
             , sender.getYaw(1.0f), sender.getPitch(1.0f)
             , Sets.newHashSet(SPlayerPositionLookPacket.Flags.X,SPlayerPositionLookPacket.Flags.Z));
             */
        }

    }
    private static void doTeleport(Entity entityIn, LivingEntity target) {
        entityIn.getPersistentData().putInt("sb.airtrick.counter",3);
        entityIn.getPersistentData().putInt("sb.airtrick.target", target.getId());

        if(entityIn instanceof ServerPlayer){
            AdvancementHelper.grantCriterion((ServerPlayer) entityIn,ADVANCEMENT_AIR_TRICK);
            Vec3 motion = target.getPosition(1.0f).subtract(entityIn.getPosition(1.0f)).scale(0.5f);
            ((ServerPlayer) entityIn).connection.send(new ClientboundSetEntityMotionPacket(entityIn.getId(), motion));
        }
    }

    private static void executeTeleport(Entity entity, LivingEntity target) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        Vec3 destination = target.position().add(0, target.getBbHeight() / 2.0, 0).add(entity.getLookAngle().scale(-2));
        if (!Level.isInSpawnableBounds(BlockPos.containing(destination))) return;
        entity.stopRiding();
        if (entity instanceof ServerPlayer player && player.isSleeping()) player.stopSleepInBed(true, true);
        entity.teleportTo(level, destination.x, destination.y, destination.z, java.util.Set.of(), entity.getYRot(), entity.getXRot(), true);
        entity.setYHeadRot(entity.getYRot());
        if (!(entity instanceof LivingEntity living) || !living.isFallFlying()) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(1,0,1));
            entity.setOnGround(false);
        }
        if (entity instanceof PathfinderMob mob) mob.getNavigation().stop();
    }

    protected Vec3 maybeBackOffFromEdge(Vec3 vec, LivingEntity mover) {
        double d0 = vec.x;
        double d1 = vec.z;
        double d2 = 0.05D;

        while(d0 != 0.0D && mover.level().noCollision(mover, mover.getBoundingBox().move(d0, (double)(-mover.maxUpStep()), 0.0D))) {
            if (d0 < 0.05D && d0 >= -0.05D) {
                d0 = 0.0D;
            } else if (d0 > 0.0D) {
                d0 -= 0.05D;
            } else {
                d0 += 0.05D;
            }
        }

        while(d1 != 0.0D && mover.level().noCollision(mover, mover.getBoundingBox().move(0.0D, (double)(-mover.maxUpStep()), d1))) {
            if (d1 < 0.05D && d1 >= -0.05D) {
                d1 = 0.0D;
            } else if (d1 > 0.0D) {
                d1 -= 0.05D;
            } else {
                d1 += 0.05D;
            }
        }

        while(d0 != 0.0D && d1 != 0.0D && mover.level().noCollision(mover, mover.getBoundingBox().move(d0, (double)(-mover.maxUpStep()), d1))) {
            if (d0 < 0.05D && d0 >= -0.05D) {
                d0 = 0.0D;
            } else if (d0 > 0.0D) {
                d0 -= 0.05D;
            } else {
                d0 += 0.05D;
            }

            if (d1 < 0.05D && d1 >= -0.05D) {
                d1 = 0.0D;
            } else if (d1 > 0.0D) {
                d1 -= 0.05D;
            } else {
                d1 += 0.05D;
            }
        }

        vec = new Vec3(d0, vec.y, d1);

        return vec;
    }

    static final float stepUpBoost = 1.1f;
    static final float stepUpDefault = 0.6f;

    @SubscribeEvent
    public void onTickPre(net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre event) {
                float stepUp = event.getEntity().maxUpStep();

                LivingEntity player = event.getEntity();
                Vec3 deltaMovement;
                {
                    Vec3 input = new Vec3((double)player.xxa, (double)player.yya, (double)player.zza);
                    double scale = 1.0;
                    float yRot = player.getYRot();
                    double d0 = input.lengthSqr();
                    if (d0 < 1.0E-7D) {
                        deltaMovement = Vec3.ZERO;
                    } else {
                        Vec3 vec3 = (d0 > 1.0D ? input.normalize() : input).scale((double)scale);
                        float f = Mth.sin(yRot * ((float)Math.PI / 180F));
                        float f1 = Mth.cos(yRot * ((float)Math.PI / 180F));
                        deltaMovement = new Vec3(vec3.x * (double)f1 - vec3.z * (double)f, vec3.y, vec3.z * (double)f1 + vec3.x * (double)f);
                    }
                }

                boolean doStepupBoost = true;

                if(doStepupBoost){
                    Vec3 offset = deltaMovement.normalize().scale(0.5f).add(0,0.25,0);
                    BlockPos offsetedPos = new BlockPos(VectorHelper.f2i(player.position().add(offset))).below();
                    BlockState blockState = player.level().getBlockState(offsetedPos);
                    if(blockState.liquid()){
                        doStepupBoost = false;
                    }
                }

                if(doStepupBoost && (event.getEntity().getMainHandItem().getItem() instanceof ItemSlashBlade) && stepUp < stepUpBoost){
                    event.getEntity().getPersistentData().putFloat("sb.store.stepup",stepUp);
                    event.getEntity().getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(stepUpBoost);
                }

                //trick up cooldown
                if(event.getEntity().onGround() && 0 < event.getEntity().getPersistentData().getIntOr("sb.avoid.trickup", 0)){

                    int count = event.getEntity().getPersistentData().getIntOr("sb.avoid.trickup", 0);
                    count--;

                    if(count <= 0){
                        event.getEntity().getPersistentData().remove("sb.avoid.trickup");

                        if(event.getEntity() instanceof ServerPlayer){
                            ((ServerPlayer)event.getEntity()).hasChangedDimension();
                        }
                    }else{
                        event.getEntity().getPersistentData().putInt("sb.avoid.trickup", count);
                    }
                }


                //handle avoid
                if(event.getEntity().getPersistentData().contains("sb.avoid.counter")){
                    int count = event.getEntity().getPersistentData().getIntOr("sb.avoid.counter", 0);
                    count--;

                    if(count <= 0){
                        if(event.getEntity().getPersistentData().contains("sb.avoid.vec")){
                            Vec3 pos = NBTHelper.getVector3d(event.getEntity().getPersistentData(),"sb.avoid.vec");
                            event.getEntity().snapTo(pos);
                        }

                        event.getEntity().getPersistentData().remove("sb.avoid.counter");
                        event.getEntity().getPersistentData().remove("sb.avoid.vec");

                        if(event.getEntity() instanceof ServerPlayer){
                            ((ServerPlayer)event.getEntity()).hasChangedDimension();
                        }
                    }else{
                        event.getEntity().getPersistentData().putInt("sb.avoid.counter", count);
                    }
                }


                //handle AirTrick
                if(event.getEntity().getPersistentData().contains("sb.airtrick.counter")){
                    int count = event.getEntity().getPersistentData().getIntOr("sb.airtrick.counter", 0);
                    count--;

                    if(count <= 0){
                        if(event.getEntity().getPersistentData().contains("sb.airtrick.target")){
                            int id = event.getEntity().getPersistentData().getIntOr("sb.airtrick.target", 0);

                            Entity target = event.getEntity().level().getEntity(id);
                            if(target != null && target instanceof LivingEntity)
                                executeTeleport(event.getEntity(), ((LivingEntity) target));
                        }

                        event.getEntity().getPersistentData().remove("sb.airtrick.counter");
                        event.getEntity().getPersistentData().remove("sb.airtrick.target");
                        if(event.getEntity() instanceof ServerPlayer){
                            ((ServerPlayer)event.getEntity()).hasChangedDimension();
                        }
                    }else{
                        event.getEntity().getPersistentData().putInt("sb.airtrick.counter", count);
                    }
                }

    }
    @SubscribeEvent
    public void onTickPost(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
                float stepUp = event.getEntity().getPersistentData().getFloatOr("sb.tmp.stepup", 0.0F);
                stepUp = Math.max(stepUp, stepUpDefault);

                if(stepUp < event.getEntity().maxUpStep())
                    event.getEntity().getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(stepUp);

    }
}
