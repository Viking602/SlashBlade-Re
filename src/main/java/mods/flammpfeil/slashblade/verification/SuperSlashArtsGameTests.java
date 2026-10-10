package mods.flammpfeil.slashblade.verification;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.ability.SuperSlashArts;
import mods.flammpfeil.slashblade.capability.slashblade.*;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.event.InputCommandEvent;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.slasharts.ResharpedCombos;
import mods.flammpfeil.slashblade.specialattack.*;
import mods.flammpfeil.slashblade.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Resharped input timing, vanilla durability semantics and actual Super cut damage. */
final class SuperSlashArtsGameTests {
    static void add(Map<String, Consumer<GameTestHelper>> tests) {
        tests.put("super_sa_held_sprint_auto_cast", h -> {
            var player = player(h); var state = state(player); long now = h.getLevel().getGameTime();
            input(player, now, InputCommand.SPRINT);
            tick(player, now + 19);
            h.assertValueEqual(state.getDamage(), 0f, "not charged before tick 20");
            tick(player, now + 20);
            h.assertValueEqual(state.getComboSeq(), ResharpedCombos.JUDGEMENT_CUT_END, "casts while sprint is still held");
            h.assertValueEqual(player.getMainHandItem().getDamageValue(), 20, "half of the 40-point durability");
            state.setDamage(0); state.setComboSeq(ComboState.NONE);
            tick(player, now + 100);
            h.assertValueEqual(state.getDamage(), 0f, "holding does not repeat the cast");
            input(player, now + 101);
            h.assertValueEqual(state.getDamage(), 0f, "release does not cast again"); h.succeed();
        });
        tests.put("super_sa_early_release_and_repress", h -> {
            var player = player(h); long now = h.getLevel().getGameTime();
            input(player, now, InputCommand.SPRINT); input(player, now + 10);
            input(player, now + 11, InputCommand.SPRINT);
            tick(player, now + 20);
            h.assertValueEqual(state(player).getDamage(), 0f, "old scheduled cast cannot consume a new press");
            tick(player, now + 30);
            h.assertValueEqual(state(player).getDamage(), 0f, "new press needs a full second");
            tick(player, now + 31);
            h.assertValueEqual(state(player).getComboSeq(), ResharpedCombos.JUDGEMENT_CUT_END, "new press casts at its own deadline"); h.succeed();
        });
        for (int gate = 0; gate < 8; gate++) {
            final int mode = gate;
            tests.put("super_sa_resharped_requirement_" + gate, h -> {
                var player = player(h); var state = state(player); long now = h.getLevel().getGameTime();
                switch (mode) {
                    case 0 -> state.setKillCount(999);
                    case 1 -> state.setDamage(.1f);
                    case 2 -> state.setBroken(true);
                    case 3 -> state.setSealed(true);
                    case 4 -> player.getMainHandItem().remove(DataComponents.ENCHANTMENTS);
                    case 5 -> state.setDefaultBewitched(false);
                    case 6 -> player.setOnGround(false);
                    default -> { }
                }
                float before = state.getDamage();
                if (mode == 7) input(player, now, InputCommand.SPRINT, InputCommand.SNEAK, InputCommand.FORWARD);
                else input(player, now, InputCommand.SPRINT);
                tick(player, now + 20);
                h.assertValueEqual(state.getDamage(), before, "ineligible charge has no durability cost");
                h.assertValueEqual(state.getComboSeq(), ComboState.NONE, "ineligible charge cannot attack"); h.succeed();
            });
        }
        for (var command : List.of(InputCommand.FORWARD, InputCommand.SNEAK)) {
            tests.put("super_sa_allowed_input_" + command.name().toLowerCase(Locale.ROOT), h -> {
                var player = player(h); long now = h.getLevel().getGameTime();
                input(player, now, InputCommand.SPRINT, command); tick(player, now + 20);
                h.assertValueEqual(state(player).getComboSeq(), ResharpedCombos.JUDGEMENT_CUT_END, "only simultaneous movement and sneak suppress charge"); h.succeed();
            });
        }
        tests.put("super_sa_uses_blade_at_deadline", h -> {
            var player = player(h); var first = player.getMainHandItem(); long now = h.getLevel().getGameTime();
            input(player, now, InputCommand.SPRINT);
            player.setItemInHand(InteractionHand.MAIN_HAND, blade(h)); tick(player, now + 20);
            h.assertValueEqual(first.getDamageValue(), 0, "the old blade is untouched");
            h.assertValueEqual(player.getMainHandItem().getDamageValue(), 20, "Resharped evaluates the held blade at the deadline"); h.succeed();
        });
        for (int protection = 0; protection < 2; protection++) {
            final int mode = protection;
            tests.put("super_sa_durability_exemption_" + mode, h -> {
                var player = player(h);
                if (mode == 0) player.setGameMode(GameType.CREATIVE);
                else player.getMainHandItem().set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
                SuperSlashArts.releaseSSA(player);
                h.assertValueEqual(state(player).getComboSeq(), ResharpedCombos.JUDGEMENT_CUT_END, "exempt blade still casts");
                h.assertValueEqual(player.getMainHandItem().getDamageValue(), 0, "vanilla durability exemption applies"); h.succeed();
            });
        }
        tests.put("super_sa_unbreaking_and_odd_durability", h -> {
            var player = player(h); var stack = player.getMainHandItem();
            state(player).setMaxDamage(41);
            stack.enchant(h.getLevel().registryAccess().getOrThrow(Enchantments.UNBREAKING), 3);
            h.getLevel().getRandom().setSeed(602);
            int expected = EnchantmentHelper.processDurabilityChange(h.getLevel(), stack, 20);
            h.getLevel().getRandom().setSeed(602);
            SuperSlashArts.releaseSSA(player);
            h.assertValueEqual(stack.getDamageValue(), expected, "integer half cost uses vanilla Unbreaking"); h.succeed();
        });
        tests.put("super_sa_custom_art_and_priority", h -> {
            var player = player(h); var state = state(player);
            var art = new SlashArts("verification_super", e -> ComboState.NONE).setComboStateSuper(e -> ResharpedCombos.DRIVE_VERTICAL);
            state.setSlashArtsKey(art.getName()); SuperSlashArts.releaseSSA(player);
            h.assertValueEqual(state.getComboSeq(), ResharpedCombos.DRIVE_VERTICAL, "dispatches this blade's Super art");
            state.setDamage(0); state.setComboSeq(Extra.EX_SUPER_SA);
            SuperSlashArts.releaseSSA(player);
            h.assertValueEqual(state.getComboSeq(), Extra.EX_SUPER_SA, "a lower-priority art cannot replace the current attack");
            h.assertValueEqual(player.getMainHandItem().getDamageValue(), 20, "Resharped settles durability before combo priority"); h.succeed();
        });
        tests.put("super_sa_target_range_and_slow_without_freeze", h -> {
            var player = player(h); double reach = TargetSelector.getResolvedReach(player) + 32;
            var near = target(h, player.position().add(0, 24, 0));
            var far = target(h, player.position().add(reach + 3, 0, 0));
            JudgementCut.doJudgementCutSuper(player);
            h.assertTrue(near.hasEffect(MobEffects.SLOWNESS), "high targets are included in the 48-block search box");
            var slow = near.getEffect(MobEffects.SLOWNESS);
            h.assertValueEqual(slow.getDuration(), 40, "two seconds of slowness");
            h.assertValueEqual(slow.getAmplifier(), 10, "Resharped slowness amplifier");
            h.assertFalse(far.hasEffect(MobEffects.SLOWNESS), "resolved reach plus 32 limits targets");
            h.assertFalse(near.hasData(SBData.SUPER_FREEZE), "Super SA does not suspend entity ticks");
            var cuts = h.getLevel().getEntitiesOfClass(EntityJudgementCut.class, near.getBoundingBox().inflate(1), c -> c.getOwner() == player);
            h.assertValueEqual(cuts.size(), 1, "one cut appears immediately per target");
            h.assertValueEqual(near.getHealth(), 1000f, "no extra melee strike before cut pulses"); h.succeed();
        });
        for (int power : new int[]{0, 3}) tests.put("judgement_cut_resharped_pulses_power_" + power, h -> {
            var player = player(h); var target = target(h, player.position().add(0, 0, 2));
            if (power > 0) player.getMainHandItem().enchant(h.getLevel().registryAccess().getOrThrow(Enchantments.POWER), power);
            var cut = new EntityJudgementCut(mods.flammpfeil.slashblade.SlashBlade.RegistryEvents.JudgementCut, h.getLevel());
            cut.setOwner(player); cut.setPos(target.position());
            float perHit = (float) ((1 + power * .1) * player.getAttributeValue(Attributes.ATTACK_DAMAGE) * .16);
            for (int tick = 2; tick <= 10; tick += 2) {
                cut.tickCount = tick; cut.tick();
                h.assertTrue(Math.abs(target.getHealth() - (1000 - perHit * tick / 2)) < .001, "five forced .16-ratio pulses, including Power bonus");
            }
            h.succeed();
        });
    }

