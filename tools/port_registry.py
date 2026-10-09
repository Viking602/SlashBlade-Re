"""Install version-specific registration code, preserving every upstream item/entity ID."""
from pathlib import Path
root = Path(__file__).resolve().parents[1] / 'src/main/java/mods/flammpfeil/slashblade'

(root / 'SlashBlade.java').write_text('''package mods.flammpfeil.slashblade;

import com.google.common.base.CaseFormat;
import mods.flammpfeil.slashblade.ability.*;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.entity.*;
import mods.flammpfeil.slashblade.event.*;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.network.NetworkManager;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.stats.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.apache.logging.log4j.*;

@Mod(SlashBlade.modid)
public class SlashBlade {
    public static final String modid = "slashblade";
    public static final Logger LOGGER = LogManager.getLogger();
    public static CreativeModeTab SLASHBLADE;
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(modid, path); }

    public SlashBlade(IEventBus modBus) {
        SBData.register(modBus);
        modBus.addListener(RegistryEvents::register);
        modBus.addListener(NetworkManager::register);
        modBus.addListener(this::setup);
        NeoForge.EVENT_BUS.addListener(SBItems::attributes);
        if (FMLEnvironment.getDist().isClient()) mods.flammpfeil.slashblade.client.SlashBladeClient.register(modBus);
    }

    private void setup(FMLCommonSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(KnockBackHandler::onLivingKnockBack);
        FallHandler.getInstance().register();
        LockOnManager.getInstance().register();
        Guard.getInstance().register();
        NeoForge.EVENT_BUS.register(new CapabilityAttachHandler());
        NeoForge.EVENT_BUS.register(new StunManager());
        AnvilCrafting.getInstance().register();
        RefineHandler.getInstance().register();
        KillCounter.getInstance().register();
        RankPointHandler.getInstance().register();
        AllowFlightOverrwrite.getInstance().register();
        BlockPickCanceller.getInstance().register();
        NeoForge.EVENT_BUS.addListener(TargetSelector::onInputChange);
        SummonedSwordArts.getInstance().register();
        SlayerStyleArts.getInstance().register();
        Untouchable.getInstance().register();
        EnemyStep.getInstance().register();
        KickJump.getInstance().register();
        PlacePreviewEntryPoint.getInstance().register();
        BladeMotionEventBroadcaster.getInstance().register();
    }

    public static class RegistryEvents {
        public static final Identifier BladeItemEntityLoc = entityId(BladeItemEntity.class);
        public static final Identifier BladeStandEntityLoc = entityId(BladeStandEntity.class);
        public static final Identifier SummonedSwordLoc = entityId(EntityAbstractSummonedSword.class);
        public static final Identifier SpiralSwordsLoc = entityId(EntitySpiralSwords.class);
        public static final Identifier StormSwordsLoc = entityId(EntityStormSwords.class);
        public static final Identifier BlisteringSwordsLoc = entityId(EntityBlisteringSwords.class);
        public static final Identifier HeavyRainSwordsLoc = entityId(EntityHeavyRainSwords.class);
        public static final Identifier JudgementCutLoc = entityId(EntityJudgementCut.class);
        public static final Identifier SlashEffectLoc = entityId(EntitySlashEffect.class);
        public static final Identifier PlacePreviewEntityLoc = entityId(PlacePreviewEntity.class);
        public static EntityType<BladeItemEntity> BladeItem;
        public static EntityType<BladeStandEntity> BladeStand;
        public static EntityType<EntityAbstractSummonedSword> SummonedSword;
        public static EntityType<EntitySpiralSwords> SpiralSwords;
        public static EntityType<EntityStormSwords> StormSwords;
        public static EntityType<EntityBlisteringSwords> BlisteringSwords;
        public static EntityType<EntityHeavyRainSwords> HeavyRainSwords;
        public static EntityType<EntityJudgementCut> JudgementCut;
        public static EntityType<EntitySlashEffect> SlashEffect;
        public static EntityType<PlacePreviewEntity> PlacePreview;
        public static Identifier SWORD_SUMMONED;

        private static Identifier entityId(Class<? extends Entity> type) {
            return id(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, type.getSimpleName()).replace("entity_", ""));
        }
        private static <T extends Entity> EntityType<T> build(Identifier id, EntityType.EntityFactory<T> factory, float width, float height, int interval) {
            return EntityType.Builder.of(factory, MobCategory.MISC).sized(width, height).clientTrackingRange(10)
                .updateInterval(interval).setShouldReceiveVelocityUpdates(false).build(ResourceKey.create(Registries.ENTITY_TYPE, id));
        }
        public static void register(RegisterEvent event) {
            SBItems.register(event);
            event.register(Registries.ENTITY_TYPE, helper -> {
                helper.register(BladeItemEntityLoc, BladeItem = build(BladeItemEntityLoc, BladeItemEntity::new, .25F, .25F, 20));
                helper.register(BladeStandEntityLoc, BladeStand = build(BladeStandEntityLoc, BladeStandEntity::new, .5F, .5F, 20));
                helper.register(SummonedSwordLoc, SummonedSword = build(SummonedSwordLoc, EntityAbstractSummonedSword::new, .5F, .5F, 20));
                helper.register(SpiralSwordsLoc, SpiralSwords = build(SpiralSwordsLoc, EntitySpiralSwords::new, .5F, .5F, 20));
                helper.register(StormSwordsLoc, StormSwords = build(StormSwordsLoc, EntityStormSwords::new, .5F, .5F, 20));
                helper.register(BlisteringSwordsLoc, BlisteringSwords = build(BlisteringSwordsLoc, EntityBlisteringSwords::new, .5F, .5F, 20));
                helper.register(HeavyRainSwordsLoc, HeavyRainSwords = build(HeavyRainSwordsLoc, EntityHeavyRainSwords::new, .5F, .5F, 20));
                helper.register(JudgementCutLoc, JudgementCut = build(JudgementCutLoc, EntityJudgementCut::new, .5F, .5F, 20));
                helper.register(SlashEffectLoc, SlashEffect = build(SlashEffectLoc, EntitySlashEffect::new, .5F, .5F, 20));
                helper.register(PlacePreviewEntityLoc, PlacePreview = build(PlacePreviewEntityLoc, PlacePreviewEntity::new, .5F, .5F, 20));
            });
            event.register(Registries.CUSTOM_STAT, helper -> {
                SWORD_SUMMONED = id("sword_summoned");
                helper.register(SWORD_SUMMONED, SWORD_SUMMONED);
                Stats.CUSTOM.get(SWORD_SUMMONED, StatFormatter.DEFAULT);
            });
            event.register(Registries.CREATIVE_MODE_TAB, helper -> {
                SLASHBLADE = CreativeModeTab.builder().title(Component.translatable(modid))
                    .icon(() -> SBItems.yamatoIcon()).displayItems((parameters, output) -> SBItems.displayItems(output)).build();
                helper.register(id("slashblade"), SLASHBLADE);
            });
        }
    }
}
''', 'utf-8')

