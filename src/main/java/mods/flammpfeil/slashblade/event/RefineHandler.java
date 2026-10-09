package mods.flammpfeil.slashblade.event;

import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.AdvancementHelper;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

public class RefineHandler {
    private static final class SingletonHolder {
        private static final RefineHandler instance = new RefineHandler();
    }
    public static RefineHandler getInstance() {
        return SingletonHolder.instance;
    }
    private RefineHandler(){}
    public void register(){
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onAnvilUpdateEvent(AnvilUpdateEvent event){
        if(!event.getOutput().isEmpty()) return;

        ItemStack base = event.getLeft();
        ItemStack material = event.getRight();

        if(base.isEmpty()) return;
        if(!(base.getItem() instanceof ItemSlashBlade)) return;
        if(material.isEmpty()) return;

        boolean isRepairable = base.isValidRepairItem(material);

        if(!isRepairable) return;

        int level = java.util.Optional.ofNullable(material.get(net.minecraft.core.component.DataComponents.ENCHANTABLE)).map(net.minecraft.world.item.enchantment.Enchantable::value).orElse(0);

        if(level < 0) return;

        ItemStack result = base.copy();

        int refineLimit = Math.max(10, level);

        int cost = 0;
        while(cost < material.getCount()){
            cost ++;

            float damage = SBData.get(result, ItemSlashBlade.BLADESTATE).map(s->{
                s.setDamage(s.getDamage() - (0.2f + 0.05f * level));
                if(s.getRefine() < refineLimit)
                    s.setRefine(s.getRefine() + 1);
                return s.getDamage();
            }).orElse(0f);

            if(damage <= 0f) break;
        }

        event.setMaterialCost(cost);
        int levelCostBase = 1;
        event.setXpCost(levelCostBase * cost);
        event.setOutput(result);
    }

    static private final Identifier REFINE = Identifier.fromNamespaceAndPath(SlashBlade.modid, "tips/refine");

    static private final TagKey<Item> soul = ItemTags.create(Identifier.fromNamespaceAndPath("slashblade", "proudsouls"));

    @SubscribeEvent
    public void onAnvilRepairEvent(AnvilCraftEvent.Post event){

        if(!(event.getEntity() instanceof ServerPlayer)) return;

        ItemStack material = event.getRight();//.getIngredientInput();
        ItemStack base = event.getLeft();//.getItemInput();
        ItemStack output = event.getOutput();

        if(base.isEmpty()) return;
        if(!(base.getItem() instanceof ItemSlashBlade)) return;
        if(material.isEmpty()) return;

        boolean isRepairable = base.isValidRepairItem(material);

        if(!isRepairable) return;

        int before = SBData.get(base, ItemSlashBlade.BLADESTATE).map(s->s.getRefine()).orElse(0);
        int after = SBData.get(output, ItemSlashBlade.BLADESTATE).map(s->s.getRefine()).orElse(0);

        if(before < after)
            AdvancementHelper.grantCriterion((ServerPlayer) event.getEntity(), REFINE);

    }

}
