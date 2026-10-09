package mods.flammpfeil.slashblade.verification;
import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.*;
import mods.flammpfeil.slashblade.compat.*;
import mods.flammpfeil.slashblade.capability.slashblade.*;
import mods.flammpfeil.slashblade.entity.*;
import mods.flammpfeil.slashblade.event.*;
import mods.flammpfeil.slashblade.event.bladestand.*;
import mods.flammpfeil.slashblade.init.*;
import mods.flammpfeil.slashblade.item.*;
import mods.flammpfeil.slashblade.registry.*;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import mods.flammpfeil.slashblade.specialattack.SlashArts;
import mods.flammpfeil.slashblade.slasharts.ResharpedCombos;
import mods.flammpfeil.slashblade.util.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
public final class ResharpedCombatGameTests {
    public static void add(Map<String,Consumer<GameTestHelper>> tests) {
        for(String name:List.of("judgement_cut","sakura_end","void_slash","circle_slash","drive_vertical","drive_horizontal","wave_edge","piercing")) {
            tests.put("resharped_"+name+"_deals_damage",h->{
                var p=player(h);var stack=p.getMainHandItem();var s=state(stack);s.setSlashArtsKey("slashblade:"+name);s.setProudSoulCount(100);
                var target=target(h,3,2,5);s.setTargetEntityId(target);
                SBItems.slashblade.releaseUsing(stack,h.getLevel(),p,71980);
                h.assertTrue(s.getComboSeq()!=ComboState.NONE,"release starts "+name);
                h.assertValueEqual(s.getProudSoulCount(),80,"one SA soul charge");
                h.onEachTick(()->((ItemSlashBlade)stack.getItem()).tickInventory(stack,h.getLevel(),p,true));
                h.runAfterDelay(85,()->{h.assertTrue(target.getHealth()<1000,name+" actually damages a hostile mob");h.succeed();});
            });
        }
        tests.put("resharped_sakura_auto_followup",h->{
            var p=player(h);var s=state(p.getMainHandItem());s.updateComboSeq(p,ResharpedCombos.SAKURA_END_LEFT);
            h.onEachTick(()->((ItemSlashBlade)p.getMainHandItem().getItem()).tickInventory(p.getMainHandItem(),h.getLevel(),p,true));
            h.runAfterDelay(7,()->{
                var slashes=h.getLevel().getEntitiesOfClass(EntitySlashEffect.class,p.getBoundingBox().inflate(6),e->e.getOwner()==p);
                h.assertTrue(slashes.stream().anyMatch(e->e.getIsCritical()),"timed right-hand slash executed without another click");h.succeed();
            });
        });
        tests.put("resharped_sa_network_and_save",h->{
            for(var field:ResharpedCombos.class.getFields())if(field.getType()==ComboState.class)try {
                var combo=(ComboState)field.get(null);var stack=BladeCatalog.blade("yamato");state(stack).setComboSeq(combo);
                var saved=SBItemData.load(SBItemData.save(stack));h.assertValueEqual(state(saved).getComboSeq(),combo,"saved "+field.getName());
                var copy=new SlashBladeState();copy.setActiveState(state(stack).getActiveState());h.assertValueEqual(copy.getComboSeq(),combo,"synced "+field.getName());
            }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}h.succeed();
        });
        tests.put("resharped_registry_and_recipes",h->{
            var defs=h.getLevel().registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY);h.assertValueEqual(defs.listElements().count(),26L,"all source named definitions");
            h.assertValueEqual(SlashArtsRegistry.VALUES.size(),9,"8 arts and NONE");h.assertValueEqual(SpecialEffectsRegistry.VALUES.size(),1,"Wither Edge");
            defs.listElements().forEach(e->{var blade=e.value().getBlade();var s=state(blade);h.assertValueEqual(s.getSlashArtsKey(),e.value().properties().art().toString(),"definition SA "+e.key());h.assertTrue(s.getSlashArts()!=null,"resolved SA");});
            h.assertValueEqual(h.getLevel().registryAccess().lookupOrThrow(mods.flammpfeil.slashblade.event.drop.EntityDropEntry.REGISTRY_KEY).listElements().count(),6L,"data driven drops");
            h.succeed();
        });
        tests.put("resharped_wither_edge_threshold",h->{
            var p=player(h);var s=state(p.getMainHandItem());s.addSpecialEffect(SlashBlade.id("wither_edge"));p.experienceLevel=19;
            ((ItemSlashBlade)p.getMainHandItem().getItem()).tickInventory(p.getMainHandItem(),h.getLevel(),p,true);
            h.assertTrue(p.hasEffect(MobEffects.WITHER),"underleveled wielder receives Wither");p.removeEffect(MobEffects.WITHER);p.experienceLevel=20;
            var target=target(h,3,2,5);p.getMainHandItem().hurtEnemy(target,p);
            h.assertTrue(target.hasEffect(MobEffects.WITHER),"qualified wielder applies Wither");
            h.assertTrue(state(SBItemData.load(SBItemData.save(p.getMainHandItem()))).hasSpecialEffect(SlashBlade.id("wither_edge")),"SE survives save");h.succeed();
        });
        tests.put("resharped_stand_change_and_extract",h->{
            var p=player(h);var blade=BladeCatalog.blade("koseki");var stand=new BladeStandEntity(SlashBlade.RegistryEvents.BladeStand,h.getLevel());stand.currentType=SBItems.bladestand_1;stand.setItem(blade);
            var orb=ArtsItems.art(SlashBlade.id("wave_edge"));p.setItemInHand(InteractionHand.MAIN_HAND,orb);stand.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(p),1);
            h.assertValueEqual(state(stand.getItem()).getSlashArts(),SlashArts.WAVE_EDGE,"applied SA");h.assertTrue(orb.isEmpty(),"SA material consumed");
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SBItems.proudsoul_crystal));stand.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(p),1);
            h.assertFalse(state(stand.getItem()).hasSpecialEffect(SlashBlade.id("wither_edge")),"copiable SE removed on extraction");h.assertTrue(p.getMainHandItem().isEmpty(),"SE copy material consumed");
            p.setItemInHand(InteractionHand.MAIN_HAND,ArtsItems.effect(SlashBlade.id("wither_edge")));stand.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(p),1);
            h.assertTrue(state(stand.getItem()).hasSpecialEffect(SlashBlade.id("wither_edge")),"SE can be installed");h.succeed();
        });
        tests.put("resharped_stand_enchant_and_copy",h->{
            var p=player(h);var blade=BladeCatalog.blade("yamato");var stand=new BladeStandEntity(SlashBlade.RegistryEvents.BladeStand,h.getLevel());stand.currentType=SBItems.bladestand_1;stand.setItem(blade);
            var sharp=h.getLevel().registryAccess().getOrThrow(Enchantments.SHARPNESS);blade=stand.getItem();blade.enchant(sharp,4);stand.currentType=SBItems.bladestand_1;stand.setItem(blade);
            var material=new ItemStack(SBItems.proudsoul_crystal);material.enchant(sharp,1);p.setItemInHand(InteractionHand.MAIN_HAND,material);
            stand.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(p),1);
            h.assertValueEqual(SBEnchantments.level(sharp,stand.getItem()),5,"guaranteed crystal applies one enchantment level");
            var tiny=new ItemStack(SBItems.proudsoul_tiny);tiny.enchant(sharp,1);p.setItemInHand(InteractionHand.MAIN_HAND,tiny);
            stand.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(p),1);h.assertTrue(tiny.isEmpty(),"max-level enchant copies SA");
            h.assertValueEqual(state(stand.getItem()).getSlashArts(),SlashArts.JUDGEMENT_CUT,"copy leaves source SA");h.succeed();
        });
        tests.put("resharped_self_repair_and_config",h->{
            var p=player(h);var blade=p.getMainHandItem();var s=state(blade);s.setDamage(.5f);p.experienceLevel=30;
            ItemSlashBlade.repairUnequipped(blade,p);h.assertTrue(s.getDamage()<.5f,"bewitched blade repairs with food and XP");
            boolean old=SlashBladeConfig.SELF_REPAIR_ENABLE.get();try{SlashBladeConfig.SELF_REPAIR_ENABLE.set(false);float value=s.getDamage();ItemSlashBlade.repairUnequipped(blade,p);h.assertValueEqual(s.getDamage(),value,"disabled repair");}finally{SlashBladeConfig.SELF_REPAIR_ENABLE.set(old);}h.succeed();
        });
        tests.put("resharped_target_protection",h->{
            var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);var hostile=target(h,3,2,6);var friend=player(h);
            h.assertFalse(TargetSelector.canAttack(p,cow),"friendly mobs protected");h.assertFalse(TargetSelector.canAttack(p,friend),"PvP disabled by default");h.assertTrue(TargetSelector.canAttack(p,hostile),"hostile mobs attackable");
            var old=SlashBladeConfig.FRIENDLY_ENABLE.get();try{SlashBladeConfig.FRIENDLY_ENABLE.set(true);h.assertTrue(TargetSelector.canAttack(p,cow),"friendly fire option applied");}finally{SlashBladeConfig.FRIENDLY_ENABLE.set(old);}h.succeed();
        });
        tests.put("resharped_soul_gain_event_and_config",h->{
            int old=SlashBladeConfig.MAX_PROUD_SOUL_GOT.get();try{SlashBladeConfig.MAX_PROUD_SOUL_GOT.set(7);h.assertValueEqual(ResharpedProgression.soulGain(300,6),7,"server soul cap");}finally{SlashBladeConfig.MAX_PROUD_SOUL_GOT.set(old);}h.succeed();
        });
        tests.put("resharped_refine_cost_config",h->{
            var p=player(h);p.experienceLevel=5;int old=SlashBladeConfig.REFINE_LEVEL_COST.get();try{
                SlashBladeConfig.REFINE_LEVEL_COST.set(2);var e=new net.neoforged.neoforge.event.AnvilUpdateEvent(p.getMainHandItem(),new ItemStack(SBItems.proudsoul_tiny,5),null,ItemStack.EMPTY,0,0,p);
                RefineHandler.getInstance().onAnvilUpdateEvent(e);h.assertValueEqual(e.getMaterialCost(),2,"only affordable materials consumed");h.assertValueEqual(e.getXpCost(),4,"configured level cost");h.assertValueEqual(state(e.getOutput()).getRefine(),2,"actual refine growth");
            }finally{SlashBladeConfig.REFINE_LEVEL_COST.set(old);}h.succeed();
        });
        tests.put("resharped_blade_fire_protection",h->{
            var p=player(h);p.getMainHandItem().enchant(h.getLevel().registryAccess().getOrThrow(Enchantments.FIRE_PROTECTION),1);
            float before=p.getHealth();p.hurtServer(h.getLevel(),h.getLevel().damageSources().onFire(),4);h.assertValueEqual(p.getHealth(),before,"fire-protected blade blocks fire");h.succeed();
        });
        tests.put("resharped_sa_gate",h->{
            var p=player(h);var stack=p.getMainHandItem();var s=state(stack);s.setSealed(true);h.assertValueEqual(s.doChargeAction(p,20),ComboState.NONE,"sealed blade cannot cast");
            s.setSealed(false);s.setBroken(true);h.assertValueEqual(s.doChargeAction(p,20),ComboState.NONE,"broken blade cannot cast");
            s.setBroken(false);s.setSlashArtsKey("slashblade:none");h.assertValueEqual(s.doChargeAction(p,20),ComboState.NONE,"NONE has no fallback SA");h.succeed();
        });
    }
    private static ServerPlayer player(GameTestHelper h){var p=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ResharpedTest"));p.setPos(h.absoluteVec(new Vec3(3,2,3)));p.setYRot(0);p.setXRot(0);p.yRotO=0;p.setOnGround(true);p.setItemInHand(InteractionHand.MAIN_HAND,BladeCatalog.blade("yamato"));p.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND,(a,m)->{var attr=p.getAttribute(a);if(attr!=null)attr.addTransientModifier(m);});return p;}
    private static net.minecraft.world.entity.monster.zombie.Husk target(GameTestHelper h,int x,int y,int z){var target=h.spawn(EntityType.HUSK,x,y,z);target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setHealth(1000);return target;}
    private static ISlashBladeState state(ItemStack blade){return SBData.get(blade,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);}
}
