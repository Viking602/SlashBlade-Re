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
    private static final boolean SHOWCASE=Boolean.getBoolean("slashblade.combatShowcase");
    private static final float SHOWCASE_HEALTH=1000;
    private record Stage(String name,ComboState combo,int ticks,String action) {}
    private static final List<Stage> stages=new ArrayList<>();
    private static volatile boolean running,done;
    private static volatile String label="准备";
    private static volatile int shownTick;
    private static volatile String displayLabel="Ready";
    private static volatile float shownHealth=SHOWCASE_HEALTH,shownDamage;
    private static float previousHealth;
    private static final List<Map<String,Object>> hitEvents=new ArrayList<>();
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
    private static int slowedSamples;
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
                mc.player.setXRot(SHOWCASE?8:20); mc.player.xRotO=mc.player.getXRot();
                return;
            }
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            if(camera==null) {
                camera=new ArmorStand(mc.level,mc.player.getX(),mc.player.getY(),mc.player.getZ());
                camera.getAttribute(Attributes.CAMERA_DISTANCE).setBaseValue(SHOWCASE?4.7:5.2);
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
            if(SHOWCASE) {
                g.text(mc.font,"SlashBlade:Re  /  "+(FIRST_PERSON?"FIRST PERSON":"COMBAT SHOWCASE"),12,12,0xFFFFFFFF);
                g.text(mc.font,displayLabel,12,27,0xFFFFDD88);
                g.text(mc.font,String.format(Locale.ROOT,"Training Husk  %.1f / %.0f HP   |   Damage dealt  %.1f",shownHealth,SHOWCASE_HEALTH,shownDamage),12,42,0xFFDDDDDD);
                e.setCanceled(true); return;
            }
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
        // Renderer probes equip the local test model without touching the server inventory.
        // Clear that temporary client-only armor before recording the actual fight.
        for(var slot:EquipmentSlot.values()) if(slot!=EquipmentSlot.MAINHAND) mc.player.setItemSlot(slot,ItemStack.EMPTY);
        folder=mc.gameDirectory.toPath().resolve(SHOWCASE
                ? (FIRST_PERSON ? "screenshots/showcase-first" : "screenshots/showcase-third")
                : (FIRST_PERSON ? "screenshots/full-combat-first" : "screenshots/full-combat"));
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
        for(String art:List.of("sakura_end","void_slash","circle_slash","drive_vertical","drive_horizontal","wave_edge","piercing")) {
            stages.add(new Stage("SA · "+art+" / charge",null,20,"charge:"+art));
            stages.add(new Stage("SA · "+art+" / release / sheathe",null,100,"art:"+art));
        }
        stages.add(new Stage("Super SA · 满耐久 / 千杀 / 蓄力",null,24,"super-charge"));
        stages.add(new Stage("Super SA · 减速 / 范围次元斩 / 收刀",null,110,"super"));
        add("演示结束",null,30);
        mc.getSingleplayerServer().execute(()-> {
            actor=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            origin=actor.position(); actor.setYRot(0); actor.setXRot(FIRST_PERSON?(SHOWCASE?8:20):0);
            for(var slot:EquipmentSlot.values()) if(slot!=EquipmentSlot.MAINHAND) actor.setItemSlot(slot,ItemStack.EMPTY);
            var sword=new ItemStack(SBItems.slashblade); var s=state(sword);
            s.setProudSoulCount(10000); s.setDefaultBewitched(true); s.setKillCount(1000); s.setBaseAttackModifier(6); s.setColorCode(0x66CCFF);
            sword.enchant(actor.level().registryAccess().getOrThrow(Enchantments.SHARPNESS),1);
            actor.setItemInHand(InteractionHand.MAIN_HAND,sword);
            if(SHOWCASE) {
                actor.level().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS,false,actor.level().getServer());
                actor.level().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
                for(var mob:actor.level().getEntitiesOfClass(Mob.class,actor.getBoundingBox().inflate(64))) mob.discard();
            }
            target=EntityType.HUSK.create(actor.level(),EntitySpawnReason.COMMAND);
            target.setNoAi(true); target.setNoGravity(true);
            // The normal targeting policy includes glowing passive entities as training targets.
            target.setGlowingTag(!SHOWCASE);
            float health=SHOWCASE?SHOWCASE_HEALTH:1000;
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health); target.setHealth(health);
            previousHealth=target.getHealth();
            if(previousHealth!=health)throw new IllegalStateException("Training health exceeds the entity attribute limit");
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            target.setYRot(180); target.yHeadRot=target.yHeadRotO=target.yBodyRot=target.yBodyRotO=180;
            target.setPos(origin.add(0,0,2.5)); actor.level().addFreshEntity(target); s.setTargetEntityId(target);
            actor.inventoryMenu.broadcastChanges();
            began=System.nanoTime(); running=true;
        });
    }
    private static ISlashBladeState state(ItemStack stack) { return SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new); }
    private static void serverTick() {
        shownTick=totalTick++;
        actor.setYRot(0); actor.setXRot(FIRST_PERSON?(SHOWCASE?8:20):0); actor.setOnGround(true);
        target.setPos(origin.add(0,0,2.5)); target.setDeltaMovement(Vec3.ZERO);
        float hit=Math.max(0,(SHOWCASE?previousHealth:1000)-target.getHealth());
        damage+=hit;
        if(hit>0) hitEvents.add(Map.of("tick",shownTick,"stage",label,"damage",hit,"health",target.getHealth()));
        previousHealth=target.getHealth();
        if(!SHOWCASE) target.setHealth(1000);
        shownHealth=target.getHealth(); shownDamage=damage;
        if(target.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS) && target.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS).getAmplifier()==10) slowedSamples++;
        if(stage>=stages.size()) { done=true; return; }
        Stage current=stages.get(stage); label=current.name;
        displayLabel=englishTitle(current);
        var stack=actor.getMainHandItem(); var s=state(stack);
        if(stageTick==0) {
            events.add(Map.of("tick",shownTick,"stage",current.name,"action",current.action,"combo",current.combo==null?"":current.combo.getName()));
            SlashBlade.LOGGER.info("Combat showcase: tick={} {}",shownTick,current.name);
            if(current.combo!=null) { s.updateComboSeq(actor,current.combo); actor.swing(InteractionHand.MAIN_HAND); }
            if(current.action.startsWith("charge:")) {
                actor.teleportTo(origin.x,origin.y,origin.z);actor.setDeltaMovement(Vec3.ZERO);
                s.setSlashArtsKey("slashblade:"+current.action.substring(7));
                s.updateComboSeq(actor,ComboState.NONE);actor.startUsingItem(InteractionHand.MAIN_HAND);
            } else if(current.action.startsWith("art:")) {
                ((ItemSlashBlade)stack.getItem()).releaseUsing(stack,actor.level(),actor,72000-20);
                actor.stopUsingItem();
            }
            switch(current.action) {
                case "charge" -> { s.updateComboSeq(actor,ComboState.NONE); actor.startUsingItem(InteractionHand.MAIN_HAND); }
                case "sa", "just" -> {
                    int held=current.action.equals("sa")?20:10;
                    ((ItemSlashBlade)stack.getItem()).releaseUsing(stack,actor.level(),actor,72000-held);
                    actor.stopUsingItem();
                    SlashBlade.LOGGER.info("Combat showcase release: held={} combo={}",held,s.getComboSeq().getName());
                }
                case "super-charge" -> { actor.teleportTo(origin.x,origin.y,origin.z);actor.setDeltaMovement(Vec3.ZERO);s.setSlashArtsKey("slashblade:judgement_cut");s.setDamage(0); s.setKillCount(1000); s.updateComboSeq(actor,ComboState.NONE); style(true); }
                case "super" -> {
                    style(false);
                    SlashBlade.LOGGER.info("Combat showcase Super SA release: combo={} damage={} kills={}",s.getComboSeq().getName(),s.getDamage(),s.getKillCount());
                }
            }
            s.sendChanges(actor);
        }
        for(var cut:actor.level().getEntitiesOfClass(EntityJudgementCut.class,actor.getBoundingBox().inflate(40),c->c.getOwner()==actor))
            cuts.computeIfAbsent(current.action.startsWith("super")?"super":current.action.isEmpty()?"other":current.action,k->new HashSet<>()).add(cut.getId());
        if(++stageTick>=current.ticks) { stage++; stageTick=0; }
    }
    private static void style(boolean down) {
        var input=actor.getData(SBData.INPUT); var old=input.getCommands().clone();
        if(down) { input.getCommands().add(InputCommand.SPRINT); if(!old.contains(InputCommand.SPRINT)) input.getLastPressTimes().put(InputCommand.SPRINT,actor.level().getGameTime()); } else input.getCommands().remove(InputCommand.SPRINT);
        InputCommandEvent.onInputChange(actor,input,old,input.getCommands().clone());
    }
    private static String englishTitle(Stage current) {
        if(current.action.startsWith("charge:") || current.action.startsWith("art:"))
            return current.action.substring(current.action.indexOf(':')+1).replace('_',' ').toUpperCase(Locale.ROOT)
                    +(current.action.startsWith("charge:")?"  /  Charge":"  /  Release - recover - sheathe");
        return switch(current.action) {
            case "charge" -> stage+1<stages.size() && stages.get(stage+1).action.equals("just")
                    ? "JUST SA  /  Precision charge" : "STANDARD SA  /  Charge";
            case "sa" -> "STANDARD SA  /  Judgment Cut - recover - sheathe";
            case "just" -> "JUST SA  /  Precision cut - recover - sheathe";
            case "super-charge" -> "SUPER SA  /  Charge";
            case "super" -> "SUPER SA  /  Auto-cast - area cuts - sheathe";
            default -> current.combo==null ? (stage==0?"Ready / Full sequence, real-time":"Complete / Blade sheathed")
                    : (current.name.startsWith("强化")?"EXTENDED COMBO  /  ":"NORMAL ATTACKS  /  ")
                    +current.combo.getName().replace("ex_combo_","").toUpperCase(Locale.ROOT)
                    +(current.ticks>=80?" - recover - sheathe":"");
        };
    }
    private static void finish() {
        running=false; writer.shutdown();
        boolean passed=cuts.getOrDefault("sa",Set.of()).size()>0 && cuts.getOrDefault("just",Set.of()).size()>0
                && cuts.getOrDefault("super",Set.of()).size()>0 && damage>0 && slowedSamples>0;
        var report=new LinkedHashMap<String,Object>(); report.put("status",passed?"passed":"failed");
        report.put("frames",frame); report.put("ticks",shownTick); report.put("damage",damage); report.put("slowedSamples",slowedSamples);
        report.put("cutEntityIds",cuts); report.put("stages",events);
        report.put("hitEvents",hitEvents);
        if(SHOWCASE) {
            report.put("target","minecraft:husk"); report.put("initialHealth",SHOWCASE_HEALTH);
            report.put("finalHealth",shownHealth); report.put("targetHealthResetDuringRecording",false);
            var categoryDamage=new LinkedHashMap<String,Float>();
            for(var hit:hitEvents) {
                String title=(String)hit.get("stage");
                String category=title.startsWith("Super SA")?"super":title.startsWith("Just SA")?"just":title.startsWith("SA")?"sa":"normal";
                categoryDamage.merge(category,((Number)hit.get("damage")).floatValue(),Float::sum);
            }
            report.put("damageByCategory",categoryDamage);
            var artDamage=new LinkedHashMap<String,Float>();
            for(String art:List.of("sakura_end","void_slash","circle_slash","drive_vertical","drive_horizontal","wave_edge","piercing")) {
                float amount=0;
                for(var hit:hitEvents)if(((String)hit.get("stage")).startsWith("SA · "+art+" / release"))amount+=((Number)hit.get("damage")).floatValue();
                artDamage.put(art,amount);if(amount<=0)passed=false;
            }
            report.put("damageByResharpedArt",artDamage);
            for(String category:List.of("normal","sa","just","super"))
                if(categoryDamage.getOrDefault(category,0F)<=0) passed=false;
            if(Math.abs(SHOWCASE_HEALTH-shownHealth-damage)>.01F) passed=false;
            report.put("status",passed?"passed":"failed");
        }
        if(FIRST_PERSON) {
            report.put("cameraRanges",cameraRanges);
            for(String prefix:List.of("A 连段","B 连段","C 分支","SA · 次元","Just SA · 精准","Super SA · 减速")) {
                boolean changed=cameraRanges.entrySet().stream().filter(e->e.getKey().startsWith(prefix))
                        .anyMatch(e->{var v=e.getValue();return Math.max(Math.max(Math.abs(v[0]),Math.abs(v[1])),Math.max(Math.max(Math.abs(v[2]),Math.abs(v[3])),Math.max(Math.abs(v[4]),Math.abs(v[5]))))>.001F;});
                if(changed) {passed=false;report.put("unexpectedCameraMotion",prefix);}

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
