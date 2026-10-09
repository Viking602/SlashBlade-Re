from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
j=root/'src/main/java/mods/flammpfeil/slashblade'
for p in j.rglob('*.java'):
    s=p.read_text(encoding='utf-8')
    changes={
        'import net.minecraft.world.entity.projectile.AbstractArrow;':'import net.minecraft.world.entity.projectile.arrow.AbstractArrow;',
        'import net.neoforged.neoforge.common.ForgeMod;':'import net.minecraft.world.entity.ai.attributes.Attributes;',
        'ForgeMod.ENTITY_REACH.get()':'Attributes.ENTITY_INTERACTION_RANGE',
        'ForgeMod.ENTITY_GRAVITY.get()':'Attributes.GRAVITY',
        'MobEffects.DAMAGE_BOOST':'MobEffects.STRENGTH',
        'MobEffects.MOVEMENT_SLOWDOWN':'MobEffects.SLOWNESS',
        'Enchantments.FALL_PROTECTION':'Enchantments.FEATHER_FALLING',
        'SoundEvents.TRIDENT_THROW,':'SoundEvents.TRIDENT_THROW.value(),',
        'new AttributeModifier("SweepingDamageRatio",':'new AttributeModifier(mods.flammpfeil.slashblade.SlashBlade.id("sweeping_damage_ratio"),',
        'new AttributeModifier("RankDamageBonus",':'new AttributeModifier(mods.flammpfeil.slashblade.SlashBlade.id("rank_damage_bonus"),',
        '.level().random.nextFloat()':'.level().getRandom().nextFloat()',
        'import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;':'import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;',
        '(AnvilRepairEvent event)':'(AnvilCraftEvent.Post event)',
        'event.setCost(':'event.setXpCost(',
        'base.getItem().isValidRepairItem(base,material)':'base.isValidRepairItem(material)',
        'material.getEnchantmentValue()':'java.util.Optional.ofNullable(material.get(net.minecraft.core.component.DataComponents.ENCHANTABLE)).map(net.minecraft.world.item.enchantment.Enchantable::value).orElse(0)',
        'TargetSelector.lockon_focus.test(player,':'TargetSelector.lockon_focus.test(player.level(), player,',
        'TargetSelector.lockon_focus.test(sender,':'TargetSelector.lockon_focus.test((net.minecraft.server.level.ServerLevel)sender.level(), sender,',
        'Minecraft.getInstance().getFrameTime()':'Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true)',
        'base.getDescriptionId()':'base.getItem().getDescriptionId(base)',
        'blade.getDescriptionId()':'blade.getItem().getDescriptionId(blade)',
        'import net.neoforged.neoforge.registries.ForgeRegistries;':'import net.minecraft.core.registries.BuiltInRegistries;',
        'ForgeRegistries.ITEMS.getKey':'BuiltInRegistries.ITEM.getKey',
        'EnchantmentHelper.getEnchantmentLevel(Enchantments.':'mods.flammpfeil.slashblade.compat.SBEnchantments.livingLevel(Enchantments.',
    }
    for old,new in changes.items(): s=s.replace(old,new)
    s=re.sub(r'startRiding\(([^;\n]+),\s*true\)',r'startRiding(\1, true, true)',s)
    # NeoForge's old canUpdate flag was removed; vanilla dispatch already controls passenger ticking.
    s=s.replace('if (canUpdate())', 'if (!isRemoved())')
    if p.name in ('IMobEffectState.java','MobEffectState.java'):
        s=s.replace('import net.minecraft.world.effect.MobEffect;', 'import net.minecraft.world.effect.MobEffect;\nimport net.minecraft.core.Holder;')
        s=s.replace('Set<MobEffect>', 'Set<Holder<MobEffect>>').replace('Collection<MobEffect>', 'Collection<Holder<MobEffect>>')
    if p.name=='Untouchable.java':
        s=s.replace('import net.minecraft.world.effect.MobEffect;', 'import net.minecraft.world.effect.MobEffect;\nimport net.minecraft.core.Holder;')
        s=s.replace('List<MobEffect>','List<Holder<MobEffect>>').replace('p.isBeneficial()', 'p.value().isBeneficial()')
        a=s.index('    @SubscribeEvent\n    public void onLivingAttack'); b=s.index('    @SubscribeEvent\n    public void onLivingDeath',a)
        s=s[:a]+s[b:]
        s=s.replace('public void onLivingDamage(LivingDamageEvent event)', 'public void onLivingDamage(LivingDamageEvent.Pre event)')
        a=s.index('    public void onLivingDamage'); b=s.index('    @SubscribeEvent',a)
        s=s[:a]+s[a:b].replace('event.setCanceled(true)', 'event.setNewDamage(0)')+s[b:]
        s=s.replace('if(ef.getStoredHealth() < storedHealth)', 'if(entity.getHealth() < storedHealth)')
    if p.name=='Projectile.java': s=s.replace('this.setOwner(null)', 'this.setOwner((net.minecraft.world.entity.Entity)null)')
    if p.name=='EntitySlashEffect.java': s=s.replace('ClipContext.Fluid.NONE,\n                            null', 'ClipContext.Fluid.NONE,\n                            (net.minecraft.world.entity.Entity)null')
    if p.name=='TNTExtinguisher.java': s=s.replace('world.getGameRules().getBooleanOr(GameRules.RULE_DOMOBLOOT, false)', '((net.minecraft.server.level.ServerLevel)world).getGameRules().get(GameRules.MOB_DROPS)')
    if p.name=='EnemyStep.java': s=s.replace('worldIn.getNearbyEntities(', '((net.minecraft.server.level.ServerLevel)worldIn).getNearbyEntities(')
    if p.name=='AllowFlightOverrwrite.java': s=s.replace('event.getServer().setFlightAllowed(true);', 'if (event.getServer() instanceof net.minecraft.server.dedicated.DedicatedServer server) server.setAllowFlight(true);')
    if p.name in ('BladeStandEntity.java','PlacePreviewEntity.java'):
        s=s.replace('interact(Player player, InteractionHand hand)', 'interact(Player player, InteractionHand hand, net.minecraft.world.phys.Vec3 location)')
        s=s.replace('super.interact(player, hand)', 'super.interact(player, hand, location)')
    if p.name=='BladeItemEntity.java':
        a=s.index('        CompoundTag compoundnbt = this.saveWithoutId'); b=s.index('\n    }',a)
        s=s[:a]+'        this.health = 100;\n        this.setUnlimitedLifetime();'+s[b:]
        s=s.replace('causeFallDamage(float distance,', 'causeFallDamage(double distance,')
    if p.name=='BladeStandItem.java':
        a=s.index('            CompoundTag compoundnbt ='); b=s.index('            if (hangingentity.survives())',a)
        s=s[:a]+'            EntityType.<HangingEntity>createDefaultStackConfig(world, itemstack, playerentity).accept(hangingentity);\n\n'+s[b:]
        s=s.replace('InteractionResult.sidedSuccess(world.isClientSide())', 'InteractionResult.SUCCESS')
    if p.name=='MixinBlockBehaviour.java':
        s=s.replace('Blocks.SCAFFOLDING.getCollisionShape(Blocks.SCAFFOLDING.defaultBlockState(),', 'Blocks.SCAFFOLDING.defaultBlockState().getCollisionShape(')
        s=s.replace('Blocks.SCAFFOLDING.getVisualShape(Blocks.SCAFFOLDING.defaultBlockState(),', 'Blocks.SCAFFOLDING.defaultBlockState().getVisualShape(')
        s=s.replace('remap = true', 'remap = false').replace('@Mixin(BlockBehaviour.BlockStateBase.class)', '@Mixin(value = BlockBehaviour.BlockStateBase.class, remap = false)')
    if p.name=='SwordType.java': s=s.replace('!itemStack.getEnchantmentTags().isEmpty()', 'itemStack.isEnchanted()')
    if p.name=='BladeStateCapabilityProvider.java':
        s=s.replace('"ComboRootAir", Optional.ofNullable(instance.getComboRoot())', '"ComboRootAir", Optional.ofNullable(instance.getComboRootAir())')
        s=s.replace('getIntOr("TargetEntity", 0)', 'getIntOr("TargetEntity", -1)')
    p.write_text(s,encoding='utf-8')