    private static ItemStack blade(GameTestHelper h) {
        var stack = new ItemStack(SBItems.slashblade); var state = SBData.get(stack, ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        state.setBaseAttackModifier(6); state.setDefaultBewitched(true); state.setKillCount(1000);
        stack.enchant(h.getLevel().registryAccess().getOrThrow(Enchantments.SHARPNESS), 1); return stack;
    }
    private static ServerPlayer player(GameTestHelper h) {
        var player = new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "SuperTest"));
        player.setGameMode(GameType.SURVIVAL); player.setPos(h.absoluteVec(new Vec3(3, 2, 3))); player.setOnGround(true);
        player.getFoodData().setFoodLevel(0); player.setItemInHand(InteractionHand.MAIN_HAND, blade(h));
        player.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            var instance = player.getAttribute(attribute); if (instance != null) instance.addTransientModifier(modifier);
        });
        return player;
    }
    private static ISlashBladeState state(ServerPlayer player) { return SBData.get(player.getMainHandItem(), ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new); }
    private static void input(ServerPlayer player, long time, InputCommand... commands) {
        var input = player.getData(SBData.INPUT); var old = input.getCommands().clone(); input.getCommands().clear();
        for (var command : commands) {
            input.getCommands().add(command); if (!old.contains(command)) input.getLastPressTimes().put(command, time);
        }
        SuperSlashArts.getInstance().onInputChange(new InputCommandEvent(player, input, old, input.getCommands().clone()));
    }
    private static void tick(ServerPlayer player, long time) { player.getData(SBData.INPUT).getScheduler().tick(player, time); }
    private static LivingEntity target(GameTestHelper h, Vec3 position) {
        var target = h.spawn(EntityType.HUSK, 3, 2, 7); target.setNoAi(true); target.setNoGravity(true); target.setPos(position);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.getAttribute(Attributes.ARMOR).setBaseValue(0); target.setHealth(1000); return target;
    }
    private SuperSlashArtsGameTests() {}
}
