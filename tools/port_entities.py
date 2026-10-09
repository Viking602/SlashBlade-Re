"""Port entity data builders, Value I/O persistence and complex spawn packets."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1] / 'src/main/java/mods/flammpfeil/slashblade'
for path in (root/'entity').glob('*.java'):
    text=path.read_text('utf-8')
    text=text.replace('IEntityAdditionalSpawnData', 'IEntityWithComplexSpawn')
    text=text.replace('FriendlyByteBuf buffer', 'net.minecraft.network.RegistryFriendlyByteBuf buffer')
    text=text.replace('FriendlyByteBuf additionalData', 'net.minecraft.network.RegistryFriendlyByteBuf additionalData')
    text=text.replace('PlayMessages.SpawnEntity', 'net.minecraft.network.protocol.game.ClientboundAddEntityPacket')
    text=re.sub(r'import net\.neoforged\.neoforge\.network\.(?:NetworkHooks|PlayMessages);\n', '', text)
    text=text.replace('import net.neoforged.neoforge.registries.ForgeRegistries;', 'import net.minecraft.core.registries.BuiltInRegistries;')
    text=text.replace('ForgeRegistries.ITEMS', 'BuiltInRegistries.ITEM')
    text=text.replace('protected void defineSynchedData()', 'protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder)')
    text=text.replace('super.defineSynchedData()', 'super.defineSynchedData(builder)')
    text=text.replace('this.entityData.define(', 'builder.define(')
    text=text.replace('public Packet<ClientGamePacketListener> getAddEntityPacket()', 'public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity entity)')
    text=text.replace('return NetworkHooks.getEntitySpawningPacket(this);', 'return super.getAddEntityPacket(entity);')
    for method, io_type, variable in [('addAdditionalSaveData','ValueOutput','output'), ('readAdditionalSaveData','ValueInput','input')]:
        match=re.search(r'(public|protected) void '+method+r'\(CompoundTag (\w+)\)\s*\{', text)
        if not match: continue
        tag=match.group(2); opening=match.end()-1; depth=1; end=opening+1
        while depth:
            if text[end]=='{': depth+=1
            elif text[end]=='}': depth-=1
            end+=1
        body=text[opening+1:end-1]
        body=body.replace(f'super.{method}({tag});', '')
        legacy_method='writeBladeData' if method=='addAdditionalSaveData' else 'readBladeData'
        key='slashblade:'+re.sub(r'(?<!^)(?=[A-Z])','_',path.stem).lower()
        if method=='addAdditionalSaveData':
            wrapper=f'''{match.group(1)} void {method}(net.minecraft.world.level.storage.{io_type} {variable}) {{
        super.{method}({variable});
        CompoundTag {tag} = new CompoundTag();
        {legacy_method}({tag});
        {variable}.store("{key}", CompoundTag.CODEC, {tag});
    }}
    private void {legacy_method}(CompoundTag {tag}) {{'''
        else:
            wrapper=f'''{match.group(1)} void {method}(net.minecraft.world.level.storage.{io_type} {variable}) {{
        super.{method}({variable});
        {legacy_method}({variable}.read("{key}", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }}
    private void {legacy_method}(CompoundTag {tag}) {{'''
        text=text[:match.start()]+wrapper+body+'}'+text[end:]
    text=text.replace('this.addAdditionalSaveData(tag);', 'this.writeBladeData(tag);').replace('this.readAdditionalSaveData(tag);', 'this.readBladeData(tag);')
    if path.name in ('BladeStandEntity.java', 'PlacePreviewEntity.java'):
        text=text.replace('public ItemEntity spawnAtLocation(ItemLike iip)', 'public ItemEntity spawnAtLocation(net.minecraft.server.level.ServerLevel level, ItemLike iip)')
        text=text.replace('super.spawnAtLocation(iip)', 'super.spawnAtLocation(level, iip)')
        text=text.replace('e.pos = placePos;', 'e.setPos(placePos.getCenter());')
    if path.name=='EntityAbstractSummonedSword.java':
        text=text.replace('targetEntity.hurt(damagesource, (float)i)', 'targetEntity.hurtOrSimulate(damagesource, (float)i)')
    path.write_text(text,'utf-8')
# A player's command sender world is its current level in the new API.
for path in root.rglob('*.java'):
    text=path.read_text('utf-8').replace('.getCommandSenderWorld()', '.level()')
    text=text.replace('net.neoforged.neoforge.event.tick.LivingTickEvent.Pre', 'net.neoforged.neoforge.event.tick.EntityTickEvent.Pre')
    if path.name in ('StunManager.java', 'Untouchable.java'):
        text=text.replace('LivingEntity target = event.getEntity();', 'if (!(event.getEntity() instanceof LivingEntity target)) return;')
        text=text.replace('LivingEntity entity = event.getEntity();', 'if (!(event.getEntity() instanceof LivingEntity entity)) return;')
    # Relocate any mechanically inserted import that preceded a copyright header's package.
    match=re.search(r'(?m)^package [^;]+;',text)
    if match:
        head=text[:match.start()]; imports=re.findall(r'(?m)^import [^;]+;\n?',head)
        if imports:
            head=re.sub(r'(?m)^import [^;]+;\n?', '', head)
            text=head+text[match.start():match.end()]+'\n\n'+''.join(imports)+text[match.end():]
    path.write_text(text,'utf-8')
print('Ported all entity persistence and synced-data builders')