(root / 'init/SBItems.java').write_text('''package mods.flammpfeil.slashblade.init;

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
            if (getId() == proudsoul.getId() && !(entity instanceof mods.flammpfeil.slashblade.entity.BladeItemEntity)) {
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
                @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }
                @Override public ItemStack getCraftingRemainingItem(ItemStack stack) { return new ItemStack(proudsoul_trapezohedron); }
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
''', 'utf-8')

(root / 'item/ItemTierSlashBlade.java').write_text('''package mods.flammpfeil.slashblade.item;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.*;
import net.minecraft.world.item.*;
/** The upstream blade material: 100 durability, no mining speed, enchantability 10. */
public final class ItemTierSlashBlade {
    public ToolMaterial material() {
        return new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 100, 0, 0, 10,
            ItemTags.create(Identifier.fromNamespaceAndPath("slashblade", "proudsouls")));
    }
}
''', 'utf-8')

(root / 'event/CapabilityAttachHandler.java').write_text('''package mods.flammpfeil.slashblade.event;
import mods.flammpfeil.slashblade.compat.SBData;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
/** Initializes independent attachments for each living entity entering a level. */
public final class CapabilityAttachHandler {
    @SubscribeEvent public void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity entity) {
            entity.getData(SBData.INPUT);
            entity.getData(SBData.EFFECT);
            entity.getData(SBData.RANK);
        }
    }
}
''', 'utf-8')

block = root / 'block/SBBlocks.java'
block.write_text(block.read_text('utf-8').replace('import net.neoforged.neoforge.registries.ObjectHolder;', ''), 'utf-8')
print('Registered all 15 upstream items, 10 entities, custom stat and creative tab')
