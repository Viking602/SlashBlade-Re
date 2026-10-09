package mods.flammpfeil.slashblade.verification;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.compat.*;
import mods.flammpfeil.slashblade.event.*;
import mods.flammpfeil.slashblade.init.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/** Boundary cases pinned to Resharped's source recipes and item economy. */
public final class ResharpedProgressionGameTests {
    public static void add(Map<String,Consumer<GameTestHelper>> tests) {
        tests.put("resharped_refine_replaces_vanilla_repair",h -> {
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.experienceLevel=10;
            var blade=BladeCatalog.blade("slashblade_white");state(blade).setDamage(.8f);
            var vanilla=blade.copy();state(vanilla).setDamage(.5f);
            var event=new AnvilUpdateEvent(blade,new ItemStack(SBItems.proudsoul_ingot),null,vanilla,1,1,player);
            RefineHandler.getInstance().onAnvilUpdateEvent(event);
            h.assertValueEqual(state(event.getOutput()).getRefine(),1,"precomputed vanilla result must not skip refine");
            h.assertValueEqual(state(event.getOutput()).getProudSoulCount(),1000,"real anvil grants source material souls");
            h.assertValueEqual(state(event.getOutput()).getDamage(),0F,"real anvil repairs durability");
        });
        tests.put("resharped_xp_event_grows_held_blade",h -> {
            var player=h.makeMockPlayer(GameType.SURVIVAL);var b=BladeCatalog.blade("slashblade_wood");player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,b);
            var victim=h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE,3,2,3);victim.setNoAi(true);
            var event=new net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent(victim,player,5);
            new ResharpedProgression().experience(event);
            h.assertValueEqual(state(b).getProudSoulCount(),5,"XP event grants souls");h.assertValueEqual(event.getDroppedExperience(),5,"XP rewards remain available");
            var copy=SBItemData.load(SBItemData.save(b));h.assertValueEqual(state(copy).getProudSoulCount(),5,"earned souls survive serialization");
            state(b).setProudSoulCount(10000);state(b).setDamage(.5F);new ResharpedProgression().experience(event);
            h.assertTrue(state(b).getDamage()<.5F,"Soul Eater repairs on experience gain");
        });
        tests.put("resharped_dragon_drop_is_actual_item",h -> {
            var player=h.makeMockPlayer(GameType.SURVIVAL);var dragon=net.minecraft.world.entity.EntityType.ENDER_DRAGON.create(h.getLevel(),net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            var drops=new ArrayList<net.minecraft.world.entity.item.ItemEntity>();
            new ResharpedProgression().drops(new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(dragon,h.getLevel().damageSources().playerAttack(player),drops,true));
            h.assertValueEqual(drops.size(),1,"dragon blade drop count");var drop=drops.getFirst();
            h.assertValueEqual(drop.position(),new net.minecraft.world.phys.Vec3(0,60,0),"upstream fixed drop position");
            h.assertTrue(state(drop.getItem()).isBroken() && state(drop.getItem()).isSealed(),"drop cannot bypass repair");
        });
        tests.put("resharped_koseki_requires_wither_explosion",h -> {
            var stand=new mods.flammpfeil.slashblade.entity.BladeStandEntity(SlashBlade.RegistryEvents.BladeStand,h.getLevel());stand.setItem(BladeCatalog.blade("slashblade"));
            var wither=net.minecraft.world.entity.EntityType.WITHER.create(h.getLevel(),net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            stand.hurtServer(h.getLevel(),h.getLevel().damageSources().explosion(wither,wither),1);
            h.assertValueEqual(state(stand.getItem()).getTranslationKey(),"item.slashblade.koseki","wither explosion transforms stand blade");
        });
        tests.put("resharped_starter_keeps_vanilla_enchantments",h -> {
            var r=upgrade(h,"slashblade_wood");var in=inputs(h,r);var sword=in.stream().filter(s -> s.is(Items.WOODEN_SWORD)).findFirst().orElseThrow();var unbreaking=h.getLevel().registryAccess().getOrThrow(Enchantments.UNBREAKING);sword.enchant(unbreaking,2);
            var output=r.assemble(CraftingInput.of(r.getWidth(),r.getHeight(),in));h.assertValueEqual(SBEnchantments.level(unbreaking,output),2,"wooden sword enchantment carried into wooden blade");
        });
        tests.put("resharped_doutanuki_all_thresholds",h -> {
            var r=upgrade(h,"doutanuki");var inputs=inputs(h,r);var base=blade(inputs);var s=state(base);
            h.assertValueEqual(s.getProudSoulCount(),1000,"source ProudSoul threshold");h.assertValueEqual(s.getKillCount(),100,"source kill threshold");h.assertValueEqual(s.getRefine(),10,"source refine threshold");
            h.assertTrue(matches(h,r,inputs),"exact thresholds rejected");
            s.setProudSoulCount(999);h.assertFalse(matches(h,r,inputs),"999 souls accepted");s.setProudSoulCount(1000);
            s.setKillCount(99);h.assertFalse(matches(h,r,inputs),"99 kills accepted");s.setKillCount(100);
            s.setRefine(9);h.assertFalse(matches(h,r,inputs),"9 refines accepted");s.setRefine(10);
            s.setTranslationKey("item.slashblade.ruby");h.assertFalse(matches(h,r,inputs),"wrong blade accepted");
        });
        tests.put("resharped_muramasa_requires_unnamed_blade",h -> {
            var r=upgrade(h,"muramasa");var in=inputs(h,r);var s=state(blade(in));
            h.assertValueEqual(s.getProudSoulCount(),10000,"muramasa souls");h.assertValueEqual(s.getRefine(),20,"muramasa refine");
            h.assertTrue(matches(h,r,in),"unnamed qualifying blade");s.setTranslationKey("item.slashblade.yamato");h.assertFalse(matches(h,r,in),"named blade bypassed unnamed requirement");
        });
        tests.put("resharped_repairs_require_broken_and_sealed",h -> {
            for(var id:List.of("yamato_fix","sabigatana")) {
                var r=upgrade(h,id);var in=inputs(h,r);var s=state(blade(in));
                h.assertTrue(s.isBroken() && s.isSealed(),"repair preview flags");h.assertTrue(matches(h,r,in),"correct broken/sealed input");
                s.setSealed(false);h.assertFalse(matches(h,r,in),"unsealed broken input accepted");s.setSealed(true);s.setBroken(false);h.assertFalse(matches(h,r,in),"intact sealed input accepted");
            }
        });
        tests.put("resharped_fox_required_enchantments",h -> {
            for(var id:List.of("fox_black","fox_white")) {
                var r=upgrade(h,id);var in=inputs(h,r);var b=blade(in);
                h.assertTrue(matches(h,r,in),"source enchantment rejected");b.remove(net.minecraft.core.component.DataComponents.ENCHANTMENTS);h.assertFalse(matches(h,r,in),"missing enchantment accepted");
            }
        });
        tests.put("resharped_refine_consumes_and_caps",h -> {
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.experienceLevel=20;
            var b=BladeCatalog.blade("slashblade");var e=new AnvilUpdateEvent(b,new ItemStack(SBItems.proudsoul_tiny,12),null,ItemStack.EMPTY,0,0,player);
            RefineHandler.getInstance().onAnvilUpdateEvent(e);
            h.assertValueEqual(e.getMaterialCost(),10,"tiny cap prevents wasted materials");h.assertValueEqual(e.getXpCost(),10,"one level per refine");
            h.assertValueEqual(state(e.getOutput()).getRefine(),10,"tiny refine cap");h.assertValueEqual(state(e.getOutput()).getProudSoulCount(),1000,"10 tiny refines give 1000 souls");
            h.assertValueEqual(state(e.getOutput()).getMaxDamage(),50,"durability grows once per refine");h.assertValueEqual(state(b).getProudSoulCount(),0,"preview cannot mutate input");
            state(b).setRefine(10);state(b).setDamage(.5f);var capped=new AnvilUpdateEvent(b,new ItemStack(SBItems.proudsoul_tiny,12),null,ItemStack.EMPTY,0,0,player);RefineHandler.getInstance().onAnvilUpdateEvent(capped);
            h.assertValueEqual(state(capped.getOutput()).getProudSoulCount(),0,"repair at cap gives no souls");h.assertValueEqual(capped.getMaterialCost(),1,"cap repair consumes one");
            player.experienceLevel=0;var poor=new AnvilUpdateEvent(b,new ItemStack(SBItems.proudsoul),null,ItemStack.EMPTY,0,0,player);RefineHandler.getInstance().onAnvilUpdateEvent(poor);h.assertTrue(poor.getOutput().isEmpty(),"free refine at zero XP");
        });
        tests.put("resharped_soul_conversion_enchantment_rules",h -> {
            var r=(ProudSoulRecipe)recipe(h,"material/soul");var sharp=h.getLevel().registryAccess().getOrThrow(Enchantments.SHARPNESS);var smite=h.getLevel().registryAccess().getOrThrow(Enchantments.SMITE);
            var in=new ArrayList<ItemStack>();for(int i=0;i<4;i++)in.add(new ItemStack(SBItems.proudsoul_tiny));in.getFirst().enchant(sharp,1);
            var grid=CraftingInput.of(2,2,in);h.assertTrue(r.matches(grid,h.getLevel()),"single level-I soul rejected");h.assertValueEqual(SBEnchantments.level(sharp,r.assemble(grid)),1,"conversion loses enchantment");
            in.get(1).enchant(smite,1);h.assertFalse(r.matches(grid,h.getLevel()),"mixed soul enchantments accepted");in.get(1).remove(net.minecraft.core.component.DataComponents.ENCHANTMENTS);in.getFirst().enchant(sharp,2);h.assertFalse(r.matches(grid,h.getLevel()),"level-II duplication accepted");
        });
        tests.put("resharped_netherite_smithing_keeps_progress",h -> {
            var r=(BladeSmithingRecipe)recipe(h,"upgrades/rodai_netherite_smithing");var b=BladeCatalog.blade("rodai_diamond");state(b).setKillCount(234);state(b).setRefine(15);state(b).setProudSoulCount(5432);
            var in=new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),b,new ItemStack(Items.NETHERITE_INGOT));h.assertTrue(r.matches(in,h.getLevel()),"smithing recipe mismatch");var out=r.assemble(in);
            h.assertValueEqual(state(out).getTranslationKey(),"item.slashblade.rodai_netherite","new definition");h.assertValueEqual(state(out).getMaxDamage(),2031,"netherite durability");h.assertValueEqual(state(out).getProudSoulCount(),5432,"smithing souls retained");h.assertValueEqual(state(out).getKillCount(),234,"smithing kills retained");h.assertValueEqual(state(out).getRefine(),15,"smithing refine retained");
        });
        tests.put("resharped_sources_and_soul_gain",h -> {
            h.assertValueEqual(ResharpedProgression.soulGain(5,5),7,"rank bonus floors");h.assertValueEqual(ResharpedProgression.soulGain(12000,7),100,"single kill cap");
            h.assertValueEqual(ResharpedProgression.zombieBlade(.02f,1),"sabigatana","intact zombie blade");h.assertValueEqual(ResharpedProgression.zombieBlade(.1f,1),"sabigatana_broken","broken zombie blade");h.assertTrue(ResharpedProgression.zombieBlade(.2f,1).isEmpty(),"zombie probability");
            var drop=ResharpedProgression.dropFor("minecraft:ender_dragon");h.assertValueEqual(drop.blade(),"yamato_broken","dragon drops broken Yamato");h.assertValueEqual(drop.chance(),1F,"dragon guaranteed drop");
        });
        tests.put("resharped_legacy_blades_remain_usable",h -> {
            var r=upgrade(h,"slashblade_white");var in=inputs(h,r);int slot=in.indexOf(blade(in));var legacy=new ItemStack(SBItems.slashblade);state(legacy).setTranslationKey("item.slashblade.simple.wood");state(legacy).setKillCount(37);in.set(slot,legacy);
            h.assertTrue(matches(h,r,in),"old saved wooden blade cannot upgrade");h.assertValueEqual(state(r.assemble(CraftingInput.of(r.getWidth(),r.getHeight(),in))).getKillCount(),37,"legacy progress lost");
        });
    }
    private static Recipe<?> recipe(GameTestHelper h,String path) { return h.getLevel().getServer().getRecipeManager().getRecipes().stream().filter(r -> r.id().identifier().equals(SlashBlade.id(path))).findFirst().orElseThrow().value(); }
    private static BladeUpgradeRecipe upgrade(GameTestHelper h,String path) { return (BladeUpgradeRecipe)recipe(h,"upgrades/"+path); }
    private static ArrayList<ItemStack> inputs(GameTestHelper h,BladeUpgradeRecipe r) { var context=SlotDisplayContext.fromLevel(h.getLevel());var in=new ArrayList<ItemStack>();for(var i:r.getIngredients())in.add(i.map(x -> x.display().resolveForFirstStack(context)).orElse(ItemStack.EMPTY));return in; }
    private static ItemStack blade(List<ItemStack> in) { return in.stream().filter(s -> s.getItem() instanceof ItemSlashBlade).findFirst().orElseThrow(); }
    private static boolean matches(GameTestHelper h,BladeUpgradeRecipe r,List<ItemStack> in) { return r.matches(CraftingInput.of(r.getWidth(),r.getHeight(),in),h.getLevel()); }
    private static mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState state(ItemStack s) { return SBData.get(s,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new); }
}
