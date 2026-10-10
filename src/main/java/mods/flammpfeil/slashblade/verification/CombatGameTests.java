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
                var target = h.spawn(EntityType.HUSK,3,2,5); target.setNoAi(true); target.setGlowingTag(true); target.setNoGravity(true); target.setHealth(1);
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
                var target = h.spawn(EntityType.HUSK,3,2,7); target.setNoAi(true); target.setGlowingTag(true); target.setNoGravity(true);
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
            var actor=player(h); var stack=actor.getMainHandItem(); var target=h.spawn(EntityType.HUSK,3,2,7);
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
        SuperSlashArtsGameTests.add(tests);
        ResharpedCombatGameTests.add(tests);
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
    private CombatGameTests() {}
}
