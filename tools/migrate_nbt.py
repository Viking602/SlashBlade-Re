"""Port legacy NBT reads to the explicit-default APIs in Minecraft 26.1.2."""
from pathlib import Path
import re
from migrate_names import end_parenthesis, split_arguments
from migrate_storage import receiver_start

root = Path(__file__).resolve().parents[1] / 'src/main/java'
defaults = {'Byte':'(byte)0', 'Short':'(short)0', 'Int':'0', 'Long':'0L', 'Float':'0.0F', 'Double':'0.0D', 'String':'""', 'Boolean':'false'}
for path in root.rglob('*.java'):
    text = path.read_text('utf-8')
    text = text.replace('.getAllKeys()', '.keySet()')
    pos = 0
    pattern = r'\.(get(?:Byte|Short|Int|Long|Float|Double|String|Boolean|Compound|List|ByteArray|IntArray|LongArray)|putUUID|hasUUID|getUUID|contains)\('
    while (match := re.search(pattern, text[pos:])):
        start = pos + match.start(); opening = pos + match.end() - 1
        closing = end_parenthesis(text, opening)
        method = match.group(1); args = split_arguments(text[opening+1:closing])
        replacement = None
        if method.startswith('get') and method[3:] in defaults and len(args) == 1 and args[0]:
            replacement = f'.{method}Or({args[0]}, {defaults[method[3:]]})'
        elif method == 'getCompound' and len(args) == 1:
            replacement = f'.getCompoundOrEmpty({args[0]})'
        elif method == 'getList' and len(args) == 2:
            replacement = f'.getListOrEmpty({args[0]})'
        elif method in ('getByteArray', 'getIntArray', 'getLongArray') and len(args) == 1 and not text[closing+1:].startswith('.orElse'):
            replacement = f'.{method}({args[0]}).orElse(new {method[3:-5].lower()}[0])'
        elif method == 'putUUID' and len(args) == 2:
            replacement = f'.store({args[0]}, net.minecraft.core.UUIDUtil.CODEC, {args[1]})'
        elif method == 'hasUUID' and len(args) == 1:
            replacement = f'.read({args[0]}, net.minecraft.core.UUIDUtil.CODEC).isPresent()'
        elif method == 'getUUID' and len(args) == 1 and args[0]:
            replacement = f'.read({args[0]}, net.minecraft.core.UUIDUtil.CODEC).orElse(new java.util.UUID(0L,0L))'
        elif method == 'contains' and len(args) == 2:
            begin = receiver_start(text, start)
            replacement = f'mods.flammpfeil.slashblade.util.NBTHelper.containsType({text[begin:start]}, {args[0]}, {args[1]})'
            start = begin
        if replacement:
            text = text[:start] + replacement + text[closing+1:]
            pos = start + len(replacement)
        else: pos = closing+1
    path.write_text(text, 'utf-8')
print('Migrated explicit-default NBT reads and UUID codecs')
