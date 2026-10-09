"""Check saved evidence and artifact hashes for the private project02 port."""
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
from zipfile import ZipFile
from initialize_instance import ROOT


def read(relative):
    return (ROOT / relative).read_text(encoding='utf-8', errors='replace')


def sha(path):
    # Preserve the paths recorded in older evidence when development folders
    # were relocated into .work; the recorded hashes themselves stay intact.
    if not path.exists() and path.is_relative_to(ROOT):
        relative = path.relative_to(ROOT)
        if relative.parts and relative.parts[0] in ('devtools', 'verification', 'test-server',
                                                   'test-original-client', 'test-original-server'):
            path = ROOT / '.work' / relative
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def save(name, value):
    path = ROOT / '.work/verification' / name
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return str(path)


if __name__ == '__main__':
    source = Path(__file__).resolve().parents[1]
    now = datetime.now().astimezone().isoformat()
    assert json.loads(read('project02.json'))['id'] == 'project02'
    properties = dict(line.split('=', 1) for line in (source / 'gradle.properties').read_text('utf-8').splitlines() if '=' in line)
    test_log = 'gametest-icon-flatlight-final.log'
    tests = read('.work/verification/' + test_log)
    assert 'BUILD SUCCESSFUL' in tests
    port_count = int(re.search(r'Registered (\d+) SlashBlade port integration tests', tests)[1])
    combat_count = int(re.search(r'Registered (\d+) SlashBlade combat integration tests', tests)[1])
    total_count = int(re.search(r'All (\d+) required tests passed', tests)[1])
    assert (port_count, combat_count, total_count) == (48, 34, 83)
    jar_name = f'SlashBlade-26.1.2-{properties["mod_version"]}.jar'
    jar_paths = [ROOT / 'mods' / jar_name, source / 'build/libs' / jar_name, ROOT / '.work/test-server/mods' / jar_name]
    hashes = [sha(path) for path in jar_paths]
    assert len(set(hashes)) == 1, 'Build, installed client, and installed server must agree'

    # Combat evidence belongs to port.2; the current GUI-only build has its own
    # GameTest run and production client/server screenshots and logs.
    combat_report = json.loads(read('.work/verification/port-verification-port.2.json'))
    combat_artifact = combat_report['artifact']
    combat_version = combat_artifact['name'].removeprefix('SlashBlade-26.1.2-').removesuffix('.jar')
    combat_hash = combat_artifact['sha256']
    for prefix in ['mods-', 'test-server-mods-']:
        assert sha(ROOT / '.work/verification/previous-builds' / (prefix + combat_artifact['name'])) == combat_hash

    server = read('.work/verification/dedicated-server-combat.log')
    primary = read('.work/verification/client-combat-primary.log')
    guest = read('.work/verification/client-combat-guest.log')
    assert 'DEDICATED_SERVER in PROD' in server and 'All dimensions are saved' in server
    assert 'There are 2 of a max of 4 players online: SlashBladeDev, SlashBladeGuest' in server
    for text in [primary, guest]:
        assert f'Slash Blade {combat_version}' in text
        assert 'Stopping!' in text
        assert 'Project02 GLFW ownership guard active' in text
        assert re.search(r'Combat client renderer submitted: judgement_cut entity \d+ owner 2a48d53f-6994-370f-9a94-a2c7949ecaa1', text)
        assert 'Combat client observer freeze synchronized:' in text
        assert 'Combat client observer critical synchronized:' in text
    assert 'localOwner=false' in guest
    for mode, ticks in [('sa', 20), ('sa', 9), ('super', 25)]:
        assert f'Combat client probe released: {mode} at tick {ticks}' in primary
    health = [float(n) for n in re.findall(r'Husk has the following entity data: ([\d.]+)f', server)]
    assert len(health) >= 6 and min(health) < 190, 'Save server health readings around combat inputs'
    damage = [float(n) for n in re.findall(r'SlashBladeDev has the following entity data: ([\d.]+)f', server)]
    assert any(.5 <= n < .65 for n in damage), 'Super SA must spend half durability plus normal hit costs'

    durability = json.loads(read('.work/verification/durability-verification.json'))
    durability_artifact = json.loads(read('.work/verification/port-verification-port.3.json'))['artifact']
    durability_version = durability_artifact['name'].removeprefix('SlashBlade-26.1.2-').removesuffix('.jar')
    assert durability['build_sha256'] == durability_artifact['sha256']
    for prefix in ['mods-', 'test-server-mods-']:
        assert sha(ROOT / '.work/verification/previous-builds' / (prefix + durability_artifact['name'])) == durability_artifact['sha256']
    assert durability['all_visual_checks_passed']
    assert durability['blade_damage_fractions'] == [0, .25, .5, .75, .99, 1]
    assert all(durability['checks'].values())
    durability_client = read('.work/verification/client-durability.log')
    durability_server = read('.work/verification/dedicated-server-durability.log')
    for log in [durability_client, durability_server]:
        assert f'Slash Blade {durability_version}' in log
    assert 'Stopping!' in durability_client
    assert 'Project02 GLFW ownership guard active' in durability_client
    assert 'Combat client probe' not in durability_client
    assert 'DEDICATED_SERVER in PROD' in durability_server
    assert 'SlashBladeDev joined the game' in durability_server
    assert 'All dimensions are saved' in durability_server
    for screenshot in durability['screenshots']:
        assert sha(ROOT / screenshot['path']) == screenshot['sha256']

    icons = json.loads(read('.work/verification/icon-verification.json'))
    assert icons['build_sha256'] == hashes[0]
    assert icons['version'] == properties['mod_version']
    assert icons['all_visual_checks_passed'] and all(icons['checks'].values())
    icon_client = read('.work/verification/client-icon-flatlight.log')
    icon_server = read('.work/verification/dedicated-server-icon-flatlight.log')
    for log in [icon_client, icon_server]:
        assert f'Slash Blade {properties["mod_version"]}' in log
    assert 'Stopping!' in icon_client and 'Project02 GLFW ownership guard active' in icon_client
    assert 'Combat client probe' not in icon_client
    assert icon_client.count('Reloading ResourceManager:') >= 2
    assert 'DEDICATED_SERVER in PROD' in icon_server and 'SlashBladeDev joined the game' in icon_server
    assert 'All dimensions are saved' in icon_server
    for screenshot in icons['screenshots']:
        assert sha(ROOT / screenshot['path']) == screenshot['sha256']
    bounds = json.loads(read('.work/verification/icon-bounds.json'))
    assert bounds['passed'] and bounds['after']['combinations'] == 90
    assert bounds['after']['outside_slot_vertices'] == 0 and bounds['after']['minimum_edge_margin_pixels'] >= .4
    lighting = json.loads(read('.work/verification/icon-lighting-contract.json'))
    assert lighting['passed'] and lighting['shipped_models_and_textures_unchanged']
    assert lighting['maximum_restored_coefficient_error'] < 1e-6
    assert lighting['gui_light_selection']['upstream_and_current_match']
    assert lighting['gui_light_selection']['current_json_gui_light'] == 'front'
    assert sha(ROOT / 'project02.jar') == lighting['current_client']['sha256']
    for asset in lighting['assets']:
        assert sha(source / asset['path']) == asset['sha256']
    prior_icons = json.loads(read('.work/verification/icon-verification-port.4.json'))
    for prefix in ['mods-', 'test-server-mods-']:
        assert sha(ROOT / '.work/verification/previous-builds' / (prefix + 'SlashBlade-26.1.2-' + prior_icons['version'] + '.jar')) == prior_icons['build_sha256']
    for screenshot in prior_icons['screenshots']:
        assert sha(ROOT / screenshot['path']) == screenshot['sha256']
    prior_lighting = json.loads(read('.work/verification/icon-verification-port.5.json'))
    for prefix in ['mods-', 'test-server-mods-']:
        assert sha(ROOT / '.work/verification/previous-builds' / (prefix + 'SlashBlade-26.1.2-' + prior_lighting['version'] + '.jar')) == prior_lighting['build_sha256']
    brightness = json.loads(read('.work/verification/icon-brightness-comparison.json'))
    assert brightness['passed'] and brightness['port6_sha256'] == hashes[0]
    assert brightness['port5_sha256'] == prior_lighting['build_sha256']
    for screenshot in brightness['screenshots'].values():
        assert sha(ROOT / screenshot['path']) == screenshot['sha256']
    provenance = json.loads(read('.work/verification/original-reference-provenance.json'))
    assert provenance['runtime_source_and_resources_unchanged'] and provenance['checked_files'] == 411
    for reference_file in provenance['files']:
        assert sha(Path(provenance['reference_source']) / reference_file['path']) == reference_file['sha256']
    assert sha(Path(brightness['reference']['mod_jar'])) == brightness['reference']['mod_jar_sha256']
    original_client = read('.work/verification/client-original-reference.log')
    original_server = read('.work/verification/dedicated-server-original-reference.log')
    assert 'Stopping!' in original_client and 'BUILD SUCCESSFUL' in original_client
    assert 'OriginalBladeDev joined the game' in original_server and 'All dimensions are saved' in original_server

    baseline = json.loads(read('.work/verification/native-fix-original-file-hashes.json'))
    current = {path: sha(ROOT / path) for path in baseline}
    assert baseline == current, 'Original Minecraft files changed'
    native_tests = json.loads(read('.work/verification/native-fix-native-tests.json'))
    switches = json.loads(read('.work/verification/native-fix-switches.json'))
    assert native_tests['regression'][0]['exception'] == '0xc0000005'
    assert native_tests['regression'][1]['passed']
    assert switches['total_switches'] == 100 and switches['all_window_state_checks_succeeded']
    native_name = 'project02-native-window-fix-26.1.2-1.0.0.jar'
    native_paths = [ROOT / 'mods' / native_name, source.parent / 'project02-native-window-fix/build/libs' / native_name, ROOT / '.work/test-server/mods' / native_name]
    native_hashes = [sha(path) for path in native_paths]
    assert len(set(native_hashes)) == 1
    with ZipFile(native_paths[0]) as archive:
        dll_names = [name for name in archive.namelist() if name.endswith('.dll')]
        assert len(dll_names) == 1
        dll_hash = hashlib.sha256(archive.read(dll_names[0])).hexdigest()
    assert dll_hash == native_tests['guard_sha256']
    native_logs = ['native-fix-client-primary.log', 'native-fix-client-guest.log']
    rejections = []
    for log in native_logs:
        text = read('.work/verification/' + log)
        assert 'Stopping!' in text and 'Project02 GLFW ownership guard active' in text
        counts = re.findall(r'(\d+) foreign GLFW pointers rejected', text)
        assert counts, f'Missing native counter in {log}'
        rejections.append(int(counts[-1]))
    assert sum(rejections) == 1101
    native_report = {
        'generated_at': now, 'status': 'verified_on_local_windows_x64',
        'source': str(source.parent / 'project02-native-window-fix'),
        'artifact': {'name': native_name, 'sha256': native_hashes[0], 'size_bytes': native_paths[0].stat().st_size, 'all_three_copies_match': True, 'dll_sha256': dll_hash},
        'original_files': {'baseline_sha256': baseline, 'current_sha256': current, 'count': len(baseline), 'all_unchanged': True},
        'implementation': 'Independent client mod; process-local GLFW GetPropW import filter checks HWND owner PID. No original file replacement or Minecraft class transformation.',
        'regression': native_tests['regression'],
        'performance': {k: v for k, v in native_tests['performance'].items() if k != 'individual_runs'},
        'two_client_window_switches': {'count': 100, 'all_succeeded': True, 'both_clients_exited_normally': True, 'rejected_foreign_pointers_per_client': rejections, 'rejected_total': sum(rejections), 'logs': native_logs},
        'scope': 'Windows x86-64; local machine only; polling microbenchmark does not measure game FPS.',
    }
    native_destination = save('native-fix-verification.json', native_report)
    report = {
        'generated_at': now, 'status': 'private_development_port_icon_verified',
        'all_features_exhaustively_confirmed': False, 'instance': str(ROOT), 'source': str(source),
        'upstream': 'https://github.com/Viking602/SlashBlade_2',
        'upstream_commit': '3fd99e26708416cc6ca346fc39b7e3881564e72d', 'branch': 'codex/port-26.1.2',
        'minecraft': properties['minecraft_version'], 'neoforge': properties['neo_version'],
        'java': 'Zulu OpenJDK 25.0.2', 'gradle': '9.2.1', 'moddevgradle': '2.0.148',
        'artifact': {'name': jar_name, 'size_bytes': jar_paths[0].stat().st_size, 'sha256': hashes[0], 'all_three_copies_match': True},
        'installation': json.loads(read('.work/verification/client-installation.json')),
        'assets': json.loads(read('.work/verification/assets.json')),
        'content': {'items': 15, 'entity_types': 10, 'recipes': 39, 'advancements': 57, 'creative_recipe_blades': 11},
        'game_tests': {'port': port_count, 'combat': combat_count, 'neoforge_baseline': 1, 'required_total': total_count, 'all_passed': True, 'log': test_log},
        'current_build_client_server_verification': {
            'build_sha256': hashes[0], 'logs': ['client-icon-flatlight.log', 'dedicated-server-icon-flatlight.log'],
            'clients': ['SlashBladeDev'], 'icon_display': icons, 'icon_bounds': {
                'report': 'icon-bounds.json', 'combinations': 90, 'all_fit': True,
                'minimum_edge_margin_pixels': bounds['after']['minimum_edge_margin_pixels'],
            }, 'upstream_icon_lighting': lighting, 'live_original_brightness_comparison': brightness,
            'clients_and_server_exited_normally': True, 'probe_disabled_on_normal_launch': True,
        },
        'prior_icon_shader_verification': {
            'build_sha256': prior_lighting['build_sha256'], 'version': prior_lighting['version'],
            'logs': ['client-icon-brightness.log', 'dedicated-server-icon-brightness.log'],
            'scope_correction': 'Port.5 matched shader coefficients but omitted the actual GUI-light selection; it used default SIDE rather than upstream front lighting. Port.6 corrects this and records a real upstream client comparison.',
        },
        'prior_icon_position_verification': {
            'build_sha256': prior_icons['build_sha256'], 'version': prior_icons['version'],
            'logs': ['client-icon-final.log', 'dedicated-server-icon-final.log'], 'icon_display': prior_icons,
        },
        'prior_durability_client_server_verification': {
            'build_sha256': durability_artifact['sha256'], 'version': durability_version,
            'logs': ['client-durability.log', 'dedicated-server-durability.log'], 'durability_display': durability,
            'clients_and_server_exited_normally': True, 'probe_disabled_on_normal_launch': True,
        },
        'prior_combat_client_server_verification': {
            'build_sha256': combat_hash, 'version': combat_version,
            'logs': ['client-combat-primary.log', 'client-combat-guest.log', 'dedicated-server-combat.log'],
            'clients': ['SlashBladeDev', 'SlashBladeGuest'], 'physical_mouse_clicks': ['left attack', 'short right attack'],
            'automated_client_hold_and_release': ['normal SA 20 ticks', 'Just SA 9 ticks', 'Super SA V mapping 25 ticks'],
            'normal_network_input_used': True, 'remote_attack_receive_and_render_submission': True,
            'just_sa_critical_state_synchronized': True, 'super_sa_freeze_attachment_synchronized_to_both_clients': True,
            'server_health_readings': health, 'server_blade_damage_readings': damage,
            'clients_and_server_exited_normally': True, 'probe_disabled_on_normal_launch': True,
        },
        'prior_verification': {
            'initial_combat_build_sha256': 'd593676c91a5ab0e18904114d52e85f549175137753fe70143b0706bc96dce77',
            'initial_combat_logs': ['client-combat-primary-initial.log', 'client-combat-guest-initial.log', 'dedicated-server-combat-initial.log'],
            'early_multiplayer_build_sha256': '09094a0b366b315ac99d6400752f940d6537daa1f8bd57c2d85e2471e48db8e2',
            'port_1_delivery_sha256': '67dc57067df136abf9cb96e9353c3eb2bff5253f441f14d14fbfc6248d138313',
            'checks': ['singleplayer create/save/reopen', 'inventory/first/third-person blade', 'blade stand persistence', 'advancement crafting/anvil preview', 'server/client resource reload', 'summoned swords and remote formation following', 'separate blade UUIDs and kill counters', 'delivery restart and saved kill counter'],
            'logs': ['client-final.log', 'dedicated-server-final.log', 'client-delivery.log', 'dedicated-server-delivery.log'],
        },
        'super_sa': {'documentation_source': 'https://w.atwiki.jp/slashblade/pages/37.html', 'default_key': 'V', 'kills': 1000, 'requires_full_durability': True, 'charge_ticks': 20, 'base_durability_cost': .5, 'strike_delay_ticks': 25, 'stun_freeze_ticks': 40, 'area_blocks': [64, 32, 64], 'one_cut_per_target_batches_five_magic_hits': True, 'non_player_living_targets_freeze': True, 'player_targets_slowed': True},
        'native_window_issue': {'status': 'fixed by independent mod on local Windows x64', 'report': native_destination, 'original_game_files_unchanged': True},
        'known_limits': ['Optional Kosmx PlayerAnimator integration remains archived; built-in NyMmd/VMD works.', 'Reverse glow and restored Super SA effects use modern rendering, not pixel-identical legacy rendering.', 'Forge 1.20.1 existing-save conversion has not been validated.', 'Other mods, custom SA, all animation/enchantment combinations and extended multiplayer are not exhaustively tested.', 'Offline testing identities produce official session/Realms authentication errors.', 'Two clients sharing one instance have original log rollover file-lock errors; non-Windows Netty native probes produce handled logging exceptions. Separate stdout logs preserve the successful join/combat/shutdown evidence.'],
        'license_declaration_retained': properties['mod_license'], 'public_release_performed': False,
        'documentation': [str(source / 'docs/PORTING.md'), str(source / 'docs/COMBAT.md')],
    }
    destination = save('port-verification.json', report)
    print(f'{total_count} GameTests passed; JAR SHA-256 {hashes[0]}; original {len(baseline)} files unchanged; {destination}')
