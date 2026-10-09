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
        ItemStack base = event.getLeft();
        ItemStack material = event.getRight();

        if(base.isEmpty()) return;
        if(!(base.getItem() instanceof ItemSlashBlade)) return;
        if(material.isEmpty()) return;

        boolean isRepairable = base.isValidRepairItem(material);

        if(!isRepairable) return;
        // NeoForge 26 supplies the vanilla repair result before this event. Replace it
        // for soul materials; otherwise damaged blades silently skip refine and soul gain.
        if(mods.flammpfeil.slashblade.compat.SBItemData.tag(material).contains("RequiredBlade")) return;
        event.setOutput(ItemStack.EMPTY);

        int level = java.util.Optional.ofNullable(material.get(net.minecraft.core.component.DataComponents.ENCHANTABLE)).map(net.minecraft.world.item.enchantment.Enchantable::value).orElse(0);

        if(level < 0) return;

        ItemStack result = base.copy();

        int refineLimit = Math.max(10, level);

        var state=SBData.get(result,ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        int available=event.getPlayer().getAbilities().instabuild ? material.getCount() : Math.min(material.getCount(),event.getPlayer().experienceLevel);
        int before=state.getRefine();
        int gain=Math.min(available,Math.max(0,refineLimit-before));
        // At the cap, a single material can still repair; it cannot create free soul points.
        int cost=gain>0 ? gain : state.getDamage()>0 && available>0 ? 1 : 0;
        if(cost==0) return;
        state.setRefine(before+gain);
        state.setMaxDamage(ResharpedProgression.addClamped(state.getMaxDamage(),Math.min(before+gain,200)-Math.min(before,200)));
        state.setProudSoulCount(ResharpedProgression.addClamped(state.getProudSoulCount(),gain*Math.min(5000L,(long)level*10)));
        state.setDamage(0);
        event.setMaterialCost(cost);
        event.setXpCost(cost);
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
