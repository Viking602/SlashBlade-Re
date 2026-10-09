"""One-shot migration of old capability lookups to SBData's modern storage."""
from pathlib import Path
import re
from migrate_names import end_parenthesis

root = Path(__file__).resolve().parents[1] / 'src/main/java/mods/flammpfeil/slashblade'

def receiver_start(text, end):
    index = end - 1
    while index >= 0 and text[index].isspace(): index -= 1
    while index >= 0:
        char = text[index]
        if char in ')]':
            close, opening = char, '(' if char == ')' else '['
            depth = 1; index -= 1
            while index >= 0 and depth:
                if text[index] == close: depth += 1
                elif text[index] == opening: depth -= 1
                index -= 1
        elif char.isalnum() or char in '_.$': index -= 1
        elif char.isspace():
            previous = index
            while previous >= 0 and text[previous].isspace(): previous -= 1
            # Only line breaks around a chained member operator are part of a receiver.
            if previous >= 0 and (text[previous] == '.' or text[index+1:end].lstrip().startswith('.')):
                index = previous
            else: break
        else: break
    return index + 1

for path in (root.rglob('*.java') if __name__ == '__main__' else []):
    if path.parts[-2] == 'compat': continue
    text = path.read_text('utf-8')
    text = re.sub(r'import net\.neoforged\.neoforge\.common\.capabilities\.[^;]+;\s*', '', text)
    text = text.replace('import net.neoforged.neoforge.common.util.INBTSerializable;', '')
    text = re.sub(r'Capability<([^>]+)>\s+(\w+)\s*=\s*CapabilityManager\.get\(new CapabilityToken<>\(\)\{\}\);',
                  r'StateKey<\1> \2 = StateKey.of(\1.class);', text)
    if 'StateKey<' in text:
        text = text.replace('\n\n', '\n\nimport mods.flammpfeil.slashblade.compat.StateKey;\n', 1)
    if path.name.endswith('CapabilityProvider.java'):
        text = re.sub(r' implements ICapabilityProvider, INBTSerializable<[^>]+>', '', text)
        text = re.sub(r'\s*@NotNull\s*@Override\s*public <T> LazyOptional<T> getCapability\([^}]+\}', '', text)
        text = text.replace('    @Override\n', '')
        state_type = re.search(r'LazyOptional<([^>]+)> state', text).group(1)
        name = path.stem
        if not re.search(r'public ' + name + r'\(\)', text):
            text = text.replace('    protected LazyOptional', f'    public {name}() {{}}\n\n    protected LazyOptional', 1)
        pos = text.index('    protected LazyOptional')
        text = text[:pos] + f'    public {name}({state_type} instance) {{ this.state = LazyOptional.of(() -> instance); }}\n    public {state_type} getState() {{ return state.orElseThrow(() -> new IllegalStateException("Missing state")); }}\n\n' + text[pos:]
    if path.name.startswith('Capability') and not path.name.endswith('Provider.java') and path.parent.name != 'event':
        text = re.sub(r'\s*public static void register\(RegisterCapabilitiesEvent event\)\s*\{[^}]*\}', '', text)
        text = text.replace('import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;', '')
    search = 0
    replaced = False
    while (pos := text.find('.getCapability(', search)) >= 0:
        opening = pos + len('.getCapability')
        closing = end_parenthesis(text, opening)
        start = receiver_start(text, pos)
        replacement = 'SBData.get(' + text[start:pos].strip() + ', ' + text[opening+1:closing] + ')'
        text = text[:start] + replacement + text[closing+1:]
        search = start + len(replacement); replaced = True
    if replaced:
        text = text.replace('\n\n', '\n\nimport mods.flammpfeil.slashblade.compat.SBData;\n', 1)
    if path.name == 'SlashBladeState.java':
        text = text.replace('    //action state', '    private Runnable changeListener = () -> {};\n    public void setChangeListener(Runnable listener) { changeListener = listener; }\n\n    //action state', 1)
        matches = list(re.finditer(r'public void (set\w+)\([^)]*\)\s*\{', text))
        for match in reversed(matches):
            if match.group(1) in ('setChangeListener', 'setHasChangedActiveState', 'setShareTag'): continue
            opening = match.end()-1; depth = 1; end = opening + 1
            while depth:
                if text[end] == '{': depth += 1
                elif text[end] == '}': depth -= 1
                end += 1
            text = text[:end-1] + '    changeListener.run();\n    ' + text[end-1:]
        text = text.replace('if(ComboState.NONE.valueOf(getComboRootName()) == null){\n                return Extra.STANDBY_INAIR;', 'if(ComboState.NONE.valueOf(getComboRootAirName()) == null){\n                return Extra.STANDBY_INAIR;')
    path.write_text(text, 'utf-8')
if __name__ == '__main__':
    print('Migrated gameplay lookups, state serializers and component write-through')
