package mods.flammpfeil.slashblade.verification;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.*;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.client.animation.BladeMotionState;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.event.InputCommandEvent;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.client.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** A real integrated-server combat recording, separate from deterministic pose previews.
 * No keys, mouse events or client pose/clock overrides. Normal inventory ticks run all hits. */
public final class CombatShowcaseClientProbe {
    private static final boolean FIRST_PERSON=Boolean.getBoolean("slashblade.combatFirstPerson");
    private record Stage(String name,ComboState combo,int ticks,String action) {}
    private static final List<Stage> stages=new ArrayList<>();
    private static volatile boolean running,done;
    private static volatile String label="准备";
    private static volatile int shownTick;
    private static ServerPlayer actor;
    private static Mob target;
    private static Vec3 origin;
    private static int stage,stageTick,totalTick,frame;
    private static long began,lastCapture;
    private static ArmorStand camera;
    private static Path folder;
    private static final AtomicInteger queued=new AtomicInteger();
    private static ExecutorService writer;
    private static final List<String> captures=Collections.synchronizedList(new ArrayList<>());
    private static final List<String> cameraFrames=new ArrayList<>();
    private static final Map<String,float[]> cameraRanges=new LinkedHashMap<>();
    private static final List<Map<String,Object>> events=new ArrayList<>();
    private static final Map<String,Set<Integer>> cuts=new LinkedHashMap<>();
    private static float damage;
    private static int frozenSamples;
    public static void register() {
        if(!BladeVisualClientProbe.ENABLED) return;
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Pre e)-> {
            if(running && !done && e.getEntity()==actor) serverTick();
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e)-> {
            if(!running) return;
            var mc=Minecraft.getInstance(); if(mc.level==null || mc.player==null) return;
            mc.getToastManager().clear();
            mc.gui.getChat().clearMessages(false);
            if(FIRST_PERSON) {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.setCameraEntity(mc.player);
                // Look at the actual cow target's torso, rather than the horizon.
                mc.player.setXRot(20); mc.player.xRotO=20;
                return;
            }
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            if(camera==null) {
                camera=new ArmorStand(mc.level,mc.player.getX(),mc.player.getY(),mc.player.getZ());
                camera.getAttribute(Attributes.CAMERA_DISTANCE).setBaseValue(5.2);
            }
            // Frame the fighter and the target together from the left front quarter.
            camera.setPos(mc.player.getX(),mc.player.getY()+.3,mc.player.getZ()+1.5);
            camera.setYRot(-75); camera.setXRot(-8); camera.setOldPosAndRot();
            camera.yHeadRot=camera.yHeadRotO=camera.yBodyRot=camera.yBodyRotO=-75;
            mc.setCameraEntity(camera);
        });
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Pre e)-> {
            if(!running) return;
            var mc=Minecraft.getInstance(); var g=e.getGuiGraphics();
            g.text(mc.font,FIRST_PERSON ? "拔刀剑 · 第一视角挥砍演示" : "拔刀剑 · 完整战斗演示",12,12,0xFFFFFFFF);
            g.text(mc.font,label,12,27,0xFFFFDD88);
            g.text(mc.font,"服务端实际攻击 / SA 命中 · "+shownTick+" ticks",12,42,0xFFDDDDDD);
            e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post e)-> {
            if(!running) return;
            var mc=Minecraft.getInstance();
            if(done && queued.get()==0) { finish(); return; }
            if(FIRST_PERSON) {
                var view=mc.gameRenderer.getMainCamera();
                float yaw=net.minecraft.util.Mth.wrapDegrees(view.yRot()-mc.player.getYRot());
                float pitch=view.xRot()-mc.player.getXRot(),roll=view.getRoll();
                float[] range=cameraRanges.computeIfAbsent(label,k->new float[]{yaw,yaw,pitch,pitch,roll,roll,0});
                range[0]=Math.min(range[0],yaw);range[1]=Math.max(range[1],yaw);
                range[2]=Math.min(range[2],pitch);range[3]=Math.max(range[3],pitch);
                range[4]=Math.min(range[4],roll);range[5]=Math.max(range[5],roll);range[6]++;
                cameraFrames.add(shownTick+"\t"+yaw+"\t"+pitch+"\t"+roll+"\t"+label);
            }
            long now=System.nanoTime();
            if(done || now-lastCapture<33_333_333L || queued.get()>=4 || mc.screen!=null) return;
            lastCapture=now; int index=frame++; int tick=shownTick; String title=label;
            queued.incrementAndGet();
            Screenshot.takeScreenshot(mc.getMainRenderTarget(),image -> writer.execute(()-> {
                try(image) {
                    image.writeToFile(folder.resolve(String.format("%05d.png",index)));
                    captures.add(index+"\t"+(now-began)/1e9+"\t"+tick+"\t"+title);
                } catch(Exception error) { SlashBlade.LOGGER.error("Combat recording failed",error); }
                finally { queued.decrementAndGet(); }
            }));
        });
    }
    private static void add(String title,ComboState combo,int ticks) { stages.add(new Stage(title,combo,ticks,"")); }
    public static void start() {
        var mc=Minecraft.getInstance();
        folder=mc.gameDirectory.toPath().resolve(FIRST_PERSON ? "screenshots/full-combat-first" : "screenshots/full-combat");
        try { Files.createDirectories(folder); } catch(Exception e) { throw new IllegalStateException(e); }
        org.lwjgl.stb.STBImageWrite.stbi_write_png_compression_level.put(0,1);
        writer=Executors.newFixedThreadPool(2); BladeMotionState.clear();
        add("起势",null,30);
        add("A 连段 · 拔刀横斩 A1",Extra.EX_COMBO_A1,9);
        add("A 连段 · 袈裟斩 A2",Extra.EX_COMBO_A2,9);
        add("A 连段 · 双向连斩 A3",Extra.EX_COMBO_A3,11);
        add("A 连段 · 终结 A4 / 完整收刀",Extra.EX_COMBO_A4,88);
        add("强化 A · A1",Extra.EX_COMBO_A1,9); add("强化 A · A2",Extra.EX_COMBO_A2,9);
        add("强化 A · A3",Extra.EX_COMBO_A3,11); add("强化 A · A4 EX",Extra.EX_COMBO_A4EX,25);
        add("强化 A · A5 EX / 完整收刀",Extra.EX_COMBO_A5EX,125);
        add("B 连段 · A1",Extra.EX_COMBO_A1,9); add("B 连段 · A2",Extra.EX_COMBO_A2,9);
        add("B 连段 · A3 / 转入 B",Extra.EX_COMBO_A3,15);
        add("B 连段 · B1",Extra.EX_COMBO_B1,13); add("B 连段 · B2",Extra.EX_COMBO_B2,6);
        add("B 连段 · B3",Extra.EX_COMBO_B3,6); add("B 连段 · B4",Extra.EX_COMBO_B4,6);
        add("B 连段 · B5",Extra.EX_COMBO_B5,6); add("B 连段 · B6",Extra.EX_COMBO_B6,6);
        add("B 连段 · B7 / 完整收刀",Extra.EX_COMBO_B7,125);
        add("C 分支 · A1",Extra.EX_COMBO_A1,9); add("C 分支 · A2 / 延迟派生",Extra.EX_COMBO_A2,14);
        add("C 分支 · C / 完整收刀",Extra.EX_COMBO_C,80);
        stages.add(new Stage("SA · 蓄力 20 ticks",null,20,"charge"));
        stages.add(new Stage("SA · 次元斩释放 / 收刀",null,100,"sa"));
        stages.add(new Stage("Just SA · 蓄力 10 ticks",null,10,"charge"));
        stages.add(new Stage("Just SA · 精准释放 / 收刀",null,85,"just"));
        stages.add(new Stage("Super SA · 满耐久 / 千杀 / 蓄力",null,24,"super-charge"));
        stages.add(new Stage("Super SA · 定身 / 延迟斩击 / 收刀",null,110,"super"));
        add("演示结束",null,30);
        mc.getSingleplayerServer().execute(()-> {
            actor=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            origin=actor.position(); actor.setYRot(0); actor.setXRot(FIRST_PERSON?20:0);
            for(var slot:EquipmentSlot.values()) if(slot!=EquipmentSlot.MAINHAND) actor.setItemSlot(slot,ItemStack.EMPTY);
            var sword=new ItemStack(SBItems.slashblade); var s=state(sword);
            s.setDefaultBewitched(true); s.setKillCount(1000); s.setBaseAttackModifier(6); s.setColorCode(0x66CCFF);
            sword.enchant(actor.level().registryAccess().getOrThrow(Enchantments.SHARPNESS),1);
            actor.setItemInHand(InteractionHand.MAIN_HAND,sword);
            target=EntityType.COW.create(actor.level(),EntitySpawnReason.COMMAND);
            target.setNoAi(true); target.setNoGravity(true);
            // The normal targeting policy includes glowing passive entities as training targets.
            target.setGlowingTag(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            target.setPos(origin.add(0,0,2.5)); actor.level().addFreshEntity(target); s.setTargetEntityId(target);
            actor.inventoryMenu.broadcastChanges();
            began=System.nanoTime(); running=true;
        });
    }
    private static ISlashBladeState state(ItemStack stack) { return SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new); }
    private static void serverTick() {
        shownTick=totalTick++;
        actor.setYRot(0); actor.setXRot(FIRST_PERSON?20:0); actor.setOnGround(true);
        target.setPos(origin.add(0,0,2.5)); target.setDeltaMovement(Vec3.ZERO);
        damage+=Math.max(0,1000-target.getHealth()); target.setHealth(1000);
        if(target.getExistingDataOrNull(SBData.SUPER_FREEZE)!=null) frozenSamples++;
        if(stage>=stages.size()) { done=true; return; }
        Stage current=stages.get(stage); label=current.name;
        var stack=actor.getMainHandItem(); var s=state(stack);
        if(stageTick==0) {
            events.add(Map.of("tick",shownTick,"stage",current.name,"action",current.action,"combo",current.combo==null?"":current.combo.getName()));
            SlashBlade.LOGGER.info("Combat showcase: tick={} {}",shownTick,current.name);
            if(current.combo!=null) { s.updateComboSeq(actor,current.combo); actor.swing(InteractionHand.MAIN_HAND); }
            switch(current.action) {
                case "charge" -> { s.updateComboSeq(actor,ComboState.NONE); actor.startUsingItem(InteractionHand.MAIN_HAND); }
                case "sa", "just" -> {
                    int held=current.action.equals("sa")?20:10;
                    ((ItemSlashBlade)stack.getItem()).releaseUsing(stack,actor.level(),actor,72000-held);
                    actor.stopUsingItem();
                    SlashBlade.LOGGER.info("Combat showcase release: held={} combo={}",held,s.getComboSeq().getName());
                }
                case "super-charge" -> { s.setDamage(0); s.setKillCount(1000); style(true); }
                case "super" -> {
                    style(false);
                    SlashBlade.LOGGER.info("Combat showcase Super SA release: combo={} damage={} kills={}",s.getComboSeq().getName(),s.getDamage(),s.getKillCount());
                }
            }
            s.sendChanges(actor);
        }
        for(var cut:actor.level().getEntitiesOfClass(EntityJudgementCut.class,actor.getBoundingBox().inflate(40),c->c.getOwner()==actor))
            cuts.computeIfAbsent(current.action.isEmpty()?"other":current.action,k->new HashSet<>()).add(cut.getId());
        if(++stageTick>=current.ticks) { stage++; stageTick=0; }
    }
    private static void style(boolean down) {
        var input=actor.getData(SBData.INPUT); var old=input.getCommands().clone();
        if(down) input.getCommands().add(InputCommand.STYLE); else input.getCommands().remove(InputCommand.STYLE);
        InputCommandEvent.onInputChange(actor,input,old,input.getCommands().clone());
    }
    private static void finish() {
        running=false; writer.shutdown();
        boolean passed=cuts.getOrDefault("sa",Set.of()).size()>0 && cuts.getOrDefault("just",Set.of()).size()>0
                && cuts.getOrDefault("super",Set.of()).size()>0 && damage>0 && frozenSamples>0;
        var report=new LinkedHashMap<String,Object>(); report.put("status",passed?"passed":"failed");
        report.put("frames",frame); report.put("ticks",shownTick); report.put("damage",damage); report.put("frozenSamples",frozenSamples);
        report.put("cutEntityIds",cuts); report.put("stages",events);
        if(FIRST_PERSON) {
            report.put("cameraRanges",cameraRanges);
            for(String prefix:List.of("A 连段","B 连段","C 分支","SA · 次元","Just SA · 精准","Super SA · 定身")) {
                boolean moving=cameraRanges.entrySet().stream().filter(e->e.getKey().startsWith(prefix))
                        .anyMatch(e->{var v=e.getValue();return Math.max(v[1]-v[0],Math.max(v[3]-v[2],v[5]-v[4]))>.5F;});
                if(!moving) {passed=false;report.put("missingCameraMotion",prefix);}
            }
            report.put("status",passed?"passed":"failed");
        }
        try {
            Files.write(folder.resolve("frames.tsv"),captures);
            if(FIRST_PERSON) Files.write(folder.resolve("camera.tsv"),cameraFrames);
            Files.writeString(Path.of(System.getProperty("slashblade.animationReport")+"-combat.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch(Exception error) { SlashBlade.LOGGER.error("Combat report failed",error); }
        SlashBlade.LOGGER.info("Combat showcase finished: {}",report);
        Minecraft.getInstance().stop();
    }
    private CombatShowcaseClientProbe() {}
}
