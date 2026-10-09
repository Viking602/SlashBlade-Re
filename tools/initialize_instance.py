"""Install the official 26.1.2 client files into the isolated project02 instance.

All downloaded artifacts are verified against Mojang's published SHA-1 hashes.
Use --prepare before the official NeoForge installer, then --assets afterwards.
"""
from __future__ import annotations
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import shutil
import urllib.request
import time
import platform
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[3]
assert ROOT == Path(r'D:\MC26\.minecraft\versions\project02')

def fetch(url: str, target: Path, sha1: str | None = None) -> str:
    if target.is_file() and (sha1 is None or hashlib.sha1(target.read_bytes()).hexdigest() == sha1):
        return 'cached'
    target.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=60) as response:
                data = response.read()
            if sha1 and hashlib.sha1(data).hexdigest() != sha1:
                raise ValueError(f'SHA-1 mismatch: {url}')
            temporary = target.with_name(target.name + '.part')
            temporary.write_bytes(data)
            temporary.replace(target)
            return 'downloaded'
        except Exception:
            if attempt == 3:
                raise
            time.sleep(attempt + 1)
    raise AssertionError('unreachable')

def prepare():
    metadata = json.loads((ROOT / '.work/devtools/vanilla-26.1.2.json').read_text('utf-8'))
    target = ROOT / 'versions/26.1.2'
    target.mkdir(parents=True, exist_ok=True)
    (target / '26.1.2.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    artifact = metadata['downloads']['client']
    cached = ROOT / '.work/devtools/gradle-cache/caches/neoformruntime/artifacts/minecraft_26.1.2_client.jar'
    client = target / '26.1.2.jar'
    if cached.is_file() and hashlib.sha1(cached.read_bytes()).hexdigest() == artifact['sha1']:
        shutil.copy2(cached, client)
    else:
        fetch(artifact['url'], client, artifact['sha1'])
    profiles = ROOT / 'launcher_profiles.json'
    if not profiles.exists():
        profiles.write_text(json.dumps({'profiles': {}, 'settings': {}}), encoding='utf-8')
    print(f'Prepared verified vanilla client: {client}', flush=True)

def assets():
    metadata = json.loads((ROOT / '.work/devtools/vanilla-26.1.2.json').read_text('utf-8'))
    index = metadata['assetIndex']
    target = ROOT / f"assets/indexes/{index['id']}.json"
    fetch(index['url'], target, index['sha1'])
    objects = json.loads(target.read_text('utf-8'))['objects']
    unique = {item['hash']: item for item in objects.values()}
    def download(sha):
        return fetch(f'https://resources.download.minecraft.net/{sha[:2]}/{sha}', ROOT / f'assets/objects/{sha[:2]}/{sha}', sha)
    downloaded = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
        for count, result in enumerate(pool.map(download, unique), 1):
            downloaded += result == 'downloaded'
            if count % 250 == 0 or count == len(unique):
                print(f'Verified assets {count}/{len(unique)} ({downloaded} downloaded)', flush=True)
    report = {'minecraft': '26.1.2', 'asset_index': index['id'], 'objects': len(unique), 'logical_files': len(objects), 'downloaded': downloaded, 'all_sha1_verified': True}
    (ROOT / '.work/verification/assets.json').write_text(json.dumps(report, indent=2), encoding='utf-8')

def allowed(entry):
    if 'rules' not in entry:
        return True
    result = False
    for rule in entry['rules']:
        os_rule = rule.get('os', {})
        match = os_rule.get('name', 'windows') == 'windows'
        if 'arch' in os_rule:
            match &= os_rule['arch'] in ('x86_64', 'amd64')
        if rule.get('features'):
            match = False  # Demo, quick play and custom-resolution options are opt-in.
        if match:
            result = rule['action'] == 'allow'
    return result

def finalize():
    vanilla = json.loads((ROOT / '.work/devtools/vanilla-26.1.2.json').read_text('utf-8'))
    neo = json.loads((ROOT / 'versions/neoforge-26.1.2.114/neoforge-26.1.2.114.json').read_text('utf-8'))
    # NeoForge replaces matching Maven modules; avoid putting two versions on the classpath.
    libraries = {}
    for entry in vanilla['libraries'] + neo['libraries']:
        coordinate = entry['name'].split(':')
        key = ':'.join(coordinate[:2] + coordinate[3:])
        libraries[key] = entry
    effective = [entry for entry in libraries.values() if allowed(entry)]
    def download(entry):
        artifact = entry.get('downloads', {}).get('artifact')
        if not artifact:
            raise ValueError(f'Missing artifact metadata: {entry["name"]}')
        target = ROOT / 'libraries' / artifact['path']
        if not artifact.get('url'):
            if not target.is_file():
                raise ValueError(f'Installer output missing: {target}')
            return 'installer output'
        return fetch(artifact['url'], target, artifact.get('sha1'))
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        list(pool.map(download, effective))
    metadata = dict(vanilla)
    metadata.update({k: v for k, v in neo.items() if k not in ('inheritsFrom', 'arguments', 'libraries', 'id')})
    metadata['id'] = 'project02'
    metadata['libraries'] = list(libraries.values())
    metadata['arguments'] = {kind: vanilla['arguments'].get(kind, []) + neo['arguments'].get(kind, []) for kind in ('game', 'jvm')}
    metadata['project02'] = {'minecraft': '26.1.2', 'neoforge': '26.1.2.114', 'game_directory': str(ROOT), 'code_directory': str(ROOT / '.work/SlashBlade_2')}
    (ROOT / 'project02.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    shutil.copy2(ROOT / 'versions/26.1.2/26.1.2.jar', ROOT / 'project02.jar')
    (ROOT / '.work/verification/client-installation.json').write_text(json.dumps({'minecraft': '26.1.2', 'neoforge': '26.1.2.114', 'windows_libraries': len(effective), 'verified': True}, indent=2), encoding='utf-8')
    # Native DLLs stay inside the game instance too.
    for entry in effective:
        if 'natives-windows' not in entry['name']:
            continue
        artifact = entry['downloads']['artifact']
        with ZipFile(ROOT / 'libraries' / artifact['path']) as archive:
            for name in archive.namelist():
                if name.endswith('.dll') and '..' not in Path(name).parts:
                    (ROOT / 'natives' / Path(name).name).write_bytes(archive.read(name))
    print(f'Initialized project02 metadata, client JAR, {len(effective)} Windows libraries and natives', flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--prepare', action='store_true')
    parser.add_argument('--assets', action='store_true')
    parser.add_argument('--finalize', action='store_true')
    args = parser.parse_args()
    if args.prepare:
        prepare()
    if args.assets:
        assets()
    if args.finalize:
        finalize()
