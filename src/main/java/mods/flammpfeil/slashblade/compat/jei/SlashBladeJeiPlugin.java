package mods.flammpfeil.slashblade.compat.jei;

import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.compat.*;
import mods.flammpfeil.slashblade.event.AnvilCraftingRecipe;
import mods.flammpfeil.slashblade.event.client.AdvancementsRecipeRenderer;
import mods.flammpfeil.slashblade.init.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;

/** Named-blade subtypes follow Resharped's JEI integration; recipes come from the connected server. */
@JeiPlugin
public final class SlashBladeJeiPlugin implements IModPlugin {
    private static IJeiRuntime runtime;
    public static IJeiRuntime runtime() { return runtime; }
    @Override public Identifier getPluginUid() { return SlashBlade.id("jei"); }
    @Override public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(SBItems.slashblade, (stack, context) -> {
            var s=SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            return List.of(s.getTranslationKey(),s.isBroken(),s.isNoScabbard());
        });
        // Material boxes are ordinary chests with a recipe component. Keep each box searchable.
        registration.registerSubtypeInterpreter(Items.CHEST, (stack, context) -> {
            var required=SBItemData.tag(stack).getCompoundOrEmpty("RequiredBlade");
            return required.isEmpty() ? null : required;
        });
    }
    @Override public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        registration.addExtraItemStacks(BladeCatalog.items());
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        var catalog=BladeCatalog.items();
        var factory=registration.getVanillaRecipeFactory();
        var anvils=new ArrayList<mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe>();
        var map=AdvancementsRecipeRenderer.getInstance().recipes();
        for (var holder:map.byType(RecipeType.CRAFTING)) {
            if (!holder.id().identifier().getNamespace().equals("slashblade")) continue;
            for (var display:holder.value().display()) for (var material:display.result().resolveForStacks(registration.getContextMap())) {
                var recipe=AnvilCraftingRecipe.getRecipe(material);
                if (recipe==null) continue;
                var candidates=catalog.stream().filter(s -> recipe.getTranslationKey().isEmpty()
                        || SBData.get(s,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new).getTranslationKey().equals(recipe.getTranslationKey())).toList();
                for (var candidate:candidates) {
                    var base=candidate.copy(); var state=SBData.get(base,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
                    state.setKillCount(recipe.getKillcount()); state.setRefine(recipe.getRefine());
                    state.setBroken(recipe.isBroken()); state.setDamage(recipe.isBroken()?1:0); state.setNoScabbard(recipe.isNoScabbard());
                    SBEnchantments.set(recipe.getEnchantments(),base);
                    if (!recipe.matches(base)) continue;
                    var output=recipe.getResult(base);
                    if (output.isEmpty()) continue;
                    anvils.add(factory.createAnvilRecipe(base,List.of(material),List.of(output),
                            SlashBlade.id("anvil/"+holder.id().identifier().getPath())));
                    registration.addItemStackInfo(output,Component.translatable("slashblade.jei.forge",recipe.getLevel()),
                            Component.translatable("slashblade.jei.requirements",recipe.getKillcount(),recipe.getRefine()));
                }
            }
        }
        registration.addRecipes(RecipeTypes.ANVIL,anvils);
        registration.addItemStackInfo(catalog,Component.translatable("slashblade.jei.progress"));
        SlashBlade.LOGGER.info("SlashBlade JEI: {} named blades, {} actual anvil upgrades",catalog.size(),anvils.size());
    }
    @Override public void onRuntimeAvailable(IJeiRuntime available) {
        runtime=available;
        // Upstream stored advancement illustrations as impossible smithing recipes.
        // Keep the advancement help but never offer a barrier-template recipe in JEI.
        var examples=AdvancementsRecipeRenderer.getInstance().recipes().byType(RecipeType.SMITHING).stream()
                .filter(r -> r.id().identifier().getNamespace().equals("slashblade") && r.id().identifier().getPath().startsWith("anvilcrafting/")).toList();
        available.getRecipeManager().hideRecipes(RecipeTypes.SMITHING,examples);
    }
    @Override public void onRuntimeUnavailable() { runtime=null; }
}
