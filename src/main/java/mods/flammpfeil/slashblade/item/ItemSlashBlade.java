package mods.flammpfeil.slashblade.item;

import mods.flammpfeil.slashblade.compat.SBItemData;
import mods.flammpfeil.slashblade.compat.SBData;
import mods.flammpfeil.slashblade.compat.StateKey;
import com.google.common.collect.*;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.capability.inputstate.InputState;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.BladeItemEntity;
import mods.flammpfeil.slashblade.event.AnvilCraftingRecipe;
import mods.flammpfeil.slashblade.event.BladeMaterialTooltips;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.util.NBTHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import mods.flammpfeil.slashblade.compat.LazyOptional;

import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import net.minecraft.world.item.Item.Properties;
import org.jetbrains.annotations.NotNull;

public class ItemSlashBlade extends Item {
    protected static final UUID ATTACK_DAMAGE_AMPLIFIER = UUID.fromString("2D988C13-595B-4E58-B254-39BB6FA077FD");
    protected static final UUID PLAYER_REACH_AMPLIFIER = UUID.fromString("2D988C13-595B-4E58-B254-39BB6FA077FE");

    public static final StateKey<ISlashBladeState> BLADESTATE = StateKey.of(ISlashBladeState.class);
    public static final StateKey<IInputState> INPUT_STATE = StateKey.of(IInputState.class);

    public ItemSlashBlade(ItemTierSlashBlade tier, int attackDamageIn, float attackSpeedIn, Properties builder) {
        super(builder.sword(tier.material(), attackDamageIn, attackSpeedIn));
    }



    @Override public boolean supportsEnchantment(ItemStack stack,net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        return extraEnchantment(enchantment) || new ItemStack(net.minecraft.world.item.Items.IRON_SWORD).supportsEnchantment(enchantment) || super.supportsEnchantment(stack,enchantment);
    }
    @Override public boolean isPrimaryItemFor(ItemStack stack,net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        return extraEnchantment(enchantment) || new ItemStack(net.minecraft.world.item.Items.IRON_SWORD).isPrimaryItemFor(enchantment) || super.isPrimaryItemFor(stack,enchantment);
    }
    private static boolean extraEnchantment(net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        return enchantment.is(net.minecraft.world.item.enchantment.Enchantments.SOUL_SPEED) || enchantment.is(net.minecraft.world.item.enchantment.Enchantments.POWER) || enchantment.is(net.minecraft.world.item.enchantment.Enchantments.FEATHER_FALLING) || enchantment.is(net.minecraft.world.item.enchantment.Enchantments.FIRE_PROTECTION) || enchantment.is(net.minecraft.world.item.enchantment.Enchantments.THORNS);
    }
    public Rarity getRarity(ItemStack stack) {
        return SBData.get(stack, BLADESTATE).map(ISlashBladeState::getRarity).orElse(Rarity.COMMON);
    }


