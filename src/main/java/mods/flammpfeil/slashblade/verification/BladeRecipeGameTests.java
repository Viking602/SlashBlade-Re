package mods.flammpfeil.slashblade.verification;

import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.compat.*;
import mods.flammpfeil.slashblade.init.BladeCatalog;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.enchantment.Enchantments;

/** Real recipe manager, ingredient matching, upgrade assembly and network round trips. */
public final class BladeRecipeGameTests {
    public static void add(Map<String,Consumer<GameTestHelper>> tests) {
        tests.put("catalog_has_distinct_named_blades", h -> {
            var catalog=BladeCatalog.items();
            h.assertValueEqual(catalog.size(),13,"named variants");
            h.assertValueEqual(catalog.stream().map(s -> state(s).getTranslationKey()).distinct().count(),13L,"distinct names");
        });
        tests.put("real_recipes_have_visible_materials", h -> {
            var context=SlotDisplayContext.fromLevel(h.getLevel());
            for (var holder:h.getLevel().getServer().getRecipeManager().getRecipes()) {
                if (!holder.id().identifier().getNamespace().equals("slashblade")) continue;
                h.assertFalse(holder.id().identifier().getPath().startsWith("creative_tab/"),"catalog placeholder advertised as recipe");
                for (var ingredient:holder.value().placementInfo().ingredients()) {
                    h.assertTrue(ingredient.display().resolveForStacks(context).stream().anyMatch(s -> !s.isEmpty()),"empty material: "+holder.id());
                }
            }
        });
        tests.put("all_direct_upgrades_keep_player_progress", h -> {
            int upgrades=0;
            var context=SlotDisplayContext.fromLevel(h.getLevel());
            for (var holder:h.getLevel().getServer().getRecipeManager().getRecipes()) {
                if (!(holder.value() instanceof BladeUpgradeRecipe recipe)) continue;
                upgrades++;
                var inputs=new ArrayList<ItemStack>();
                for (var ingredient:recipe.getIngredients()) inputs.add(ingredient.map(i -> i.display().resolveForFirstStack(context)).orElse(ItemStack.EMPTY));
                var base=inputs.stream().filter(s -> s.getItem() instanceof ItemSlashBlade).findFirst().orElseThrow();
                var owner=UUID.randomUUID(); var unique=state(base).getUniqueId();
                int kills=state(base).getKillCount()+173;
                state(base).setKillCount(kills); state(base).setRefine(31); state(base).setOwner(owner);
                base.set(DataComponents.CUSTOM_NAME,Component.literal("My blade"));
                var unbreaking=h.getLevel().registryAccess().getOrThrow(Enchantments.UNBREAKING);
                base.enchant(unbreaking,3);
                var before=SBItemData.save(base).copy();
                var input=CraftingInput.of(recipe.getWidth(),recipe.getHeight(),inputs);
                h.assertTrue(recipe.matches(input,h.getLevel()),"valid input rejected: "+holder.id());
                var output=recipe.assemble(input);
                h.assertFalse(output.isEmpty(),"empty crafted blade: "+holder.id());
                h.assertValueEqual(state(output).getKillCount(),kills,"kills retained");
                h.assertValueEqual(state(output).getRefine(),31,"refine retained");
                h.assertValueEqual(state(output).getOwner(),owner,"owner retained");
                h.assertValueEqual(state(output).getUniqueId(),unique,"blade identity retained");
                h.assertValueEqual(SBEnchantments.level(unbreaking,output),3,"enchantment retained");
                h.assertValueEqual(output.get(DataComponents.CUSTOM_NAME),base.get(DataComponents.CUSTOM_NAME),"name retained");
                h.assertTrue(before.equals(SBItemData.save(base)),"craft preview mutated input");
                h.assertFalse(state(output).isBroken(),"newly forged output is broken");
                var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
                try {
                    Recipe.STREAM_CODEC.encode(buffer,recipe);
                    var decoded=(BladeUpgradeRecipe)Recipe.STREAM_CODEC.decode(buffer);
                    h.assertTrue(decoded.matches(input,h.getLevel()),"client/server recipe mismatch");
                    h.assertValueEqual(state(decoded.assemble(input)).getTranslationKey(),state(output).getTranslationKey(),"network output identity");
                } finally { buffer.release(); }
            }
            h.assertValueEqual(upgrades,13,"tested direct upgrades");
        });
        tests.put("blade_requirements_reject_wrong_progress", h -> {
            var base=BladeCatalog.items().getFirst();var name=state(base).getTranslationKey();
            var ingredient=new BladeRequirementIngredient(name,100,5,true,Map.of(),ItemStackTemplate.fromNonEmptyStack(base));
            state(base).setKillCount(99);state(base).setRefine(5);state(base).setBroken(true);
            h.assertFalse(ingredient.test(base),"insufficient kills accepted");
            state(base).setKillCount(101); h.assertTrue(ingredient.test(base),"higher progress must match");
            state(base).setBroken(false);h.assertFalse(ingredient.test(base),"intact blade accepted for broken recipe");
            state(base).setBroken(true);state(base).setTranslationKey("wrong");h.assertFalse(ingredient.test(base),"wrong named blade accepted");
            h.assertFalse(ingredient.test(new ItemStack(Items.IRON_SWORD)),"vanilla sword accepted");
        });
    }
    private static mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState state(ItemStack stack) {
        return SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
    }
    private BladeRecipeGameTests() {}
}