p=j/'compat/SBEnchantments.java'
s=p.read_text(encoding='utf-8')
a=s.rindex('}')
s=s[:a]+'''    public static int livingLevel(net.minecraft.resources.ResourceKey<Enchantment> key, net.minecraft.world.entity.LivingEntity entity) {
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentLevel(entity.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(key), entity);
    }
'''+s[a:]
p.write_text(s,encoding='utf-8')
p=j/'util/AdvancementHelper.java'
s=p.read_text(encoding='utf-8').replace('import net.minecraft.advancements.Advancement;', 'import net.minecraft.advancements.AdvancementHolder;')
s=s.replace('Advancement adv = player.getServer()', 'AdvancementHolder adv = player.level().getServer()')
s=s.replace('grantedIf(Enchantment enchantment,', 'grantedIf(net.minecraft.resources.ResourceKey<Enchantment> enchantment,')
s=s.replace('EnchantmentHelper.getEnchantmentLevel(enchantment, owner)', 'mods.flammpfeil.slashblade.compat.SBEnchantments.livingLevel(enchantment, owner)')
s=s.replace('BuiltInRegistries.ENCHANTMENT.getKey(enchantment).getPath()', 'enchantment.identifier().getPath()')
p.write_text(s,encoding='utf-8')
p=j/'event/MoveInputHandler.java'
s=p.read_text(encoding='utf-8')
for old,new in [('up','forward()'),('down','backward()'),('left','left()'),('right','right()'),('shiftKeyDown','shift()')]:
    s=s.replace('player.input.'+old, 'player.input.keyPresses.'+new)
s=s.replace('import net.neoforged.neoforge.network.PacketDistributor;', 'import net.neoforged.neoforge.client.network.ClientPacketDistributor;').replace('PacketDistributor.sendToServer(', 'ClientPacketDistributor.sendToServer(')
s=s.replace('CompoundTag tag = new CompoundTag();\n                stack.save(tag);', 'CompoundTag tag = SBItemData.save(stack);')
# Client inventory callbacks moved to this client tick hook in 26.1.
s=s.replace('        EnumSet<InputCommand> commands =', '''        for (ItemStack held : player.getInventory()) {
            if (held.getItem() instanceof ItemSlashBlade blade) blade.tickInventory(held, player.level(), player, 0, held == player.getMainHandItem());
        }

        EnumSet<InputCommand> commands =''')
p.write_text(s,encoding='utf-8')
print('Updated common gameplay, iron anvil events, movement input and persistence')