    @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return 72000; }
    public int getUseDuration(ItemStack stack) { return 72000; }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        SBData.get(stack, BLADESTATE).ifPresent(state -> {
            SBData.get(player, INPUT_STATE).ifPresent(input -> input.getCommands().add(InputCommand.R_CLICK));
            try {
                ComboState combo = state.progressCombo(player);
                if (combo != ComboState.NONE) player.swing(hand);
            } finally { SBData.get(player, INPUT_STATE).ifPresent(input -> input.getCommands().remove(InputCommand.R_CLICK)); }
        });
        player.startUsingItem(hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack itemstack, Player playerIn, Entity entity) {

        Level worldIn = playerIn.level();

        Optional<ISlashBladeState> stateHolder = SBData.get(itemstack, BLADESTATE)
                .filter((state) -> !state.onClick());

        stateHolder.ifPresent((state) -> {
            SBData.get(playerIn, INPUT_STATE).ifPresent((s)->s.getCommands().add(InputCommand.L_CLICK));

            ComboState combo = state.progressCombo(playerIn);

            SBData.get(playerIn, INPUT_STATE).ifPresent((s)->s.getCommands().remove(InputCommand.L_CLICK));
        });

        return stateHolder.isPresent();
    }

    static public final String BREAK_ACTION_TIMEOUT = "BreakActionTimeout";

    static public Consumer<LivingEntity> getOnBroken(ItemStack stack){
        return (user)->{
            user.onEquippedItemBroken(stack.getItem(), user.getUsedItemHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);

            var state = SBData.get(stack,BLADESTATE).orElseThrow(IllegalStateException::new);
            if(net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new mods.flammpfeil.slashblade.event.SlashBladeEvent.BreakEvent(stack,state)).isCanceled())return;
            if (stack.isEnchanted() && user.level() instanceof ServerLevel level) {
                var pool=level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).listElements()
                    .filter(e -> stack.supportsEnchantment(e) && !mods.flammpfeil.slashblade.SlashBladeConfig.NON_DROPPABLE_ENCHANTMENT.get().contains(e.key().identifier().toString())).toList();
                int count=Math.max(1,Math.min(mods.flammpfeil.slashblade.SlashBladeConfig.MAX_ENCHANTED_PROUDSOUL_DROP.get(),state.getProudSoulCount()/100));
                for (int i=0;i<count && !pool.isEmpty();i++) {
                    var tiny=new ItemStack(SBItems.proudsoul_tiny);
                    tiny.enchant(pool.get(user.getRandom().nextInt(pool.size())),1);
                    var drop=new ItemEntity(level,user.getX(),user.getY(),user.getZ(),tiny);
                    drop.setDefaultPickUpDelay();level.addFreshEntity(drop);
                    state.setProudSoulCount(state.getProudSoulCount()-100);
                }
            }
            int count=Math.max(1,Math.min(mods.flammpfeil.slashblade.SlashBladeConfig.MAX_PROUDSOUL_DROP.get(),state.getProudSoulCount()/100));
            ItemStack soul = new ItemStack(SBItems.proudsoul_tiny,count);
            state.setProudSoulCount(state.getProudSoulCount()-count*100);

            ItemEntity itementity = new ItemEntity(user.level(), user.getX(), user.getY() , user.getZ(), soul);
            BladeItemEntity e = new BladeItemEntity(SlashBlade.RegistryEvents.BladeItem, user.level()){

                static final String isReleased = "isReleased";
                @Override
                public boolean causeFallDamage(double distance, float damageMultiplier, DamageSource ds) {

                    CompoundTag tag = this.getPersistentData();

                    if(!tag.getBooleanOr(isReleased, false)){
                        this.getPersistentData().putBoolean(isReleased, true);

                        if(this.level() instanceof ServerLevel){
                            Entity thrower = getOwner();

                            if (thrower != null) {
                                thrower.getPersistentData().remove(BREAK_ACTION_TIMEOUT);
                            }
                        }
                    }

                    return super.causeFallDamage(distance, damageMultiplier, ds);
                }
            };

            e.restoreFrom(itementity);
            e.init();
            e.push(0,0.4,0);

            e.setPickUpDelay(20*2);
            e.setGlowingTag(true);

            e.setAirSupply(-1);

            e.setThrower(user);

            user.level().addFreshEntity(e);

            user.getPersistentData().putLong(BREAK_ACTION_TIMEOUT, user.level().getGameTime() + 20*5);

            SBData.get(stack, ItemSlashBlade.BLADESTATE).ifPresent(brokenState->{
                if(0 < brokenState.getRefine()){
                    brokenState.setRefine(brokenState.getRefine() - 1);
                    brokenState.doBrokenAction(user);
                }
            });
        };
    }

    @Override
    public void hurtEnemy(ItemStack stackF, LivingEntity target, LivingEntity attacker) {

        ItemStack stack = attacker.getMainHandItem();

        SBData.get(stack, BLADESTATE).ifPresent((state)->{
            if(net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new mods.flammpfeil.slashblade.event.SlashBladeEvent.HitEvent(stack,state,target,attacker)).isCanceled())return;
            state.resolvCurrentComboState(attacker).hitEffect(target, attacker);

            state.damageBlade(stack, 1, attacker, this.getOnBroken(stack));

        });


    }
    public boolean mineBlock(ItemStack stack, Level worldIn, BlockState state, BlockPos pos, LivingEntity entityLiving) {

        if (state.getDestroySpeed(worldIn, pos) != 0.0F) {
            SBData.get(stack, BLADESTATE).ifPresent((s)->{
                s.damageBlade(stack, 1, entityLiving, this.getOnBroken(stack));
            });
        }

        return true;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level worldIn, LivingEntity entityLiving, int timeLeft) {
        int elapsed = this.getUseDuration(stack) - timeLeft;

        if (!worldIn.isClientSide()) {

            SBData.get(stack, BLADESTATE).ifPresent((state) -> {

                ComboState sa = state.doChargeAction(entityLiving, elapsed);

                //sa.tickAction(entityLiving);
                if (sa != ComboState.NONE){
                    boolean creative=entityLiving instanceof Player player && player.getAbilities().instabuild;
                    if(!creative && state.getProudSoulCount()>=state.getSlashArts().getProudSoulCost()) state.setProudSoulCount(state.getProudSoulCount()-state.getSlashArts().getProudSoulCost());
                    else if(!creative) state.damageBlade(stack, 1, entityLiving, this.getOnBroken(stack));
                    entityLiving.swing(InteractionHand.MAIN_HAND);
                }
            });
        }
        return true;
    }

    @Override
    public void onUseTick(Level level, LivingEntity player, ItemStack stack, int count) {
        SBData.get(stack, BLADESTATE).ifPresent((state)->{
            if(net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new mods.flammpfeil.slashblade.event.SlashBladeEvent.ChargeActionEvent(player,player.getTicksUsingItem(),state)).isCanceled())return;
            state.getComboSeq().holdAction(player);

            if(!player.level().isClientSide()){
                int ticks = player.getTicksUsingItem();
                if(0 < ticks){

                    if (ticks == state.getFullChargeTicks(player)) {
                        Vec3 pos = player.getEyePosition(1.0f).add(player.getLookAngle());
                        ((ServerLevel)player.level()).sendParticles(ParticleTypes.PORTAL,pos.x,pos.y,pos.z, 7, 0.7,0.7,0.7, 0.02);
                    }
                }
            }
        });
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel worldIn, Entity entityIn, @Nullable EquipmentSlot slot) {
        tickInventory(stack, worldIn, entityIn, slot == EquipmentSlot.MAINHAND);
    }

    public void tickInventory(ItemStack stack, Level worldIn, Entity entityIn, boolean isSelected) {
        var updateState=SBData.get(stack,BLADESTATE).orElseThrow(IllegalStateException::new);
        if(net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new mods.flammpfeil.slashblade.event.SlashBladeEvent.UpdateEvent(stack,updateState,worldIn,entityIn,-1,isSelected)).isCanceled())return;


        if(!isSelected) {
            if(entityIn instanceof Player player) repairUnequipped(stack,player);
            return;
        }

        if(stack == null)
            return;
        if(entityIn == null)
            return;

        SBData.get(stack, BLADESTATE).ifPresent((state)->{
            if(entityIn instanceof LivingEntity){

                SBData.get(entityIn, INPUT_STATE).ifPresent(mInput->{
                    mInput.getScheduler().onTick((LivingEntity) entityIn);
                });

                /*
                if(0.5f > state.getDamage())
                    state.setDamage(0.99f);
                */

                var living=(LivingEntity)entityIn;
                var resolved=state.resolvCurrentComboState(living);
                // Timed SA follow-ups must execute their entry hit exactly once, too.
                resolved=mods.flammpfeil.slashblade.slasharts.ResharpedCombos.enterTimedSegment(living,state,resolved);
                resolved.tickAction(living);
                state.sendChanges(entityIn);
            }
        });
    }

    public static void repairUnequipped(ItemStack stack,Player player) {
        if(!mods.flammpfeil.slashblade.SlashBladeConfig.SELF_REPAIR_ENABLE.get())return;
        var state=SBData.get(stack,BLADESTATE).orElseThrow(IllegalStateException::new);
        boolean hunger=mods.flammpfeil.slashblade.SlashBladeConfig.HUNGER_CAN_REPAIR.get() && player.hasEffect(MobEffects.HUNGER);
        if((!hunger && !SwordType.from(stack).contains(SwordType.Bewitched)) || state.getDamage()<=0 || player.getFoodData().getFoodLevel()<=0)return;
        int amount=hunger?1+player.getEffect(MobEffects.HUNGER).getAmplifier():1;
        int cost=(int)Math.min(Integer.MAX_VALUE,(long)amount*mods.flammpfeil.slashblade.SlashBladeConfig.BEWITCHED_EXP_COST.get());
        if(mods.flammpfeil.slashblade.SlashBladeConfig.SELF_REPAIR_COST_EXP.get()) {
            if(player.experienceLevel<cost)return;
            player.giveExperiencePoints(-cost);
        }
        player.causeFoodExhaustion(amount*mods.flammpfeil.slashblade.SlashBladeConfig.BEWITCHED_HUNGER_EXHAUSTION.get().floatValue());
        state.setDamage(state.getDamage()-amount/(float)state.getMaxDamage());
    }

    public CompoundTag getShareTag(ItemStack stack) { return mods.flammpfeil.slashblade.compat.SBItemData.tag(stack); }

    public static final String ICON_TAG_KEY = "SlashBladeIcon";
    //public static final String CLIENT_CAPS_KEY = "AllCapsData";



    //damage ----------------------------------------------------------
    @Override public int getDamage(ItemStack stack) {
        return SBData.get(stack,BLADESTATE).map(s -> Math.round(s.getDamage()*s.getMaxDamage())).orElse(0);
    }
    @Override public void setDamage(ItemStack stack,int damage) {
        SBData.get(stack,BLADESTATE).ifPresent(s -> s.setDamage(damage/(float)s.getMaxDamage()));
    }
    @Override public boolean isDamaged(ItemStack stack) { return getDamage(stack)>0; }
    @Override public <T extends LivingEntity> int damageItem(ItemStack stack,int amount,@Nullable T entity,Consumer<Item> onBroken) {
        // Gameplay wear is applied in hurtEnemy/mineBlock/charge; vanilla's weapon component must not apply it a second time or delete a broken blade.
        return 0;
    }

    // GUI icons render their own diamond-shaped durability gauge in SlashBladeTEISR.
    @Override public boolean isBarVisible(ItemStack stack) { return false; }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.F - 13.0F * SBData.get(stack, BLADESTATE).map(s->s.getDamage()).orElse(0.0f));
        //return super.getDurabilityForDisplay(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        boolean isBroken = SBData.get(stack, BLADESTATE).filter(s->s.isBroken()).isPresent();

        return isBroken ? 0xFF66AE : 0x02E0EE;
    }

    public String getDescriptionId(ItemStack stack) {
        return SBData.get(stack, BLADESTATE)
                .filter((s)->!s.getTranslationKey().isEmpty())
                .map((state)->state.getTranslationKey())
                .orElseGet(()->super.getDescriptionId());
    }


    public static RecipeManager getServerRM(){
        MinecraftServer sw = ServerLifecycleHooks.getCurrentServer();
        if(sw != null)
            return sw.getRecipeManager();
        else
            return null;
    }


    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) { return repair.is(ItemTags.STONE_TOOL_MATERIALS) || repair.is(ItemTags.create(Identifier.fromNamespaceAndPath("slashblade", "proudsouls"))); }

    RangeMap refineColor = ImmutableRangeMap.builder()
            .put(Range.lessThan(10), ChatFormatting.WHITE)
            .put(Range.closedOpen(10,50), ChatFormatting.YELLOW)
            .put(Range.closedOpen(50,100), ChatFormatting.GREEN)
            .put(Range.closedOpen(100,150), ChatFormatting.AQUA)
            .put(Range.closedOpen(150,200), ChatFormatting.BLUE)
            .put(Range.atLeast(200), ChatFormatting.LIGHT_PURPLE)
            .build();


    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        SBData.get(stack, BLADESTATE).ifPresent(state -> {
            tooltip.accept(Component.translatable("slashblade.tooltip.proud_soul",state.getProudSoulCount()));
            if (state.isBroken()) tooltip.accept(Component.translatable("slashblade.tooltip.broken"));
            if (state.isSealed()) tooltip.accept(Component.translatable("slashblade.tooltip.sealed"));
            tooltip.accept(Component.translatable("slashblade.tooltip.slash_art",Component.translatable("slash_art.slashblade."+state.getSlashArts().getName())).withStyle(ChatFormatting.AQUA));
            for(var id:state.getSpecialEffects()) {
                var effect=mods.flammpfeil.slashblade.registry.SpecialEffectsRegistry.REGISTRY.get().getValue(id);
                if(effect!=null)tooltip.accept(Component.translatable(effect.getDescriptionId()).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (state.getKillCount() > 0) tooltip.accept(Component.translatable("slashblade.tooltip.killcount", state.getKillCount()));
            if (state.getRefine() > 0) tooltip.accept(Component.translatable("slashblade.tooltip.refine", state.getRefine()).withStyle((ChatFormatting)refineColor.get(state.getRefine())));
        });
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
    @Override public Component getName(ItemStack stack) { return Component.translatable(getDescriptionId(stack)); }


    /**
     * @return true = cancel : false = swing
     */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        return !SBData.get(stack, BLADESTATE).filter(s->s.getLastActionTime() == entity.level().getGameTime()).isPresent();
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    @Nullable
    @Override
    public Entity createEntity(Level world, Entity location, ItemStack itemstack) {
        // /give creates a visual-only item with one tick left. Keeping that entity
        // immortal would leave a permanent unpickable blade beside the player.
        if (location instanceof ItemEntity original && original.getAge() >= itemstack.getEntityLifespan(world) - 1) return null;
        BladeItemEntity e = new BladeItemEntity(SlashBlade.RegistryEvents.BladeItem, world);
        e.restoreFrom(location);
        e.init();
        return e;
    }

    @Override
    public int getEntityLifespan(ItemStack itemStack, Level world) {
        return super.getEntityLifespan(itemStack, world);// Short.MAX_VALUE;
    }


}
