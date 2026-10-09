"""One-shot, mechanical package/name migration; semantic changes are separate."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1] / 'src/main/java'
def split_arguments(text):
    result, start, depth, quote = [], 0, 0, None
    for i, char in enumerate(text):
        if quote:
            if char == quote and (i == 0 or text[i-1] != '\\'):
                quote = None
        elif char in '\"\'': quote = char
        elif char in '([{': depth += 1
        elif char in ')]}': depth -= 1
        elif char == ',' and depth == 0:
            result.append(text[start:i].strip()); start = i+1
    result.append(text[start:].strip())
    return result

def end_parenthesis(text, start):
    depth, quote = 0, None
    for i in range(start, len(text)):
        char = text[i]
        if quote:
            if char == quote and text[i-1] != '\\': quote = None
        elif char in '\"\'': quote = char
        elif char == '(': depth += 1
        elif char == ')':
            depth -= 1
            if depth == 0: return i
    raise ValueError('Unclosed parenthesis')

changed = 0
for path in (root.rglob('*.java') if __name__ == '__main__' else []):
    original = path.read_text('utf-8')
    text = original.replace('net.minecraftforge.eventbus', 'net.neoforged.bus')
    text = text.replace('net.minecraftforge.fml', 'net.neoforged.fml')
    text = text.replace('net.minecraftforge.api', 'net.neoforged.api')
    text = text.replace('net.minecraftforge', 'net.neoforged.neoforge')
    text = text.replace('net.neoforged.neoforge.common.MinecraftForge', 'net.neoforged.neoforge.common.NeoForge')
    text = text.replace('MinecraftForge.EVENT_BUS', 'NeoForge.EVENT_BUS')
    text = text.replace('net.neoforged.neoforge.common.util.LazyOptional', 'mods.flammpfeil.slashblade.compat.LazyOptional')
    text = re.sub(r'\bResourceLocation\b', 'Identifier', text)
    position = 0
    while (match := re.search(r'new\s+Identifier\s*\(', text[position:])):
        start = position + match.start(); opening = position + match.end() - 1
        closing = end_parenthesis(text, opening)
        args = split_arguments(text[opening+1:closing])
        replacement = f"Identifier.{'fromNamespaceAndPath' if len(args) == 2 else 'parse'}({', '.join(args)})"
        text = text[:start] + replacement + text[closing+1:]
        position = start + len(replacement)
    text = text.replace('javax.annotation.Nonnull', 'org.jetbrains.annotations.NotNull').replace('@Nonnull', '@NotNull')
    text = text.replace('javax.annotation.Nullable', 'org.jetbrains.annotations.Nullable')
    if text != original:
        path.write_text(text, 'utf-8'); changed += 1
if __name__ == '__main__':
    print(f'Migrated mechanical names in {changed} Java files')
