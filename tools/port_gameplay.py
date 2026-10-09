"""Migrate shared gameplay helpers, phased movement ticks and enchantments."""
from pathlib import Path
import re
from migrate_names import end_parenthesis, split_arguments
from migrate_storage import receiver_start
root = Path(__file__).resolve().parents[1] / 'src/main/java/mods/flammpfeil/slashblade'

def block_end(text, opening):
    depth=1; end=opening+1
    while depth:
        if text[end]=='{': depth+=1
        elif text[end]=='}': depth-=1
        end+=1
    return end

for path in root.rglob('*.java'):
    text=path.read_text('utf-8')
    text=text.replace('.moveTo(', '.snapTo(').replace('.absMoveTo(', '.snapTo(')
    text=text.replace('.getTags()', '.entityTags()').replace('.setSecondsOnFire(', '.igniteForSeconds(')
    text=text.replace('.hasImpulse', '.hurtMarked')
    text=text.replace('net.neoforged.neoforge.event.ForgeEventFactory', 'net.neoforged.neoforge.event.EventHooks')
    text=text.replace('EnchantmentHelper.getEnchantments(', 'mods.flammpfeil.slashblade.compat.SBEnchantments.map(')
    text=text.replace('EnchantmentHelper.setEnchantments(', 'mods.flammpfeil.slashblade.compat.SBEnchantments.set(')
    text=text.replace('EnchantmentHelper.getItemEnchantmentLevel(', 'mods.flammpfeil.slashblade.compat.SBEnchantments.level(')
    text=text.replace('Map<Enchantment,', 'Map<net.minecraft.core.Holder<Enchantment>,').replace('Map.Entry<Enchantment,', 'Map.Entry<net.minecraft.core.Holder<Enchantment>,')
    text=text.replace('Enchantment key = srcEntry.getKey();', 'net.minecraft.core.Holder<Enchantment> key = srcEntry.getKey();')
    pos=0
    while (match:=re.search(r'\.playNotifySound\(', text[pos:])):
        start=pos+match.start(); opening=pos+match.end()-1; end=end_parenthesis(text,opening)
        begin=receiver_start(text,start)
        replacement=f'mods.flammpfeil.slashblade.compat.SBEffects.notifySound({text[begin:start]}, {text[opening+1:end]})'
        text=text[:begin]+replacement+text[end+1:]; pos=begin+len(replacement)
    text=text.replace('PotionUtils.getAllEffects(this.getPersistentData())', 'mods.flammpfeil.slashblade.compat.SBEffects.effects(this.getPersistentData(), this.level().registryAccess())')
    text=text.replace('import net.minecraft.world.item.alchemy.PotionUtils;', '')
    text=text.replace('MobEffect effect = effectinstance.getEffect();', 'var effect = effectinstance.getEffect();')
    text=text.replace('effect.isInstantenous()', 'effect.value().isInstantenous()')
    text=text.replace('effect.applyInstantenousEffect(this, this.getShooter(),', 'effect.value().applyInstantenousEffect((net.minecraft.server.level.ServerLevel)this.level(), this, this.getShooter(),')
    if path.name in ('EntityAbstractSummonedSword.java','EntityJudgementCut.java','EntitySlashEffect.java'):
        text=text.replace('    @Override\n    @OnlyIn(Dist.CLIENT)\n    public void lerpTo(', '    @OnlyIn(Dist.CLIENT)\n    public void lerpTo(')
        text=text.replace('public void lerpMotion(double x, double y, double z) {', 'public void lerpMotion(Vec3 movement) {\n        double x = movement.x, y = movement.y, z = movement.z;')
        text=text.replace('this.checkInsideBlocks();', 'this.applyEffectsFromBlocks(this.position(), this.position());')
        text=text.replace('EnchantmentHelper.doPostHurtEffects(targetLivingEntity, shooter);\n                    EnchantmentHelper.doPostDamageEffects((LivingEntity)shooter, targetLivingEntity);',
            'EnchantmentHelper.doPostAttackEffects((ServerLevel)this.level(), targetLivingEntity, damagesource);')
    if path.name=='EntityHeavyRainSwords.java':
        text=text.replace('mobeffectinstance.save(new CompoundTag())', 'mods.flammpfeil.slashblade.compat.SBEffects.saveEffect(mobeffectinstance, this.level().registryAccess())')
    if path.name in ('SlayerStyleArts.java','KickJump.java'):
        start=text.index('    @SubscribeEvent\n    public void onTick(')
        opening=text.index('{',start); end=block_end(text,opening)
        old=text[start:end]
        match=re.search(r'case START\s*->\s*\{',old); opening=match.end()-1; ending=block_end(old,opening)
        pre=old[opening+1:ending-1]
        post=''
        if (match:=re.search(r'case END\s*->\s*\{',old)):
            opening=match.end()-1; ending=block_end(old,opening); post=old[opening+1:ending-1]
        replacement='    @SubscribeEvent\n    public void onTickPre(net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre event) {'+pre+'\n    }\n'
        if post:
            replacement+='    @SubscribeEvent\n    public void onTickPost(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {'+post+'\n    }'
        text=text[:start]+replacement+text[end:]
        text=text.replace('event.getEntity().setMaxUpStep(', 'event.getEntity().getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(')
    if path.name=='SlayerStyleArts.java':
        start=text.index('    private static void executeTeleport('); opening=text.index('{',start); end=block_end(text,opening)
        replacement='''    private static void executeTeleport(Entity entity, LivingEntity target) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        Vec3 destination = target.position().add(0, target.getBbHeight() / 2.0, 0).add(entity.getLookAngle().scale(-2));
        if (!Level.isInSpawnableBounds(BlockPos.containing(destination))) return;
        entity.stopRiding();
        if (entity instanceof ServerPlayer player && player.isSleeping()) player.stopSleepInBed(true, true);
        entity.teleportTo(level, destination.x, destination.y, destination.z, java.util.Set.of(), entity.getYRot(), entity.getXRot(), true);
        entity.setYHeadRot(entity.getYRot());
        if (!(entity instanceof LivingEntity living) || !living.isFallFlying()) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(1,0,1));
            entity.setOnGround(false);
        }
        if (entity instanceof PathfinderMob mob) mob.getNavigation().stop();
    }'''
        text=text[:start]+replacement+text[end:]
        text=text.replace('import net.minecraft.world.entity.RelativeMovement;', '')
    path.write_text(text,'utf-8')

