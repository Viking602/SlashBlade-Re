package mods.flammpfeil.slashblade;

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
        mods.flammpfeil.slashblade.compat.SBRecipes.register(modBus);
        modBus.addListener(RegistryEvents::register);
        modBus.addListener(NetworkManager::register);
        modBus.addListener(mods.flammpfeil.slashblade.verification.PortGameTests::register);
        modBus.addListener(mods.flammpfeil.slashblade.verification.CombatGameTests::register);
        modBus.addListener(this::setup);
        NeoForge.EVENT_BUS.addListener(SBItems::attributes);
        NeoForge.EVENT_BUS.addListener(mods.flammpfeil.slashblade.compat.SBItemData::tagsUpdated);
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
        NeoForge.EVENT_BUS.register(new mods.flammpfeil.slashblade.event.ResharpedProgression());
        RankPointHandler.getInstance().register();
        AllowFlightOverrwrite.getInstance().register();
        NeoForge.EVENT_BUS.addListener(TargetSelector::onInputChange);
        SummonedSwordArts.getInstance().register();
        NeoForge.EVENT_BUS.register(SuperSlashArts.getInstance());
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
                SLASHBLADE = CreativeModeTab.builder().title(Component.translatable("itemGroup." + modid))
                    .icon(() -> SBItems.yamatoIcon()).displayItems((parameters, output) -> SBItems.displayItems(output)).build();
                helper.register(id("slashblade"), SLASHBLADE);
            });
        }
    }
}
