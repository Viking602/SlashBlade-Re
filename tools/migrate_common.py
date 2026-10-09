"""Mechanical event, package and entity-side API migration."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1] / 'src/main/java'
for path in root.rglob('*.java'):
    text = path.read_text('utf-8')
    text = re.sub(r'\.isClientSide\b(?!\s*\()', '.isClientSide()', text)
    text = text.replace('.serverLevel()', '.level()')
    text = text.replace('net.minecraft.world.level.GameRules', 'net.minecraft.world.level.gamerules.GameRules')
    text = text.replace('net.minecraft.world.entity.monster.Monster', 'net.minecraft.world.entity.monster.Monster')
    text = text.replace('net.minecraft.client.player.Input', 'net.minecraft.client.player.ClientInput')
    text = text.replace('net.minecraft.client.renderer.RenderType', 'net.minecraft.client.renderer.rendertype.RenderType')
    text = text.replace('AttributeModifier.Operation.ADDITION', 'AttributeModifier.Operation.ADD_VALUE')
    text = text.replace('AttributeModifier.Operation.MULTIPLY_BASE', 'AttributeModifier.Operation.ADD_MULTIPLIED_BASE')
    text = text.replace('AttributeModifier.Operation.MULTIPLY_TOTAL', 'AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL')
    text = text.replace('import net.neoforged.neoforge.event.TickEvent;', '')
    if 'TickEvent.PlayerTickEvent' in text:
        text = text.replace('TickEvent.PlayerTickEvent', 'net.neoforged.neoforge.event.tick.PlayerTickEvent.Post')
        text = text.replace('        if(event.phase != TickEvent.Phase.END) return;\n', '')
        text = text.replace('event.player', 'event.getEntity()')
    if 'TickEvent.RenderTickEvent' in text:
        text = text.replace('TickEvent.RenderTickEvent', 'net.neoforged.neoforge.client.event.RenderFrameEvent.Pre')
        text = text.replace('        if(event.phase != TickEvent.Phase.START) return;\n', '')
        text = text.replace('event.renderTickTime', 'event.getPartialTick().getGameTimeDeltaPartialTick(false)')
    text = text.replace('LivingEvent.LivingTickEvent', 'net.neoforged.neoforge.event.tick.LivingTickEvent.Pre')
    text = text.replace('LivingHurtEvent', 'LivingIncomingDamageEvent').replace('LivingAttackEvent', 'LivingIncomingDamageEvent')
    text = text.replace('getAsString()', 'asString().orElse("")') if 'net.minecraft.nbt.StringTag' in text else text
    text = text.replace('NetworkManager.INSTANCE.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(sp.getX(), sp.getY(),sp.getZ(), 20, sp.level().dimension())), msg)',
        'PacketDistributor.sendToPlayersNear(sp.level(), null, sp.getX(), sp.getY(), sp.getZ(), 20, msg)')
    text = text.replace('import net.neoforged.neoforge.client.event.RenderLivingEvent;', '') if path.name == 'BladeMotionEventBroadcaster.java' else text
    path.write_text(text, 'utf-8')
print('Migrated event phases and common 26.1.2 APIs')
