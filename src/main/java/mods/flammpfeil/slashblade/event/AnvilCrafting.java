package mods.flammpfeil.slashblade.event;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.util.AdvancementHelper;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

public class AnvilCrafting {
    private static final class SingletonHolder {
        private static final AnvilCrafting instance = new AnvilCrafting();
    }
    public static AnvilCrafting getInstance() {
        return SingletonHolder.instance;
    }
    private AnvilCrafting(){}
    public void register(){
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAnvilUpdateEvent(AnvilUpdateEvent event){
        ItemStack base = event.getLeft();
        ItemStack material = event.getRight();

        if(base.isEmpty()) return;
        if(material.isEmpty()) return;

        AnvilCraftingRecipe recipe = AnvilCraftingRecipe.getRecipe(material);
        if(recipe == null) return;

        if(!recipe.matches(base)) return;

        event.setMaterialCost(1);
        event.setXpCost(recipe.getLevel());
        event.setOutput(recipe.getResult(base));
    }

    static private final Identifier REFORGE = Identifier.fromNamespaceAndPath(SlashBlade.modid, "tips/reforge");

    @SubscribeEvent
    public void onAnvilRepairEvent(AnvilCraftEvent.Post event){

        if(!(event.getEntity() instanceof ServerPlayer)) return;


        ItemStack material = event.getRight();//.getIngredientInput();
        AnvilCraftingRecipe recipe = AnvilCraftingRecipe.getRecipe(material);
        if(recipe == null) return;

        ItemStack base = event.getLeft();//.getItemInput();
        if(!recipe.matches(base)) return;

        AdvancementHelper.grantCriterion((ServerPlayer) event.getEntity(), REFORGE);
    }

}
