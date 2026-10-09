package mods.flammpfeil.slashblade.verification;

import com.mojang.serialization.MapCodec;
import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.ability.SuperSlashArts;
import mods.flammpfeil.slashblade.event.InputCommandEvent;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.network.BladeAttackMessage;
import mods.flammpfeil.slashblade.capability.slashblade.*;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.compat.*;
import mods.flammpfeil.slashblade.entity.*;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Exercises the item/input/animation paths rather than only calling damage helpers. */
public final class CombatGameTests {
    public static void register(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(SlashBlade.id("combat_verification"));
        Map<String, Consumer<GameTestHelper>> tests = new LinkedHashMap<>();
        tests.put("combo_registry_roundtrip", h -> {
            for (var type : List.of(ComboState.class, Extra.class)) {
                for (var field : type.getFields()) {
                    if (field.getType() != ComboState.class) continue;
                    try {
                        ComboState combo = (ComboState)field.get(null);
                        var original = blade(h); state(original).setComboSeq(combo);
                        var restored = SBItemData.load(SBItemData.save(original));
                        h.assertValueEqual(state(restored).getComboSeq(), combo, "saved combo " + field.getName());
                        var clientState = new SlashBladeState(); clientState.setActiveState(state(original).getActiveState());
                        h.assertValueEqual(clientState.getComboSeq(), combo, "network combo " + field.getName());
                    } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                }
            }
            h.succeed();
        });
        tests.put("timeline_independent_players", h -> {
            var first = player(h); var second = player(h);
            int[] fired = {0};
            var timeline = ComboState.TimeLineTickAction.getBuilder().put(0, e -> fired[0]++).put(2, e -> fired[0]++).build();
            long now = h.getLevel().getGameTime();
            state(first.getMainHandItem()).setLastActionTime(now-6);
            state(second.getMainHandItem()).setLastActionTime(now-3);
            timeline.accept(first); timeline.accept(second);
            h.assertValueEqual(fired[0], 2, "each player's first timeline event");
            timeline.accept(first);
            h.assertValueEqual(fired[0], 2, "same tick cannot replay the event");
            h.runAfterDelay(2, () -> {
                timeline.accept(first); timeline.accept(second);
                h.assertValueEqual(fired[0], 4, "both timelines reach their second event"); h.succeed();
            });
        });
        for (boolean air : List.of(false, true)) for (boolean right : List.of(false, true)) {
            tests.put((air?"air":"ground") + (right?"_right":"_left") + "_attack", h -> {
                var actor = h.makeMockPlayer(GameType.SURVIVAL);
                actor.setPos(h.absoluteVec(new Vec3(3,2,3))); actor.setYRot(0); actor.yRotO=0; actor.setOnGround(!air);
                var stack = blade(h); actor.setItemInHand(InteractionHand.MAIN_HAND, stack); equipAttributes(actor, stack);
                var target = h.spawn(EntityType.COW,3,2,5); target.setNoAi(true); target.setGlowingTag(true); target.setNoGravity(true); target.setHealth(1);
                if (right) SBItems.slashblade.use(h.getLevel(),actor,InteractionHand.MAIN_HAND); else actor.attack(target);
                h.assertValueEqual(state(stack).getComboSeq(), air?Extra.EX_AERIAL_RAVE_A1:Extra.EX_COMBO_A1, "item starts correct combo");
                h.onEachTick(() -> ((ItemSlashBlade)SBItems.slashblade).tickInventory(stack,h.getLevel(),actor,true));
                h.runAfterDelay(4, () -> {
                    for (var slash : h.getLevel().getEntitiesOfClass(EntitySlashEffect.class, actor.getBoundingBox().inflate(8), e -> e.getOwner()==actor))
                        for (int i=0;i<12 && !slash.isRemoved();i++) slash.tick();
                    h.assertFalse(target.isAlive(), "actual slash killed target");
                    h.assertValueEqual(state(stack).getKillCount(), 1, "kill belongs to attacking blade"); h.succeed();
                });
            });
        }
        for (boolean air : List.of(false,true)) for (int charge : new int[]{2,8,9,11,12,20}) {
            tests.put((air?"air":"ground")+"_sa_charge_"+charge, h -> {
                var actor = player(h); actor.setOnGround(!air);
                var stack = actor.getMainHandItem(); var s = state(stack);
                var target = h.spawn(EntityType.COW,3,2,7); target.setNoAi(true); target.setGlowingTag(true); target.setNoGravity(true);
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200); target.setHealth(200);
                s.setTargetEntityId(target); s.setComboSeq(ComboState.NONE);
                // Item.releaseUsing receives remaining use time from Minecraft's release packet.
                SBItems.slashblade.releaseUsing(stack,h.getLevel(),actor,72000-charge);
                if (charge<9) {
                    h.assertValueEqual(s.getComboSeq(),ComboState.NONE,"early release does not fire SA");
                    h.assertValueEqual(s.getDamage(),0f,"early release costs no durability"); h.succeed(); return;
                }
                boolean just = charge<12;
                h.assertValueEqual(s.getComboSeq(),just?Extra.EX_JUDGEMENT_CUT_SLASH_JUST:(air?Extra.EX_JUDGEMENT_CUT_SLASH_AIR:Extra.EX_JUDGEMENT_CUT),"charge selection");
                h.assertTrue(s.getDamage()>0,"SA consumed durability");
                h.onEachTick(() -> ((ItemSlashBlade)SBItems.slashblade).tickInventory(stack,h.getLevel(),actor,true));
                h.runAfterDelay(just?1:air?2:17, () -> {
                    var cuts=h.getLevel().getEntitiesOfClass(EntityJudgementCut.class,actor.getBoundingBox().inflate(12),e->e.getOwner()==actor);
                    h.assertValueEqual(cuts.size(),1,"one authoritative SA entity");
                    var cut=cuts.getFirst(); h.assertValueEqual(cut.getIsCritical(),just,"Just SA critical flag");
                    // ServerLevel increments tickCount before dispatching Entity.tick.
                    for(int i=0;i<8 && !cut.isRemoved();i++) { cut.tickCount++; cut.tick(); }
                    h.assertTrue(target.getHealth()<200,"SA dealt server damage"); h.succeed();
                });
            });
        }
        for(boolean air:List.of(false,true))tests.put((air?"air":"ground")+"_empty_left_click",h -> {
            var actor=player(h); actor.setOnGround(!air); var stack=actor.getMainHandItem();
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            BladeAttackMessage decoded;
            try {
                BladeAttackMessage.STREAM_CODEC.encode(buffer,new BladeAttackMessage(state(stack).getUniqueId()));
                decoded=BladeAttackMessage.STREAM_CODEC.decode(buffer);
            } finally { buffer.release(); }
            BladeAttackMessage.apply(decoded,actor);
            h.assertValueEqual(state(stack).getComboSeq(),air?Extra.EX_AERIAL_RAVE_A1:Extra.EX_COMBO_A1,"empty-click packet starts combo");
            h.onEachTick(()->((ItemSlashBlade)SBItems.slashblade).tickInventory(stack,h.getLevel(),actor,true));
            h.runAfterDelay(4,()-> {
                h.assertTrue(!h.getLevel().getEntitiesOfClass(EntitySlashEffect.class,actor.getBoundingBox().inflate(8),e->e.getOwner()==actor).isEmpty(),"empty left click creates slash");h.succeed();
            });
        });
        tests.put("empty_attack_wrong_blade_rejected",h -> {
            var actor=player(h); var s=state(actor.getMainHandItem());
            BladeAttackMessage.apply(new BladeAttackMessage(UUID.randomUUID()),actor);
            h.assertValueEqual(s.getComboSeq(),ComboState.NONE,"stale blade UUID cannot attack"); h.succeed();
        });
        tests.put("just_sa_repeat_cast",h -> {
            var actor=player(h); var stack=actor.getMainHandItem(); var target=h.spawn(EntityType.COW,3,2,7);
            target.setNoAi(true);target.setNoGravity(true);target.setGlowingTag(true);
            target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);
            state(stack).setTargetEntityId(target); Set<Integer> attacks=new HashSet<>();
            h.onEachTick(()-> {
                ((ItemSlashBlade)SBItems.slashblade).tickInventory(stack,h.getLevel(),actor,true);
                for(var cut:h.getLevel().getEntitiesOfClass(EntityJudgementCut.class,actor.getBoundingBox().inflate(12),e->e.getOwner()==actor))attacks.add(cut.getId());
            });
            SBItems.slashblade.releaseUsing(stack,h.getLevel(),actor,72000-9);
            h.runAfterDelay(8,()->SBItems.slashblade.releaseUsing(stack,h.getLevel(),actor,72000-9));
            h.runAfterDelay(16,()-> { h.assertValueEqual(attacks.size(),2,"repeated casts each fire exactly once");h.succeed(); });
        });
        for(int charge:new int[]{13,14})tests.put("soul_speed_just_window_"+charge,h -> {
            var actor=player(h);var boots=new ItemStack(Items.DIAMOND_BOOTS);
            boots.enchant(h.getLevel().registryAccess().getOrThrow(Enchantments.SOUL_SPEED),3);actor.setItemSlot(EquipmentSlot.FEET,boots);
            SBItems.slashblade.releaseUsing(actor.getMainHandItem(),h.getLevel(),actor,72000-charge);
            h.assertValueEqual(state(actor.getMainHandItem()).getComboSeq(),charge==13?Extra.EX_JUDGEMENT_CUT_SLASH_JUST:Extra.EX_JUDGEMENT_CUT,"Soul Speed extends capped Just window");h.succeed();
        });
        for(int gate=0;gate<7;gate++) {
            final int mode=gate;
            tests.put("super_sa_requirement_"+mode,h -> {
                var actor=player(h); var stack=actor.getMainHandItem(); var s=state(stack); s.setKillCount(1000);
                switch(mode) {
                    case 0 -> s.setKillCount(999);
                    case 1 -> s.setDamage(.01f);
                    case 2 -> s.setBroken(true);
                    case 3 -> s.setSealed(true);
                    case 4 -> stack.remove(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
                    case 5 -> s.setDefaultBewitched(false);
                    default -> { }
                }
                style(actor,true);
                h.runAfterDelay(mode==6?19:20,() -> {
                    style(actor,false);
                    h.assertValueEqual(s.getDamage(),mode==1?.01f:0f,"failed Super SA has no cost");
                    h.assertFalse(s.getComboSeq()==Extra.EX_SUPER_SA,"failed Super SA has no attack"); h.succeed();
                });
            });
        }
        tests.put("super_sa_swap_cancels_charge",h -> {
            var actor=player(h); var first=actor.getMainHandItem(); state(first).setKillCount(1000);
            style(actor,true);
            h.runAfterDelay(20,() -> {
                var second=blade(h); state(second).setKillCount(1000); actor.setItemInHand(InteractionHand.MAIN_HAND,second);
                style(actor,false);
                h.assertValueEqual(state(first).getDamage(),0f,"old blade not charged by another blade");
                h.assertValueEqual(state(second).getDamage(),0f,"new blade cannot inherit charge"); h.succeed();
            });
        });
        tests.put("super_sa_release_stun_wide_damage",h -> {
            var actor=player(h); var stack=actor.getMainHandItem(); var s=state(stack); s.setKillCount(1000);
            stack.set(net.minecraft.core.component.DataComponents.UNBREAKABLE,net.minecraft.util.Unit.INSTANCE);
            var target=h.spawn(EntityType.COW,3,2,7); target.setNoAi(true); target.setGlowingTag(true); target.setNoGravity(true);
            target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200); target.setHealth(200);
            target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(20);
            target.setPos(actor.position().add(20,0,0));
            style(actor,true);
            h.onEachTick(() -> SuperSlashArts.getInstance().onTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(actor)));
            h.runAfterDelay(20,() -> {
                style(actor,false);
                h.assertValueEqual(s.getDamage(),.5f,"Super SA always costs half durability");
                h.assertValueEqual(s.getComboSeq(),Extra.EX_SUPER_SA,"Super SA animation entered");
                h.assertTrue(target.getData(SBData.EFFECT).isStun(h.getLevel().getGameTime()+39),"40-tick wide-area stun");
                h.assertFalse(target.getData(SBData.EFFECT).isStun(h.getLevel().getGameTime()+41),"stun expires");
                h.assertValueEqual(target.getData(SBData.SUPER_FREEZE).until(),h.getLevel().getGameTime()+40,"40-tick synchronized freeze deadline");
                style(actor,false); h.assertValueEqual(s.getDamage(),.5f,"duplicate release cannot charge twice");
            });
            h.runAfterDelay(44,() -> h.assertValueEqual(target.getHealth(),200f,"attack waits for its animation"));
            h.runAfterDelay(47,() -> {
                var cuts=h.getLevel().getEntitiesOfClass(EntityJudgementCut.class,target.getBoundingBox().inflate(3),e->e.getOwner()==actor);
                h.assertValueEqual(cuts.size(),1,"one cut batches Super SA hits per target");
                float before=target.getHealth(); h.assertTrue(before<200,"wide melee strike dealt damage");
                var cut=cuts.getFirst(); for(int i=0;i<10 && !cut.isRemoved();i++){cut.tickCount++;cut.tick();}
                h.assertTrue(before-target.getHealth()>=3.9f,"remaining Super SA magic pulses bypass 20 armor"); h.succeed();
            });
        });
        tests.put("super_sa_freeze_pauses_and_resumes",h -> {
            var target=h.spawn(EntityType.COW,3,2,7);target.setNoAi(true);target.setNoGravity(true);
            target.tickCount=37;
            int initialTicks=target.tickCount;var position=target.position();
            SuperSlashArts.freezeUntil(target,h.getLevel().getGameTime()+5);
            target.setDeltaMovement(1,0,0);
            h.runAfterDelay(3,()-> {
                h.assertValueEqual(target.tickCount,initialTicks,"frozen entity age does not advance");
                h.assertValueEqual(target.position(),position,"frozen target does not move");
            });
            h.runAfterDelay(7,()-> {
                h.assertFalse(target.hasData(SBData.SUPER_FREEZE),"expired freeze attachment is removed");
                h.assertTrue(target.tickCount>initialTicks,"entity resumes ticking after freeze");h.succeed();
            });
        });
        tests.put("super_sa_freeze_tick_order",h -> {
            var target=h.spawn(EntityType.COW,3,2,7); target.tickCount=41;
            SuperSlashArts.freezeUntil(target,h.getLevel().getGameTime()+5);
            target.tickCount++;
            var afterIncrement=new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(target);
            SuperSlashArts.getInstance().freeze(afterIncrement);
            h.assertTrue(afterIncrement.isCanceled(),"NeoForge tick work is cancelled");
            h.assertValueEqual(target.tickCount,41,"age is held when tickCount advances before the event");
            var beforeIncrement=new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(target);
            SuperSlashArts.getInstance().freeze(beforeIncrement);
            h.assertTrue(beforeIncrement.isCanceled(),"ServerCore tick work is cancelled");
            h.assertValueEqual(target.tickCount,41,"age is held when tickCount advances inside the cancelled work");
            h.succeed();
        });
        tests.put("super_sa_freeze_save_compatibility",h -> {
            var ops=com.mojang.serialization.JsonOps.INSTANCE;
            var legacy=new com.google.gson.JsonObject(); legacy.addProperty("until",123L);
            var restored=FreezeState.CODEC.codec().parse(ops,legacy).getOrThrow();
            h.assertValueEqual(restored,new FreezeState(123L,0),"previous until-only saves remain readable");
            var current=new FreezeState(321L,57);
            var saved=FreezeState.CODEC.codec().encodeStart(ops,current).getOrThrow();
            h.assertValueEqual(FreezeState.CODEC.codec().parse(ops,saved).getOrThrow(),current,"freeze deadline and age survive saving");
            h.succeed();
        });
        tests.put("super_sa_freeze_sync_local_clock",h -> {
            var target=h.spawn(EntityType.COW,3,2,7); target.tickCount=27;
            var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            try {
                var server=new FreezeState(321L,903);
                FreezeState.SYNC.write(buf,server,true);
                var client=FreezeState.SYNC.read(target,buf,null);
                h.assertValueEqual(client,new FreezeState(321L,27),"sync preserves the client's own animation phase");
                h.assertValueEqual(buf.readableBytes(),0,"existing deadline-only wire format is fully consumed");
                target.tickCount=28;
                FreezeState.SYNC.write(buf,server,false);
                h.assertValueEqual(FreezeState.SYNC.read(target,buf,client),client,"repeat sync cannot advance a frozen clock");
            } finally { buf.release(); }
            h.succeed();
        });
        tests.forEach((name,test) -> event.registerTest(SlashBlade.id("combat_"+name),new GameTestInstance(new TestData<>(environment,SlashBlade.id("port_test"),180,0,true)) {
            @Override public void run(GameTestHelper h) { test.accept(h); }
            @Override public MapCodec<? extends GameTestInstance> codec() { return FunctionGameTestInstance.CODEC; }
            @Override protected net.minecraft.network.chat.MutableComponent typeDescription() { return net.minecraft.network.chat.Component.literal("SlashBlade combat integration"); }
        }));
        SlashBlade.LOGGER.info("Registered {} SlashBlade combat integration tests",tests.size());
    }
    private static ItemStack blade(GameTestHelper h) {
        var stack=new ItemStack(SBItems.slashblade); var s=state(stack); s.setBaseAttackModifier(6); s.setDefaultBewitched(true);
        stack.enchant(h.getLevel().registryAccess().getOrThrow(Enchantments.SHARPNESS),1); return stack;
    }
    private static ServerPlayer player(GameTestHelper h) {
        var player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"CombatTest"));
        player.setPos(h.absoluteVec(new Vec3(3,2,3))); player.setYRot(0); player.yRotO=0; player.setOnGround(true);
        var stack=blade(h); player.setItemInHand(InteractionHand.MAIN_HAND,stack); equipAttributes(player,stack); return player;
    }
    private static void equipAttributes(net.minecraft.world.entity.player.Player player,ItemStack stack) {
        stack.forEachModifier(EquipmentSlot.MAINHAND,(attribute,modifier)-> {
            var instance=player.getAttribute(attribute); if(instance!=null)instance.addTransientModifier(modifier);
        });
    }
    private static ISlashBladeState state(ItemStack stack) { return SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(()->new IllegalStateException("No blade state")); }
    private static void style(ServerPlayer player,boolean down) {
        var input=player.getData(SBData.INPUT); var old=input.getCommands().clone();
        if(down)input.getCommands().add(InputCommand.STYLE);else input.getCommands().remove(InputCommand.STYLE);
        InputCommandEvent.onInputChange(player,input,old,input.getCommands().clone());
    }
    private CombatGameTests() {}
}
