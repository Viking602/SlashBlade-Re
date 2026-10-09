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
        for (var blade:SBItems.blades()) registration.registerSubtypeInterpreter(blade, (stack, context) -> {
            var s=SBData.get(stack,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            return List.of(s.getTranslationKey(),s.isBroken(),s.isSealed(),s.isNoScabbard());
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
        int index=0;
        for(var base:catalog) {
          int bladeIndex=index++;
          for(var material:List.of(SBItems.proudsoul_tiny,SBItems.proudsoul,SBItems.proudsoul_ingot,SBItems.proudsoul_sphere,SBItems.proudsoul_crystal,SBItems.proudsoul_trapezohedron)) {
            var soul=new ItemStack(material);var output=base.copy();
            int enchantability=soul.get(net.minecraft.core.component.DataComponents.ENCHANTABLE).value();
            var state=SBData.get(output,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            state.setRefine(1);state.setProudSoulCount((int)Math.min(5000L,(long)enchantability*10));state.setMaxDamage(state.getMaxDamage()+1);state.setDamage(0);
            anvils.add(factory.createAnvilRecipe(base,List.of(soul),List.of(output),SlashBlade.id("refine/"+bladeIndex+"/"+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(material).getPath())));
            if(bladeIndex==0) registration.addItemStackInfo(soul,Component.translatable("slashblade.jei.refine_material",Math.max(10,enchantability),(int)Math.min(5000L,(long)enchantability*10)));
          }
        }
        registration.addRecipes(RecipeTypes.ANVIL,anvils);
        registration.addItemStackInfo(catalog,Component.translatable("slashblade.jei.progress"));
        for(var blade:catalog) {
            var state=SBData.get(blade,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            String key=SBItemData.tag(blade).getStringOr("resharped_definition",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(blade.getItem()).getPath());
            registration.addItemStackInfo(blade,Component.translatable("slashblade.route."+key));
        }
        SlashBlade.LOGGER.info("SlashBlade JEI: {} blade variants, {} refining recipes",catalog.size(),anvils.size());
    }
    @Override public void onRuntimeAvailable(IJeiRuntime available) {
        runtime=available;
        available.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,List.of(new ItemStack(SBItems.proudsoul_activated),new ItemStack(SBItems.proudsoul_awakened)));
        var vanillaRepairs=available.getRecipeManager().createRecipeLookup(RecipeTypes.ANVIL).get()
                .filter(r -> r.getUid()!=null && r.getUid().getNamespace().equals("slashblade") && !r.getUid().getPath().startsWith("refine/")).toList();
        available.getRecipeManager().hideRecipes(RecipeTypes.ANVIL,vanillaRepairs);
        // Upstream stored advancement illustrations as impossible smithing recipes.
        // Keep the advancement help but never offer a barrier-template recipe in JEI.
        var examples=AdvancementsRecipeRenderer.getInstance().recipes().byType(RecipeType.SMITHING).stream()
                .filter(r -> r.id().identifier().getNamespace().equals("slashblade") && r.id().identifier().getPath().startsWith("anvilcrafting/")).toList();
        available.getRecipeManager().hideRecipes(RecipeTypes.SMITHING,examples);
    }
    @Override public void onRuntimeUnavailable() { runtime=null; }
}
