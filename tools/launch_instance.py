"""Start the isolated installed client, with an offline local testing profile."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import uuid
from initialize_instance import ROOT, allowed

def arguments(items, replacements):
    result = []
    for item in items:
        if isinstance(item, dict):
            if not allowed(item):
                continue
            item = item['value']
        for value in item if isinstance(item, list) else [item]:
            for key, replacement in replacements.items():
                value = value.replace('${' + key + '}', replacement)
            if '${' in value:
                raise ValueError(f'Unresolved argument: {value}')
            result.append(value)
    return result

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--username', default='SlashBladeDev')
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--vanilla', action='store_true')
    parser.add_argument('--server', help='Connect directly to a local verification server, e.g. 127.0.0.1:25582')
    parser.add_argument('--game-directory', type=Path, help='Separate game directory for disposable integration test fixtures')
    parser.add_argument('--java-executable', choices=('java.exe', 'javaw.exe'), default='java.exe')
    args = parser.parse_args()
    game_directory = args.game_directory.resolve() if args.game_directory else ROOT
    metadata = json.loads((ROOT / ('.work/devtools/vanilla-26.1.2.json' if args.vanilla else 'project02.json')).read_text('utf-8'))
    classpath = [str(ROOT / 'libraries' / entry['downloads']['artifact']['path']) for entry in metadata['libraries'] if allowed(entry)]
    classpath.append(str(ROOT / 'project02.jar'))
    replacements = {
        'auth_player_name': args.username,
        'auth_uuid': uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + args.username).encode('utf-8')).digest(), version=3).hex,
        'auth_access_token': '0', 'auth_xuid': '', 'clientid': '', 'user_type': 'legacy',
        'version_name': 'project02', 'version_type': 'release', 'game_directory': str(game_directory),
        'assets_root': str(ROOT / 'assets'), 'assets_index_name': metadata['assetIndex']['id'],
        'natives_directory': str(ROOT / 'natives'), 'library_directory': str(ROOT / 'libraries'),
        'launcher_name': 'project02-development', 'launcher_version': '1', 'classpath': ';'.join(classpath),
    }
    java = ROOT / '.work/devtools/jdk-25/bin' / args.java_executable
    command = [str(java), '-Xms1G', '-Xmx6G'] + arguments(metadata['arguments']['jvm'], replacements)
    command += [metadata['mainClass']] + arguments(metadata['arguments']['game'], replacements)
    command += ['--width', '1280', '--height', '720']
    if args.server:
        command += ['--quickPlayMultiplayer', args.server]
    if args.dry_run:
        print(f'{metadata["mainClass"]}; {len(classpath)} classpath entries; gameDir={game_directory}')
        for path in classpath:
            if not Path(path).is_file():
                raise FileNotFoundError(path)
    else:
        raise SystemExit(subprocess.call(command, cwd=game_directory))
