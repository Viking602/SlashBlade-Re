package mods.flammpfeil.slashblade.verification;

import com.mojang.serialization.MapCodec;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.ability.*;
import mods.flammpfeil.slashblade.capability.slashblade.*;
import mods.flammpfeil.slashblade.capability.slashblade.combo.Extra;
import mods.flammpfeil.slashblade.compat.*;
import mods.flammpfeil.slashblade.entity.*;
import mods.flammpfeil.slashblade.event.*;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.network.*;
import mods.flammpfeil.slashblade.util.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.*;

/** Real server tests. The registration event only runs when NeoForge GameTests are enabled. */
public final class PortGameTests {
    public static void register(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(SlashBlade.id("port_verification"));
        Map<String, Consumer<GameTestHelper>> tests = new LinkedHashMap<>();
        tests.put("registrations", h -> {
            h.assertValueEqual(BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals("slashblade")).count(), 15L, "items");
            h.assertValueEqual(BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(id -> id.getNamespace().equals("slashblade")).count(), 10L, "entities");
            h.assertValueEqual(state(blade()).getTargetEntityId(), -1, "fresh blade has no lock-on target");
        });
        tests.put("recipes_and_advancements", h -> {
            var manager = h.getLevel().getServer().getResourceManager();
            var recipes = manager.listResources("recipe", id -> id.getNamespace().equals("slashblade") && id.getPath().endsWith(".json"));
            h.assertValueEqual(recipes.size(), 39, "recipe resources");
            for (var id : recipes.keySet()) {
                var name = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(7, id.getPath().length()-5));
                h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, name)).isPresent(), "Recipe failed to load: " + name);
            }
            var advancements = manager.listResources("advancement", id -> id.getNamespace().equals("slashblade") && id.getPath().endsWith(".json"));
            h.assertValueEqual(advancements.size(), 57, "advancement resources");
            for (var id : advancements.keySet()) {
                var name = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(12, id.getPath().length()-5));
                h.assertTrue(h.getLevel().getServer().getAdvancements().get(name) != null, "Advancement failed to load: " + name);
            }
        });
        tests.put("state_save_reload", h -> {
            ItemStack stack = blade(); var s = state(stack); UUID owner = UUID.randomUUID();
            s.setKillCount(123); s.setRefine(41); s.setDamage(.45f); s.setBaseAttackModifier(9);
            s.setTranslationKey("item.slashblade.yamato"); s.setOwner(owner); s.setModel(SlashBlade.id("model/named/yamato.obj"));
            s.setTexture(SlashBlade.id("model/named/yamato.png")); s.setColorCode(0x1288ff); s.setComboRootName(Extra.STANDBY_EX.getName()); s.setComboRootAirName(Extra.STANDBY_INAIR.getName());
            s.setNoScabbard(true); s.setSealed(true); s.setSlashArtsKey("judgement_cut");
            ItemStack restored = SBItemData.load(SBItemData.save(stack)); var r = state(restored);
            h.assertValueEqual(r.getKillCount(), 123, "kills"); h.assertValueEqual(r.getRefine(), 41, "refine");
            h.assertValueEqual(r.getOwner(), owner, "owner"); h.assertValueEqual(r.getUniqueId(), s.getUniqueId(), "blade UUID");
            h.assertValueEqual(r.getDamage(), .45f, "damage"); h.assertValueEqual(r.getBaseAttackModifier(), 9f, "attack");
            h.assertValueEqual(r.getComboRoot(), Extra.STANDBY_EX, "ground combo root");
            h.assertValueEqual(r.getComboRootName(), Extra.STANDBY_EX.getName(), "ground combo name");
            h.assertValueEqual(r.getComboRootAir(), Extra.STANDBY_INAIR, "air combo root");
            h.assertValueEqual(r.getComboRootAirName(), Extra.STANDBY_INAIR.getName(), "air combo name");
            h.assertValueEqual(r.getTexture(), s.getTexture(), "texture"); h.assertValueEqual(r.getModel(), s.getModel(), "model");
            h.assertTrue(r.isNoScabbard() && r.isSealed(), "flags"); h.assertValueEqual(r.getSlashArtsKey(), "judgement_cut", "slash art");
        });
        tests.put("stack_copy_isolation", h -> {
            var original = blade(); state(original).setKillCount(70); var copy = original.copy();
            state(copy).setKillCount(90); state(original).setRefine(10);
            h.assertValueEqual(state(original).getKillCount(), 70, "original kills");
            h.assertValueEqual(state(copy).getKillCount(), 90, "copy kills"); h.assertValueEqual(state(copy).getRefine(), 0, "copy refine");
        });
        tests.put("component_external_update", h -> {
            var stack = blade(); state(stack).setKillCount(2);
            var data = stack.get(SBData.BLADE_STATE.get()).copyTag(); data.putInt("killCount", 99);
            stack.set(SBData.BLADE_STATE.get(), CustomData.of(data)); h.assertValueEqual(state(stack).getKillCount(), 99, "external component update");
        });
        tests.put("damage_and_repair", h -> {
            var stack = blade(); var s = state(stack); s.setDamage(1f); s.setBroken(true);
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var e = new AnvilUpdateEvent(stack, new ItemStack(SBItems.proudsoul), null, ItemStack.EMPTY, 0, 0, player);
            RefineHandler.getInstance().onAnvilUpdateEvent(e);
            h.assertFalse(e.getOutput().isEmpty(), "refine output"); h.assertFalse(state(e.getOutput()).isBroken(), "repaired blade");
            h.assertValueEqual(state(e.getOutput()).getRefine(), 1, "refine count"); h.assertValueEqual(e.getMaterialCost(), 1, "material cost");
            h.assertTrue(state(stack).isBroken(), "input must remain broken");
        });
        tests.put("anvil_requirements", h -> {
            var stack = blade(); var s = state(stack); s.setTranslationKey("item.slashblade.yamato");
            var recipe = new AnvilCraftingRecipe(); recipe.setTranslationKey(s.getTranslationKey()); recipe.setKillcount(20); recipe.setRefine(3); recipe.setBroken(true);
            h.assertFalse(recipe.matches(stack), "unmet requirements"); s.setKillCount(20); s.setRefine(3); s.setBroken(true);
            h.assertTrue(recipe.matches(stack), "met requirements"); s.setTranslationKey("other"); h.assertFalse(recipe.matches(stack), "wrong blade");
        });
        tests.put("anvil_reforge_preserves_progress", h -> {
            var base = blade(); state(base).setKillCount(100); state(base).setRefine(12);
            var enchantment = h.getLevel().registryAccess().getOrThrow(Enchantments.SHARPNESS); base.enchant(enchantment, 3);
            var dest = blade(); state(dest).setTranslationKey("reforged"); dest.enchant(enchantment, 1);
            var recipe = new AnvilCraftingRecipe(); recipe.setResult(dest); var result = recipe.getResult(base);
            h.assertValueEqual(state(result).getKillCount(), 100, "reforged kills"); h.assertValueEqual(state(result).getRefine(), 12, "reforged refine");
            h.assertValueEqual(SBEnchantments.level(enchantment, result), 3, "higher enchantment retained");
            h.assertValueEqual(state(result).getTranslationKey(), "reforged", "result identity");
        });
        tests.put("anvil_component_overwrite", h -> {
            var base = blade(); state(base).setKillCount(120); var change = new CompoundTag(); change.putString("translationKey", "changed");
            var components = new CompoundTag(); components.put("slashblade:blade_state", change); var overlay = new CompoundTag(); overlay.put("components", components);
            var recipe = new AnvilCraftingRecipe(); recipe.setOverwriteTag(overlay); var result = recipe.getResult(base);
            h.assertValueEqual(state(result).getKillCount(), 120, "overwrite retains kills"); h.assertValueEqual(state(result).getTranslationKey(), "changed", "overwrite name");
        });
        tests.put("ingredient_partial_matching", h -> {
            var expected = new CompoundTag(); expected.putString("translationKey", "expected"); expected.putInt("RepairCounter", 5);
            var stack = blade(); state(stack).setTranslationKey("expected"); state(stack).setRefine(5);
            var ingredient = new BladeIngredient(SBItems.slashblade.builtInRegistryHolder(), expected, new CompoundTag(), ItemStackTemplate.fromNonEmptyStack(stack));
            h.assertTrue(ingredient.test(stack), "matching blade"); state(stack).setRefine(4); h.assertFalse(ingredient.test(stack), "wrong refine");
            h.assertFalse(ingredient.test(new ItemStack(Items.IRON_SWORD)), "wrong item");
        });
        tests.put("rank_and_stun_attachments", h -> {
            var mob = h.spawn(EntityType.ZOMBIE, 3, 2, 3); mob.setNoAi(true);
            mob.getData(SBData.RANK).setRawRankPoint(500); mob.getData(SBData.INPUT).getCommands().add(InputCommand.SNEAK);
            StunManager.setStun(mob, 12); h.assertTrue(mob.getData(SBData.EFFECT).isStun(h.getLevel().getGameTime()), "stun enabled");
            var copy = EntityType.ZOMBIE.create(h.getLevel(), EntitySpawnReason.COMMAND); load(copy, save(mob), h);
            h.assertValueEqual(copy.getData(SBData.RANK).getRawRankPoint(), 500L, "rank attachment restored");
            h.assertTrue(copy.getData(SBData.INPUT).getCommands().contains(InputCommand.SNEAK), "input attachment restored");
            h.assertTrue(copy.getData(SBData.EFFECT).isStun(h.getLevel().getGameTime()), "stun attachment restored");
            StunManager.removeStun(mob); h.assertFalse(mob.getData(SBData.EFFECT).isStun(h.getLevel().getGameTime()), "stun cleared");
        });
        tests.put("blade_stand_insert_remove_pose", h -> {
            var stand = BladeStandEntity.createInstanceFromPos(h.getLevel(), h.absolutePos(new net.minecraft.core.BlockPos(3,2,3)), net.minecraft.core.Direction.NORTH, SBItems.bladestand_1);
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL); var stack = blade(); state(stack).setKillCount(13);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack); stand.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            h.assertFalse(stand.getItem().isEmpty(), "blade inserted");
            stand.setPose(net.minecraft.world.entity.Pose.values()[1]); var restored = new BladeStandEntity(SlashBlade.RegistryEvents.BladeStand, h.getLevel()); load(restored, save(stand), h);
            h.assertValueEqual(restored.getPose(), stand.getPose(), "stand pose"); h.assertValueEqual(state(restored.getItem()).getKillCount(), 13, "stand blade data");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); stand.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            h.assertTrue(stand.getItem().isEmpty() && !player.getMainHandItem().isEmpty(), "blade removed");
        });
        tests.put("combo_and_slash_damage", h -> {
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL); player.setPos(h.absoluteVec(new Vec3(3,2,3))); player.setItemInHand(InteractionHand.MAIN_HAND, blade());
            state(player.getMainHandItem()).setBaseAttackModifier(8); player.setOnGround(true);
            player.getData(SBData.INPUT).getCommands().add(InputCommand.R_CLICK);
            var next = state(player.getMainHandItem()).progressCombo(player);
            h.assertTrue(next != ComboState.NONE, "right click combo entered");
            var target = h.spawn(EntityType.ZOMBIE, 3, 2, 4); target.setNoAi(true); float before = target.getHealth();
            AttackManager.doAttackWith(h.getLevel().damageSources().playerAttack(player), 4, target, true, true);
            h.assertTrue(target.getHealth() < before, "managed slash damage applied");
            var slash = AttackManager.doSlash(player, 45f); h.assertTrue(slash != null && slash.getOwner() == player, "slash spawned with owner");
        });
        tests.put("animation_timeline_speed", h -> {
            var s = state(blade()); s.setComboSeq(Extra.EX_JUDGEMENT_CUT_SLASH); s.setLastActionTime(100);
            var timeline = mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline.resolve(s, 103, 0);
            h.assertValueEqual(timeline.combo(), Extra.EX_JUDGEMENT_CUT_SLASH, "SA phase");
            h.assertTrue(Math.abs(timeline.frame() - 1924.8F) < 0.001F, "VMD must honor the phase's 0.4 playback speed");
        });
        tests.put("animation_pmd_chunked_resource", h -> {
            // Reproduce ZIP streams: available() can be 1 and read() need not fill the buffer.
            try (var original = PortGameTests.class.getResourceAsStream("/assets/slashblade/model/pa/alex.pmd")) {
                h.assertTrue(original != null, "original player PMD resource bundled");
                var chunked = new java.io.FilterInputStream(original) {
                    @Override public int available() { return 1; }
                    @Override public int read(byte[] bytes, int offset, int length) throws java.io.IOException {
                        return super.read(bytes, offset, Math.min(length, 7));
                    }
                };
                var model = new jp.nyatla.nymmd.MmdPmdModel_BasicClass(chunked, net.minecraft.resources.Identifier::parse) {};
                for (String name : new String[] {"body", "torso", "head", "left arm", "right arm", "left leg", "right leg"})
                    h.assertTrue(model.getBoneByName(name) != null, "complete PMD bone " + name);
            } catch (Exception error) { throw new IllegalStateException("Chunked player model resource failed to load", error); }
        });
        tests.put("animation_timeline_recovery", h -> {
            var s = state(blade()); s.setComboSeq(Extra.EX_COMBO_A1); s.setLastActionTime(100);
            var timeline = mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline.resolve(s, 109, 0);
            h.assertValueEqual(timeline.combo(), Extra.EX_COMBO_A1_END, "attack advances into recovery");
            h.assertTrue(Math.abs(timeline.frame() - 14.5F) < 0.001F, "recovery preserves elapsed time");
        });
        tests.put("animation_timeline_idle", h -> {
            var s = state(blade()); s.setComboSeq(Extra.EX_COMBO_A1); s.setLastActionTime(100);
            var timeline = mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline.resolve(s, 200, 0);
            h.assertValueEqual(timeline.combo(), Extra.STANDBY_EX, "completed attack restores the standby pose");
            h.assertTrue(timeline.frame() >= 0 && timeline.frame() <= 1, "idle remains inside its loop");
        });
        tests.put("animation_timeline_remote_clock", h -> {
            var s = state(blade()); s.setComboSeq(Extra.EX_SUPER_SA); s.setLastActionTime(100);
            var other = state(blade()); other.setComboSeq(Extra.EX_SUPER_SA); other.setLastActionTime(110);
            var a = mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline.resolve(s, 112, .5F);
            var b = mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline.resolve(other, 112, .5F);
            h.assertTrue(Math.abs(a.frame() - b.frame() - 15F) < .001F, "players retain independent action clocks");
            var future = mods.flammpfeil.slashblade.client.renderer.model.BladeAnimationTimeline.resolve(other, 100, 0);
            h.assertValueEqual(future.frame(), 1900F, "future synchronized timestamp clamps at start");
        });
        tests.put("melee_after_stack_reload", h -> {
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.setPos(h.absoluteVec(new Vec3(3,2,3))); player.setOnGround(true); player.setYRot(0);
            var original = blade(); state(original).setBaseAttackModifier(6);
            var stack = SBItemData.load(SBItemData.save(original));
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                var instance = player.getAttribute(attribute);
                if (instance != null) instance.addTransientModifier(modifier);
            });
            h.assertValueEqual(state(stack).getComboRoot(), Extra.STANDBY_EX, "reloaded grounded combo");
            var target = h.spawn(EntityType.ZOMBIE, 3, 2, 5); target.setNoAi(true); target.setHealth(1);
            player.attack(target);
            h.assertValueEqual(state(stack).getComboSeq(), Extra.EX_COMBO_A1, "left-click starts grounded slash combo");
            var slashes = h.getLevel().getEntitiesOfClass(EntitySlashEffect.class, player.getBoundingBox().inflate(8));
            h.assertTrue(!slashes.isEmpty(), "left-click spawned slash entity");
            for (int i=0; i<12; i++) for (var slash : slashes) if (!slash.isRemoved()) slash.tick();
            h.assertFalse(target.isAlive(), "vanilla left-click hook dealt melee damage");
            h.assertValueEqual(state(stack).getKillCount(), 1, "left-click kill counted on reloaded blade");
        });
        tests.put("awakened_soul_crafting_remainder", h -> {
            var result = SBItems.proudsoul_awakened.getCraftingRemainder(new ItemStack(SBItems.proudsoul_awakened));
            h.assertTrue(result != null && result.create().is(SBItems.proudsoul_trapezohedron), "soul crafting remainder");
        });
        tests.put("projectile_owner_save_reload", h -> {
            var owner = h.spawn(EntityType.ZOMBIE, 3, 2, 3); owner.setNoAi(true);
            var projectile = new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect, h.getLevel()); projectile.setOwner(owner);
            var restored = new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect, h.getLevel()); load(restored, save(projectile), h);
            h.assertValueEqual(restored.getOwner(), owner, "persistent projectile owner");
        });
        tests.put("lethal_attack_kill_counter", h -> {
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL); var stack=blade(); player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var target=h.spawn(EntityType.ZOMBIE,3,2,3);target.setNoAi(true);
            AttackManager.doAttackWith(h.getLevel().damageSources().playerAttack(player),100,target,true,true);
            h.assertFalse(target.isAlive(),"target killed");h.assertValueEqual(state(stack).getKillCount(),1,"kill counter incremented");
        });
        tests.put("just_guard_prevents_damage", h -> {
            var defender=h.spawn(EntityType.ZOMBIE,3,2,3);var attacker=h.spawn(EntityType.ZOMBIE,3,2,4);defender.setNoAi(true);attacker.setNoAi(true);
            defender.setItemSlot(EquipmentSlot.MAINHAND,blade());defender.setOnGround(true);defender.setYRot(0);defender.yRotO=0;
            var input=defender.getData(SBData.INPUT);input.getCommands().add(InputCommand.SNEAK);input.getLastPressTimes().put(InputCommand.SNEAK,h.getLevel().getGameTime());
            float health=defender.getHealth(); defender.hurtServer(h.getLevel(),h.getLevel().damageSources().mobAttack(attacker),5);
            h.assertValueEqual(defender.getHealth(),health,"just guard cancelled incoming damage");
            h.assertTrue(defender.getData(SBData.RANK).getRawRankPoint()>0,"guard awards rank");
        });
        tests.put("untouchable_prevents_damage", h -> {
            var defender=h.spawn(EntityType.ZOMBIE,3,2,3);defender.setNoAi(true);var attacker=h.spawn(EntityType.ZOMBIE,3,2,4);
            mods.flammpfeil.slashblade.ability.Untouchable.setUntouchable(defender,10);float health=defender.getHealth();
            defender.hurtServer(h.getLevel(),h.getLevel().damageSources().mobAttack(attacker),8);
            h.assertValueEqual(defender.getHealth(),health,"untouchable cancelled incoming damage");
        });
        tests.put("judgement_cut_target_and_hit", h -> {
            var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(h.absoluteVec(new Vec3(3,2,3)));var stack=blade();player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var target=h.spawn(EntityType.ZOMBIE,3,2,5);target.setNoAi(true);state(stack).setTargetEntityId(target);
            var cut=mods.flammpfeil.slashblade.specialattack.JudgementCut.doJudgementCutJust(player);
            h.assertTrue(cut.getOwner()==player && cut.getIsCritical(),"just judgement cut owner and critical");
            float health=target.getHealth();for(int i=0;i<12 && !cut.isRemoved();i++)cut.tick();
            h.assertTrue(target.getHealth()<health,"judgement cut damaged locked target");
        });
        tests.put("activated_soul_awakening", h -> {
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL); var soul = new ItemStack(SBItems.proudsoul_activated);
            player.setItemInHand(InteractionHand.OFF_HAND, soul); soul.setDamageValue(1199);
            SBItems.proudsoul_activated.inventoryTick(soul,h.getLevel(),player,EquipmentSlot.OFFHAND);
            h.assertValueEqual(soul.getDamageValue(),1200,"soul maturity");
            SBItems.proudsoul_activated.inventoryTick(soul,h.getLevel(),player,EquipmentSlot.OFFHAND);
            h.assertTrue(player.getOffhandItem().is(SBItems.proudsoul_awakened),"awakened soul replaces offhand");
        });
        tests.put("lock_on_acquire_release", h -> {
            var player = fakePlayer(h, new Vec3(3,2,3));
            var target = h.spawn(EntityType.ZOMBIE,3,2,6); target.setNoAi(true);
            var input = player.getData(SBData.INPUT);
            LockOnManager.getInstance().onInputChange(new InputCommandEvent(player,input,EnumSet.noneOf(InputCommand.class),EnumSet.of(InputCommand.SNEAK)));
            h.assertValueEqual(state(player.getMainHandItem()).getTargetEntity(player.level()),target,"sneak acquires facing hostile");
            LockOnManager.getInstance().onInputChange(new InputCommandEvent(player,input,EnumSet.of(InputCommand.SNEAK),EnumSet.noneOf(InputCommand.class)));
            h.assertValueEqual(state(player.getMainHandItem()).getTargetEntityId(),-1,"releasing sneak clears target");
        });
        tests.put("enemy_step_jump", h -> {
            var player = fakePlayer(h,new Vec3(3,3,3)); player.setOnGround(false);
            var enemy = h.spawn(EntityType.ZOMBIE,3,3,3); enemy.setNoAi(true);
            double before = player.getY();
            EnemyStep.getInstance().onInputChange(inputEvent(player,InputCommand.JUMP));
            h.assertTrue(player.getY()>before+.49,"stepping on an enemy lifts the player");
            h.assertTrue(player.getData(SBData.EFFECT).isUntouchable(h.getLevel().getGameTime()),"enemy step protects player");
        });
        tests.put("kick_jump_and_cooldown", h -> {
            var player = fakePlayer(h,new Vec3(3.5,2,3.5)); player.setOnGround(false);
            h.setBlock(4,2,3,net.minecraft.world.level.block.Blocks.STONE);
            double before = player.getY(); var jumpEvent = inputEvent(player,InputCommand.JUMP);
            KickJump.getInstance().onInputChange(jumpEvent);
            h.assertTrue(player.getY()>before+.79,"nearby wall enables kick jump");
            double after = player.getY(); KickJump.getInstance().onInputChange(jumpEvent);
            h.assertValueEqual(player.getY(),after,"kick jump cannot repeat before landing");
            player.setOnGround(true);
            for(int i=0;i<2;i++)KickJump.getInstance().onTickPre(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(player));
            h.assertFalse(player.getPersistentData().contains(KickJump.KEY_KICKJUMP),"landing resets kick cooldown");
        });
        tests.put("trick_up", h -> {
            var player = fakePlayer(h,new Vec3(3,2,3)); player.setOnGround(true); double before=player.getY();
            SlayerStyleArts.getInstance().onInputChange(inputEvent(player,InputCommand.FORWARD,InputCommand.SPRINT,InputCommand.SNEAK));
            h.assertTrue(player.getY()>before+.79 && !player.onGround(),"trick up lifts player");
            h.assertTrue(player.getData(SBData.EFFECT).isUntouchable(h.getLevel().getGameTime()),"trick up grants invulnerability");
        });
        tests.put("trick_down", h -> {
            var player = fakePlayer(h,new Vec3(3.5,4,3.5)); player.setOnGround(false); double before=player.getY();
            h.setBlock(3,0,3,net.minecraft.world.level.block.Blocks.STONE);
            SlayerStyleArts.getInstance().onInputChange(inputEvent(player,InputCommand.BACK,InputCommand.SPRINT,InputCommand.SNEAK));
            h.assertTrue(player.onGround() && player.getY()<before-1,"trick down lands on the floor");
            h.assertTrue(player.getData(SBData.EFFECT).isUntouchable(h.getLevel().getGameTime()),"trick down protects landing");
        });
        tests.put("trick_dodge", h -> {
            var player = fakePlayer(h,new Vec3(3.5,1,3.5)); player.setOnGround(true); var before=player.position();
            SlayerStyleArts.getInstance().onInputChange(inputEvent(player,InputCommand.FORWARD,InputCommand.SPRINT));
            h.assertTrue(player.position().distanceToSqr(before)>.25,"ground dodge moves player");
            h.assertTrue(player.getData(SBData.EFFECT).isUntouchable(h.getLevel().getGameTime()),"ground dodge prevents damage");
        });
        tests.put("air_trick_teleport", h -> {
            var player = fakePlayer(h,new Vec3(3,2,3));
            var target=h.spawn(EntityType.ZOMBIE,3,2,6);target.setNoAi(true);state(player.getMainHandItem()).setTargetEntityId(target);
            player.setLastHurtMob(target);
            SlayerStyleArts.getInstance().onInputChange(inputEvent(player,InputCommand.FORWARD,InputCommand.SPRINT,InputCommand.SNEAK));
            h.assertValueEqual(player.getPersistentData().getIntOr("sb.airtrick.counter",0),3,"air trick schedules teleport");
            for(int i=0;i<3;i++)SlayerStyleArts.getInstance().onTickPre(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(player));
            var expected=target.position().add(0,target.getBbHeight()/2.0,0).add(player.getLookAngle().scale(-2));
            h.assertTrue(player.position().distanceToSqr(expected)<.01,"air trick teleports near locked target");
            h.assertFalse(player.getPersistentData().contains("sb.airtrick.counter"),"air trick completes pending teleport");
        });
        tests.put("arrow_reflection_and_tnt_extinguish", h -> {
            var player=fakePlayer(h,new Vec3(3,2,3));var target=h.spawn(EntityType.ZOMBIE,3,2,7);target.setNoAi(true);
            state(player.getMainHandItem()).setTargetEntityId(target);
            var arrow=h.spawn(EntityType.ARROW,3,2,4);
            ArrowReflector.doReflect(arrow,player);
            h.assertTrue(arrow.isCritArrow() && arrow.isNoGravity(),"reflected arrow is critical and gravity free");
            h.assertTrue(arrow.getDeltaMovement().normalize().dot(target.getEyePosition().subtract(arrow.position()).normalize())>.99,"reflection aims at locked enemy");
            var slash=new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect,h.getLevel());
            ArrowReflector.doReflect(arrow,slash);
            h.assertTrue(arrow.getDeltaMovement().lengthSqr()>1,"projectile reflection accepts nonliving attacker");
            var tnt=h.spawn(EntityType.TNT,3,2,4);TNTExtinguisher.doExtinguishing(tnt,player);
            h.assertTrue(tnt.isRemoved(),"slash extinguishes primed TNT");
            h.assertTrue(!h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,tnt.getBoundingBox().inflate(2),e->e.getItem().is(Items.TNT)).isEmpty(),"extinguished TNT becomes an item");
        });
        tests.put("give_visual_item_expires", h -> {
            var stack=blade();var fake=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),0,0,0,stack);fake.makeFakeItem();
            h.assertTrue(SBItems.slashblade.createEntity(h.getLevel(),fake,stack)==null,"visual-only give item keeps vanilla expiration");
        });
        tests.put("packet_move", h -> { var m = new MoveCommandMessage(); m.command = EnumSetConverter.convertToInt(EnumSet.of(InputCommand.SNEAK, InputCommand.R_CLICK)); h.assertValueEqual(roundtrip(MoveCommandMessage.STREAM_CODEC,m,h).command,m.command,"move packet"); });
        tests.put("packet_rank", h -> { var m = new RankSyncMessage(); m.rawPoint = 987654321L; h.assertValueEqual(roundtrip(RankSyncMessage.STREAM_CODEC,m,h).rawPoint,m.rawPoint,"rank packet"); });
        tests.put("packet_motion", h -> { var m = new MotionBroadcastMessage(); m.playerId=UUID.randomUUID();m.combo="arts_rapid_slash"; var r=roundtrip(MotionBroadcastMessage.STREAM_CODEC,m,h);h.assertValueEqual(r.playerId,m.playerId,"motion UUID");h.assertValueEqual(r.combo,m.combo,"motion combo"); });
        tests.put("packet_active_state", h -> { var m = new ActiveStateSyncMessage();m.id=321;m.activeTag=state(blade()).getActiveState();var r=roundtrip(ActiveStateSyncMessage.STREAM_CODEC,m,h);h.assertValueEqual(r.activeTag,m.activeTag,"active NBT");h.assertValueEqual(r.id,m.id,"active entity"); });
        for (int variant=0;variant<4;variant++) {
            final int mode=variant;
            tests.put("summoned_sword_art_"+variant,h -> {
                var player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SlashBladeTest"));
                player.setPos(h.absoluteVec(new Vec3(3,2,3)));player.setItemInHand(InteractionHand.MAIN_HAND,blade());player.experienceLevel=100;
                var target=h.spawn(EntityType.ZOMBIE,3,2,5);target.setNoAi(true);state(player.getMainHandItem()).setTargetEntityId(target);
                var input=player.getData(SBData.INPUT);long now=h.getLevel().getGameTime();input.getLastPressTimes().put(InputCommand.M_DOWN,now);
                input.getCommands().add(InputCommand.M_DOWN);
                if(mode>0) {input.getCommands().add(InputCommand.SNEAK);input.getCommands().add(mode==1?InputCommand.BACK:InputCommand.FORWARD);}
                input.getLastPressTimes().put(InputCommand.BACK,mode==3?now:now-100);
                mods.flammpfeil.slashblade.ability.SummonedSwordArts.getInstance().onInputChange(new InputCommandEvent(player,input,EnumSet.noneOf(InputCommand.class),input.getCommands().clone()));
                input.getScheduler().tick(player,now+10);
                EntityType<?> expected=switch(mode) {case 0->SlashBlade.RegistryEvents.SpiralSwords;case 1->SlashBlade.RegistryEvents.StormSwords;case 2->SlashBlade.RegistryEvents.BlisteringSwords;default->SlashBlade.RegistryEvents.HeavyRainSwords;};
                var host=mode==1?target:player;
                h.assertTrue(EntityAbstractSummonedSword.formationSwords(host).stream().anyMatch(entity->entity.getType()==expected),"summoned sword formation "+expected);
                h.assertTrue(player.experienceLevel<100,"summoning consumed experience");
                var swords=new ArrayList<>(EntityAbstractSummonedSword.formationSwords(host));
                for(var sword:swords) { sword.tickCount++; sword.rideTick(); }
                if(mode==0) {var sword=swords.getFirst();var before=sword.position();player.setPos(player.position().add(1,0,0));sword.tickCount++;sword.tick();h.assertTrue(sword.position().distanceToSqr(before)>.5,"orbit follows player movement");}
                for(var sword:swords) {
                    if(sword instanceof EntitySpiralSwords s)s.doFire();else if(sword instanceof EntityStormSwords s)s.doFire();else if(sword instanceof EntityBlisteringSwords s)s.doFire();else if(sword instanceof EntityHeavyRainSwords s)s.doFire();
                    for(int i=0;i<60 && sword.getFormationHost()!=null;i++){sword.tickCount++;sword.rideTick();}
                    h.assertTrue(sword.getFormationHost()==null,"formation sword launched");sword.discard();
                }
            });
        }
        for (var type : new EntityType<?>[]{SlashBlade.RegistryEvents.SummonedSword,SlashBlade.RegistryEvents.StormSwords,SlashBlade.RegistryEvents.SpiralSwords,SlashBlade.RegistryEvents.BlisteringSwords,SlashBlade.RegistryEvents.HeavyRainSwords,SlashBlade.RegistryEvents.JudgementCut,SlashBlade.RegistryEvents.SlashEffect,SlashBlade.RegistryEvents.BladeItem,SlashBlade.RegistryEvents.BladeStand,SlashBlade.RegistryEvents.PlacePreview}) {
            tests.put("entity_"+BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath(), h -> {
                var entity = type.create(h.getLevel(), EntitySpawnReason.COMMAND); h.assertTrue(entity != null,"entity constructed"); entity.setPos(h.absoluteVec(new Vec3(4,2,4)));
                if(entity instanceof net.minecraft.world.entity.item.ItemEntity item) item.setItem(blade());
                if(entity instanceof BladeStandEntity stand) { stand.currentType = SBItems.bladestand_1; stand.setItem(blade()); }
                if(entity instanceof EntityAbstractSummonedSword sword) { sword.setColor(0x55aaff); sword.setDamage(7); }
                var copy = type.create(h.getLevel(), EntitySpawnReason.COMMAND); load(copy,save(entity),h);
                h.assertValueEqual(copy.getType(),entity.getType(),"entity save/load type");
                if(copy instanceof EntityAbstractSummonedSword sword) { h.assertValueEqual(sword.getColor(),0x55aaff,"projectile colour");h.assertValueEqual(sword.getDamage(),7d,"projectile damage"); }
                h.getLevel().addFreshEntity(copy); copy.tick(); copy.discard();
            });
        }
        tests.forEach((name, test) -> event.registerTest(SlashBlade.id("port_"+name), new GameTestInstance(new TestData<>(environment,SlashBlade.id("port_test"),100,0,true)) {
            @Override public void run(GameTestHelper helper) { test.accept(helper); helper.succeed(); }
            @Override public MapCodec<? extends GameTestInstance> codec() { return FunctionGameTestInstance.CODEC; }
            @Override protected net.minecraft.network.chat.MutableComponent typeDescription() { return net.minecraft.network.chat.Component.literal("SlashBlade port verification"); }
        }));
        SlashBlade.LOGGER.info("Registered {} SlashBlade port integration tests", tests.size());
    }
    private static ItemStack blade() { return new ItemStack(SBItems.slashblade); }
    private static net.neoforged.neoforge.common.util.FakePlayer fakePlayer(GameTestHelper h,Vec3 position) {
        var player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SlashBladeTest")) {
            @Override public net.minecraft.server.level.ServerPlayer teleport(net.minecraft.world.level.portal.TeleportTransition transition) {
                var result=super.teleport(transition);
                // FakePlayer's network handler suppresses teleports. Apply the same vanilla
                // position operation that a real ServerGamePacketListenerImpl performs.
                if(result!=null && transition.newLevel()==level())
                    teleportSetPosition(net.minecraft.world.entity.PositionMoveRotation.of(transition),transition.relatives());
                return result;
            }
        };
        player.setPos(h.absoluteVec(position));player.setYRot(0);player.setItemInHand(InteractionHand.MAIN_HAND,blade());
        return player;
    }
    private static InputCommandEvent inputEvent(net.minecraft.server.level.ServerPlayer player,InputCommand... commands) {
        return new InputCommandEvent(player,player.getData(SBData.INPUT),EnumSet.noneOf(InputCommand.class),EnumSet.copyOf(Arrays.asList(commands)));
    }
    private static ISlashBladeState state(ItemStack stack) { return SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(() -> new IllegalStateException("Missing blade state")); }
    private static CompoundTag save(Entity entity) { var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,entity.registryAccess());entity.saveWithoutId(output);return output.buildResult(); }
    private static void load(Entity entity,CompoundTag tag,GameTestHelper h) { entity.load(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),tag)); }
    private static <T> T roundtrip(StreamCodec<RegistryFriendlyByteBuf,T> codec,T value,GameTestHelper h) { var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try {codec.encode(buffer,value);return codec.decode(buffer);}finally{buffer.release();} }
    private PortGameTests() {}
}
