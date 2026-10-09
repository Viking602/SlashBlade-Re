"""Move packet registration to typed payloads and physically separate client handlers."""
from pathlib import Path
import re
from migrate_names import end_parenthesis
root = Path(__file__).resolve().parents[1] / 'src/main/java/mods/flammpfeil/slashblade'
for name, id in [('MoveCommandMessage','move_command'), ('ActiveStateSyncMessage','active_state'), ('RankSyncMessage','rank_sync'), ('MotionBroadcastMessage','motion')]:
    path = root / f'network/{name}.java'
    text = path.read_text('utf-8')
    text = re.sub(r'import net\.neoforged\.(?:fml\.DistExecutor|neoforge\.network\.(?:NetworkDirection|NetworkEvent));\n', '', text)
    text = re.sub(r'import net\.minecraft\.client\.[^;]+;\n', '', text)
    if name != 'MoveCommandMessage':
        start = text.index('    static public void handle(')
        text = text[:start] + '}\n'
    else:
        text = text.replace('Supplier<NetworkEvent.Context> ctx', 'net.neoforged.neoforge.network.handling.IPayloadContext ctx')
        text = text.replace('ctx.get().enqueueWork', 'ctx.enqueueWork')
        text = text.replace('ServerPlayer sender = ctx.get().getSender();', 'if (!(ctx.player() instanceof ServerPlayer sender)) return;')
        text = text.replace('        ctx.get().setPacketHandled(true);\n', '')
    text = text.replace(f'public class {name} {{', f'''public class {name} implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {{
    public static final Type<{name}> TYPE = new Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("slashblade", "{id}"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, {name}> STREAM_CODEC =
        net.minecraft.network.codec.StreamCodec.of((buffer, message) -> encode(message, buffer), {name}::decode);
    @Override public Type<{name}> type() {{ return TYPE; }}
''')
    text = text.replace('buf.readUtf()', 'buf.readUtf(128)')
    path.write_text(text, 'utf-8')
for path in root.rglob('*.java'):
    text = path.read_text('utf-8')
    text = text.replace('NetworkManager.INSTANCE.sendToServer(', 'PacketDistributor.sendToServer(')
    pattern = r'NetworkManager\.INSTANCE\.send\(PacketDistributor\.(PLAYER|TRACKING_ENTITY_AND_SELF|TRACKING_ENTITY)\.with\(\(\)->([^)]*)\), ([^)]*)\)'
    def replacement(match):
        method = {'PLAYER':'sendToPlayer', 'TRACKING_ENTITY_AND_SELF':'sendToPlayersTrackingEntityAndSelf', 'TRACKING_ENTITY':'sendToPlayersTrackingEntity'}[match.group(1)]
        return f'PacketDistributor.{method}({match.group(2)}, {match.group(3)})'
    text = re.sub(pattern, replacement, text)
    text = text.replace('NetworkManager.INSTANCE.send(PacketDistributor.PLAYER.with(()->(ServerPlayer)user), msg)', 'PacketDistributor.sendToPlayer((ServerPlayer)user, msg)')
    if 'PacketDistributor.' in text and 'import net.neoforged.neoforge.network.PacketDistributor;' not in text:
        text = text.replace('\n\n', '\n\nimport net.neoforged.neoforge.network.PacketDistributor;\n', 1)
    path.write_text(text, 'utf-8')
print('Ported four payloads and packet distribution')