path=root/'util/TargetSelector.java'; text=path.read_text('utf-8')
text=text.replace('net.minecraft.world.entity.animal.Wolf', 'net.minecraft.world.entity.animal.wolf.Wolf')
text=text.replace('import net.neoforged.neoforge.common.ForgeMod;', '')
text=text.replace('.selector(new AttackablePredicate())', '.selector((target, level) -> new AttackablePredicate().test(target))')
text=text.replace('public boolean test(@Nullable LivingEntity attacker, LivingEntity target)', 'public boolean test(ServerLevel level, @Nullable LivingEntity attacker, LivingEntity target)')
text=text.replace('super.test(attacker, target)', 'super.test(level, attacker, target)')
text=text.replace('predicate.test(attacker,', 'predicate.test((ServerLevel)world, attacker,').replace('predicate.test(user,', 'predicate.test((ServerLevel)world, user,')
text=text.replace('ForgeMod.ENTITY_REACH.get()', 'net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE')
text=text.replace('sw.sendParticles(sender, ParticleTypes.ANGRY_VILLAGER, false,', 'sw.sendParticles(sender, ParticleTypes.ANGRY_VILLAGER, false, false,')
path.write_text(text,'utf-8')

path=root/'init/SBItems.java'; text=path.read_text('utf-8')
text=text.replace('                @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }\n', '')
text=text.replace('@Override public ItemStack getCraftingRemainingItem(ItemStack stack) { return new ItemStack(proudsoul_trapezohedron); }',
    '@Override public ItemStackTemplate getCraftingRemainder(ItemInstance stack) { return ItemStackTemplate.fromNonEmptyStack(new ItemStack(proudsoul_trapezohedron)); }')
path.write_text(text,'utf-8')
print('Ported enchantments, potion effects, movement phases and teleport API')
