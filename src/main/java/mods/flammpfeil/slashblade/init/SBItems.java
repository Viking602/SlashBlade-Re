package mods.flammpfeil.slashblade.init;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.item.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class SBItems {
    public static Item slashblade, proudsoul, proudsoul_ingot, proudsoul_tiny, proudsoul_sphere, proudsoul_crystal,
        proudsoul_trapezohedron, proudsoul_activated, proudsoul_awakened,
        bladestand_1, bladestand_2, bladestand_v, bladestand_s, bladestand_1w, bladestand_2w;
    private static Item.Properties properties(String name) { return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, SlashBlade.id(name))); }
    private static class SoulItem extends Item {
        SoulItem(Properties properties, int enchantability) { super(properties.enchantable(enchantability)); }
        @Override public boolean isFoil(ItemStack stack) { return true; }
        @Override public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
            if (this == proudsoul && !(entity instanceof mods.flammpfeil.slashblade.entity.BladeItemEntity)) {
                entity.health = 50;
                if (entity.isCurrentlyGlowing()) entity.setDeltaMovement(entity.getDeltaMovement().multiply(.8,0,.8).add(0,.04,0));
                else if (entity.isOnFire()) entity.setDeltaMovement(entity.getDeltaMovement().multiply(.8,.5,.8).add(0,.04,0));
            }
            return false;
        }
    }
    public static void register(RegisterEvent event) {
        event.register(Registries.ITEM, helper -> {
            helper.register(SlashBlade.id("slashblade"), slashblade = new ItemSlashBlade(new ItemTierSlashBlade(), 1, -2.4F, properties("slashblade")));
            helper.register(SlashBlade.id("proudsoul"), proudsoul = new SoulItem(properties("proudsoul"), 50));
            helper.register(SlashBlade.id("proudsoul_ingot"), proudsoul_ingot = new SoulItem(properties("proudsoul_ingot"), 100));
            helper.register(SlashBlade.id("proudsoul_tiny"), proudsoul_tiny = new SoulItem(properties("proudsoul_tiny"), 10));
            helper.register(SlashBlade.id("proudsoul_sphere"), proudsoul_sphere = new SoulItem(properties("proudsoul_sphere").rarity(Rarity.UNCOMMON), 150));
            helper.register(SlashBlade.id("proudsoul_crystal"), proudsoul_crystal = new SoulItem(properties("proudsoul_crystal").rarity(Rarity.RARE), 200));
            helper.register(SlashBlade.id("proudsoul_trapezohedron"), proudsoul_trapezohedron = new SoulItem(properties("proudsoul_trapezohedron").rarity(Rarity.EPIC), Integer.MAX_VALUE) {
                @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
                    if (!player.isCrouching()) return InteractionResult.PASS;
                    level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.NEUTRAL, 1.5F, 1);
                    return InteractionResult.SUCCESS.heldItemTransformedTo(ItemUtils.createFilledResult(player.getItemInHand(hand), player, new ItemStack(proudsoul_activated)));
                }
            });
            helper.register(SlashBlade.id("proudsoul_activated"), proudsoul_activated = new ItemSoulActivated(properties("proudsoul_activated").durability(1200).rarity(Rarity.EPIC).enchantable(Integer.MAX_VALUE)) {
                @Override public boolean isFoil(ItemStack stack) { return true; }
            });
            helper.register(SlashBlade.id("proudsoul_awakened"), proudsoul_awakened = new SoulItem(properties("proudsoul_awakened").stacksTo(1).rarity(Rarity.EPIC), Integer.MAX_VALUE) {
                @Override public ItemStackTemplate getCraftingRemainder(ItemInstance stack) { return ItemStackTemplate.fromNonEmptyStack(new ItemStack(proudsoul_trapezohedron)); }
            });
            helper.register(SlashBlade.id("bladestand_1"), bladestand_1 = new BladeStandItem(properties("bladestand_1")));
            helper.register(SlashBlade.id("bladestand_2"), bladestand_2 = new BladeStandItem(properties("bladestand_2")));
            helper.register(SlashBlade.id("bladestand_v"), bladestand_v = new BladeStandItem(properties("bladestand_v")));
            helper.register(SlashBlade.id("bladestand_s"), bladestand_s = new BladeStandItem(properties("bladestand_s")));
            helper.register(SlashBlade.id("bladestand_1w"), bladestand_1w = new BladeStandItem(properties("bladestand_1w"), true));
            helper.register(SlashBlade.id("bladestand_2w"), bladestand_2w = new BladeStandItem(properties("bladestand_2w"), true));
        });
    }
    public static ItemStack yamatoIcon() {
        ItemStack stack = new ItemStack(slashblade);
        SBData.get(stack, ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            state.setModel(SlashBlade.id("model/named/yamato.obj"));
            state.setTexture(SlashBlade.id("model/named/yamato.png"));
        });
        return stack;
    }
    public static void displayItems(CreativeModeTab.Output output) {
        for (Item item : new Item[]{slashblade, proudsoul, proudsoul_tiny, proudsoul_ingot, proudsoul_sphere, proudsoul_crystal,
            proudsoul_trapezohedron, proudsoul_activated, proudsoul_awakened, bladestand_1, bladestand_1w, bladestand_2, bladestand_2w, bladestand_s, bladestand_v})
            output.accept(item, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        BladeCatalog.displayItems(output);
    }
    public static void attributes(ItemAttributeModifierEvent event) {
        SBData.get(event.getItemStack(), ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            event.removeAllModifiersFor(Attributes.ATTACK_DAMAGE);
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(SlashBlade.id("blade_damage"), state.getBaseAttackModifier(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(SlashBlade.id("rank_damage"), state.getAttackAmplifier(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            event.addModifier(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(SlashBlade.id("blade_reach"), state.isBroken() ? ReachModifier.BrokendReach() : ReachModifier.BladeReach(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        });
    }
    private SBItems() {}
}
