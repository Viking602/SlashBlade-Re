"""Replace old ItemStack tag APIs with explicit immutable-component transactions."""
from pathlib import Path
import re
from migrate_names import end_parenthesis, split_arguments
from migrate_storage import receiver_start
root = Path(__file__).resolve().parents[1]
java = root / 'src/main/java'
legacy = root / 'src/legacy/java'
# Packet components supersede the buffer mixin; renderer registration supersedes
# the two injections into renderer implementation details. Their behavior is
# implemented in the modern payload, special model and AddLayers code instead.
for name in ('MixinPacketBuffer', 'MixinItemRenderer', 'MixinPlayerRenderer'):
    path = java / f'mods/flammpfeil/slashblade/mixin/{name}.java'
    if path.exists():
        destination = legacy / path.relative_to(java)
        destination.parent.mkdir(parents=True, exist_ok=True)
        path.rename(destination)
for path in java.rglob('*.java'):
    text = path.read_text('utf-8')
    if path.name == 'SBItemData.java': continue
    text = text.replace('ItemStack.of(', 'SBItemData.load(')
    pos = 0; changed = False
    while (match := re.search(r'\.(getOrCreateTag|getTag|getShareTag|hasTag|addTagElement|save)\(', text[pos:])):
        start = pos+match.start(); opening=pos+match.end()-1; closing=end_parenthesis(text, opening)
        name=match.group(1); args=split_arguments(text[opening+1:closing])
        if name == 'save' and (path.name == 'EntityHeavyRainSwords.java' or args != ['new CompoundTag()']):
            pos=closing+1; continue
        begin=receiver_start(text,start); receiver=text[begin:start].strip()
        if name in ('getOrCreateTag', 'getTag', 'getShareTag') and args == ['']:
            replacement=f'SBItemData.tag({receiver})'
        elif name == 'hasTag' and args == ['']:
            replacement=f'SBItemData.hasTag({receiver})'
        elif name == 'addTagElement' and len(args)==2:
            replacement=f'SBItemData.put({receiver}, {args[0]}, {args[1]})'
        elif name == 'save': replacement=f'SBItemData.save({receiver})'
        else:
            pos=closing+1; continue
        text=text[:begin]+replacement+text[closing+1:]; pos=begin+len(replacement); changed=True
    if 'SBItemData.' in text and 'import mods.flammpfeil.slashblade.compat.SBItemData;' not in text:
        text=text.replace('\n\n', '\n\nimport mods.flammpfeil.slashblade.compat.SBItemData;\n',1)
    path.write_text(text,'utf-8')
print('Migrated stack snapshots and custom-data transactions')
