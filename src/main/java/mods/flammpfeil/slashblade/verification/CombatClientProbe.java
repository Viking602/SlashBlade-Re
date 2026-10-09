package mods.flammpfeil.slashblade.verification;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.*;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.client.SlashBladeKeys;
import mods.flammpfeil.slashblade.entity.*;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;

/** Optional client verification driver. Normal launches register no probe listeners. */
public final class CombatClientProbe {
    public static final boolean ENABLED = Boolean.getBoolean("slashblade.portVerification");
    private static String mode;
    private static int elapsed,holdTicks,monitorTicks;
    private static final Set<Integer> received=new HashSet<>();
    private static final Set<Integer> observed=new HashSet<>(), submitted=new HashSet<>(), critical=new HashSet<>(), frozen=new HashSet<>();
    private static final Map<Integer,String> geometry=new HashMap<>();
    private static final Map<Integer,FreezeTrace> freezeTraces=new HashMap<>();
    private static final class FreezeTrace {
        final int age; int samples; boolean mismatch;
        FreezeTrace(int age) { this.age=age; }
    }
    private static Object observedLevel;
    public static void register() {
        if(!ENABLED)return;
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(Commands.literal("sbverify")
                .then(Commands.literal("sa").then(Commands.argument("ticks",IntegerArgumentType.integer(1,100)).executes(c->start("sa",IntegerArgumentType.getInteger(c,"ticks")))))
                .then(Commands.literal("super").then(Commands.argument("ticks",IntegerArgumentType.integer(1,100)).executes(c->start("super",IntegerArgumentType.getInteger(c,"ticks")))))));
        NeoForge.EVENT_BUS.addListener(CombatClientProbe::tick);
    }
    private static int start(String art,int ticks) {
        var mc=Minecraft.getInstance(); if(mc.player==null || mc.gameMode==null || mode!=null)return 0;
        mc.setScreen(null); mode=art; elapsed=0; holdTicks=ticks; monitorTicks=80; received.clear();
        if(art.equals("sa")) {
            mc.options.keyUse.setDown(true); mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
        } else SlashBladeKeys.SUPER_SA.setDown(true);
        SlashBlade.LOGGER.info("Combat client probe began: {} held {} ticks",art,ticks); return 1;
    }
    private static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(observedLevel!=mc.level) {
            observed.clear(); submitted.clear(); critical.clear(); frozen.clear(); geometry.clear(); freezeTraces.clear(); observedLevel=mc.level;
        }
        if(mc.player==null || mc.level==null || mc.gameMode==null) { reset(mc); return; }
        for(var entity:mc.level.getEntitiesOfClass(Projectile.class,mc.player.getBoundingBox().inflate(64),CombatClientProbe::isAttack)) {
            if(observed.add(entity.getId()))
                SlashBlade.LOGGER.info("Combat client observer received: {} localOwner={}",describe(entity),entity.getOwner()==mc.player);
            if(mode!=null && entity.getOwner()==mc.player && received.add(entity.getId()))
                SlashBlade.LOGGER.info("Combat client probe received: {}",describe(entity));
            if(entity instanceof EntityJudgementCut cut && cut.getIsCritical() && critical.add(entity.getId()))
                SlashBlade.LOGGER.info("Combat client observer critical synchronized: {}",describe(entity));
        }
        for(var entity:mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,mc.player.getBoundingBox().inflate(64))) {
            var freeze=entity.getExistingDataOrNull(mods.flammpfeil.slashblade.compat.SBData.SUPER_FREEZE);
            if(freeze!=null && mc.level.getGameTime()<freeze.until()) {
                if(frozen.add(entity.getId()))
                    SlashBlade.LOGGER.info("Combat client observer freeze synchronized: {} entity {} until {} age {}",entity.getType().toShortString(),entity.getId(),freeze.until(),freeze.age());
                var trace=freezeTraces.computeIfAbsent(entity.getId(),id->new FreezeTrace(freeze.age())); trace.samples++;
                if(entity.tickCount!=trace.age && !trace.mismatch) {
                    trace.mismatch=true;
                    SlashBlade.LOGGER.error("Combat client observer freeze age drift: entity {} expected {} actual {}",entity.getId(),trace.age,entity.tickCount);
                }
            } else {
                var trace=freezeTraces.remove(entity.getId());
                if(trace!=null) SlashBlade.LOGGER.info("Combat client observer freeze ended: entity {} stable={} samples={} frozenAge={} resumedAge={}",entity.getId(),!trace.mismatch,trace.samples,trace.age,entity.tickCount);
            }
        }
        if(mode==null)return;
        if(elapsed++==holdTicks) {
            if(mode.equals("sa")) { mc.options.keyUse.setDown(false); mc.gameMode.releaseUsingItem(mc.player); }
            else SlashBladeKeys.SUPER_SA.setDown(false);
            SlashBlade.LOGGER.info("Combat client probe released: {} at tick {}",mode,elapsed-1);
        }
        if(elapsed>holdTicks && --monitorTicks<=0) {
            SlashBlade.LOGGER.info("Combat client probe finished: {} received {} unique attack entities",mode,received.size()); reset(mc);
        }
    }
    private static boolean isAttack(net.minecraft.world.entity.Entity entity) {
        return entity instanceof EntityJudgementCut || entity instanceof EntitySlashEffect || entity instanceof EntityDrive;
    }
    private static String describe(Projectile entity) {
        return entity.getType().toShortString()+" entity "+entity.getId()+" owner "+(entity.getOwner()==null?null:entity.getOwner().getUUID())
                +" critical="+(entity instanceof EntityJudgementCut cut ? cut.getIsCritical() : entity instanceof EntityDrive drive ? drive.getIsCritical() : ((EntitySlashEffect)entity).getIsCritical());
    }
    /** Test-only recording at the renderer's geometry extraction/submission boundaries. */
    public static int extracted(net.minecraft.world.entity.Entity entity) {
        if(!isAttack(entity))return -1;
        if(!submitted.contains(entity.getId()))geometry.putIfAbsent(entity.getId(),describe((Projectile)entity));
        return entity.getId();
    }
    public static void submitted(int id) {
        if(id>=0 && submitted.add(id)) {
            var description=geometry.remove(id);
            SlashBlade.LOGGER.info("Combat client renderer submitted: {}",description);
        }
    }
    private static void reset(Minecraft mc) { mc.options.keyUse.setDown(false); SlashBladeKeys.SUPER_SA.setDown(false); mode=null; }
    private CombatClientProbe() {}
}
