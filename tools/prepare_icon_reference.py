"""Prepare vanilla server commands and source provenance for the upstream client.

Uses an ordinary chest to deserialize ForgeCaps, then vanilla /item commands.
Does not alter the upstream runtime source or the production game files.
"""
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import subprocess
from initialize_instance import ROOT

SOURCE = Path(__file__).resolve().parents[1]
REFERENCE = SOURCE.parent / 'SlashBlade_2-original-1.20.1'


def snbt(value, key=None):
    if isinstance(value, dict):
        return '{' + ','.join(json.dumps(k) + ':' + snbt(v, k) for k, v in value.items()) + '}'
    if isinstance(value, list):
        return '[' + ','.join(snbt(v) for v in value) + ']'
    if isinstance(value, str):
        return json.dumps(value)
    if isinstance(value, float):
        return str(value) + 'f'
    if key in ('Slot', 'Count', 'isBroken', 'isSealed', 'isNoScabbard', 'rarityType'):
        return str(value) + 'b'
    return str(value)


def blade(state):
    return {'id': 'slashblade:slashblade', 'Count': 1,
            'ForgeCaps': {'slashblade:bladestate': {'State': state}}, 'tag': {}}


if __name__ == '__main__':
    original_files = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', 'HEAD', 'src'], cwd=SOURCE, text=True).splitlines()
    checked = []
    for relative in original_files:
        expected = subprocess.check_output(['git', 'show', 'HEAD:' + relative], cwd=SOURCE)
        current = (REFERENCE / relative).read_bytes()
        text_file = Path(relative).suffix.lower() not in ('.png', '.bmp', '.jpg', '.ogg', '.vmd', '.pmd', '.jar')
        compared = current.replace(b'\r\n', b'\n') if text_file else current
        original = expected.replace(b'\r\n', b'\n') if text_file else expected
        assert compared == original, 'Reference runtime source changed: ' + relative
        checked.append({'path': relative, 'sha256': hashlib.sha256(current).hexdigest(),
                        'comparison': 'Text with normalized line endings' if text_file else 'Identical bytes'})

    items = [blade({'Damage': damage, 'isBroken': int(damage == 1), 'isNoScabbard': 0, 'isSealed': 0})
             for damage in (0., .25, .5, .75, .99, 1.)]
    items.append({'id': 'minecraft:diamond_sword', 'Count': 1, 'tag': {'Damage': 600}})
    items.append(blade({'Damage': .5, 'isNoScabbard': 1, 'isBroken': 0}))
    recipes = REFERENCE / 'src/main/resources/data/slashblade/recipes/creative_tab'
    # Locate named recipe files from their translations rather than assuming names.
    recipe_files = list(recipes.rglob('*.json')) + [recipes.parent / 's_wood.json', recipes.parent / 'yamato.json']
    all_recipes = [json.loads(path.read_text('utf-8'))['result']['nbt'] for path in recipe_files]
    def catalog_item(token):
        matches = [item for item in all_recipes if item['ForgeCaps']['slashblade:bladestate']['State']['translationKey'].endswith(token)
                   and not item['ForgeCaps']['slashblade:bladestate']['State'].get('isBroken', 0)]
        assert len(matches) == 1, (token, len(matches))
        original = matches[0]['ForgeCaps']['slashblade:bladestate']['State']
        state = {k: v for k, v in original.items() if k in ('ModelName', 'TextureName', 'translationKey', 'rarityType', 'isSealed')}
        state.update(Damage=0., isBroken=0, isNoScabbard=0)
        return blade(state)
    tokens = ['simple.bamboo', 'simple.iron', 'simple.silverbamboo', 'simple.white', 'simple.wood',
              'slashblade.yamato', 'ex.fox.black', 'ex.fox.white', 'ex.muramasa', 'ex.ruby', 'ex.tukumo']
    catalog = [catalog_item(token) for token in tokens]
    enchanted = deepcopy(catalog[5])
    enchanted['ForgeCaps']['slashblade:bladestate']['State']['Damage'] = .25
    enchanted['tag']['Enchantments'] = [{'id': 'minecraft:unbreaking', 'lvl': 1}]
    items.append(enchanted)
    items.extend(catalog)
    for slot, item in enumerate(items):
        item['Slot'] = slot
    commands = ['gamemode creative OriginalBladeDev', 'time set noon', 'gamerule doDaylightCycle false',
                'gamerule doWeatherCycle false', 'weather clear', 'clear OriginalBladeDev',
                'setblock 0 -60 3 minecraft:chest', 'data merge block 0 -60 3 ' + snbt({'Items': items})]
    for slot in range(9):
        commands.append(f'item replace entity OriginalBladeDev hotbar.{slot} from block 0 -60 3 container.{slot}')
    for slot in range(6):
        commands.append(f'item replace entity OriginalBladeDev inventory.{slot} from block 0 -60 3 container.{slot}')
    commands.append('item replace entity OriginalBladeDev inventory.6 from block 0 -60 3 container.6')
    for slot in range(11):
        commands.append(f'item replace entity OriginalBladeDev inventory.{9 + slot} from block 0 -60 3 container.{9 + slot}')
    commands.extend(['data get entity OriginalBladeDev Inventory', 'list'])
    (ROOT / '.work/verification/original-reference-fixture-commands.json').write_text(json.dumps(commands, indent=2) + '\n', encoding='utf-8')
    provenance = {'upstream_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=SOURCE, text=True).strip(),
                  'reference_source': str(REFERENCE), 'runtime_source_and_resources_unchanged': True,
                  'checked_files': len(checked), 'files': checked,
                  'build_only_adjustments': ['Restrict Kosmx Maven to its own group.',
                                            'Filter two obsolete 1.16 Projectile constructor descriptors from build AT input and output.',
                                            'Use independent runtime directories and matching window arguments.'],
                  'minecraft': '1.20.1', 'forge': '47.1.44', 'mod_version': '0.1.2',
                  'shader_and_model_reference_client': 'Unmodified upstream runtime source/assets, Java 17, Forge userdev client.',
                  'options': {'gamma': .5, 'guiScale': 3, 'resolution': [1280, 720], 'resourcePacks': []}}
    (ROOT / '.work/verification/original-reference-provenance.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8')
    print(f'{len(checked)} unchanged upstream source/resource files; {len(items)} fixture stacks; {len(commands)} vanilla server commands')
