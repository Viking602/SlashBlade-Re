"""Compare shipped OBJ lighting with the upstream 1.20.1 shader contract.

Reads both official client JARs without changing them. This checks GUI-light
selection, shader coefficients and asset identity; production screenshots validate the result
separately and are not claimed to be pixel-identical across game versions.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess
from zipfile import ZipFile

from initialize_instance import ROOT
from verify_icon_bounds import ASSETS, SOURCE, project


def git_bytes(relative):
    return subprocess.check_output(['git', 'show', 'HEAD:' + relative], cwd=SOURCE)


def normalize(vector):
    length = math.sqrt(sum(value * value for value in vector))
    return tuple(value / length for value in vector)


def rotate(vector, axis, angle):
    x, y, z = vector
    c, s = math.cos(angle), math.sin(angle)
    if axis == 'x':
        return x, c * y - s * z, s * y + c * z
    return c * x + s * z, y, -s * x + c * z


def item_light(vector, three_dimensional):
    vector = rotate(vector, 'x', math.pi * 3 / 4)
    vector = rotate(vector, 'y', -math.pi / 8)
    if three_dimensional:
        vector = rotate(vector, 'x', 3.2375858)
        vector = rotate(vector, 'y', 1.0821041)
        vector = vector[0], -vector[1], vector[2]
    return normalize(vector)


def mix_light(normal, lights, power, ambient):
    return min(1.0, ambient + power * sum(max(0.0, sum(a * b for a, b in zip(light, normal))) for light in lights))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference-client-jar', type=Path, required=True)
    args = parser.parse_args()
    reference = args.reference_client_jar.resolve()
    modern = ROOT / 'project02.jar'
    reference_metadata = json.loads(reference.with_suffix('.json').read_text('utf-8'))
    reference_sha1 = hashlib.sha1(reference.read_bytes()).hexdigest()
    assert reference_sha1 == reference_metadata['downloads']['client']['sha1']
    with ZipFile(reference) as archive:
        assert json.loads(archive.read('version.json'))['id'] == '1.20.1'
        old_light = archive.read('assets/minecraft/shaders/include/light.glsl').decode()
        old_vertex = archive.read('assets/minecraft/shaders/core/rendertype_entity_smooth_cutout.vsh').decode()
        old_fragment = archive.read('assets/minecraft/shaders/core/rendertype_entity_smooth_cutout.fsh').decode()
    with ZipFile(modern) as archive:
        new_light = archive.read('assets/minecraft/shaders/include/light.glsl').decode()
        new_vertex = archive.read('assets/minecraft/shaders/core/entity.vsh').decode()

    def constants(shader):
        return tuple(float(re.search(r'#define ' + name + r'\s+\(([\d.]+)\)', shader)[1])
                     for name in ('MINECRAFT_LIGHT_POWER', 'MINECRAFT_AMBIENT_LIGHT'))

    old_coefficients, new_coefficients = constants(old_light), constants(new_light)
    assert old_coefficients == new_coefficients == (.6, .4)
    assert 'minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color)' in old_vertex
    assert 'color.a < 0.1' in old_fragment
    assert 'minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color)' in new_vertex
    upstream = git_bytes('src/main/java/mods/flammpfeil/slashblade/client/renderer/util/BladeRenderState.java').decode()
    assert 'RenderType::entitySmoothCutout' in upstream
    upstream_model = git_bytes('src/main/java/mods/flammpfeil/slashblade/client/renderer/model/BladeModel.java').decode()
    assert re.search(r'boolean usesBlockLight\(\)\s*\{\s*return false;', upstream_model)
    current_model = json.loads((ASSETS / 'models/item/slashblade.json').read_text('utf-8'))
    assert current_model['gui_light'] == 'front', 'Upstream disabled block lighting; modern model must select ITEMS_FLAT'
    render_source = (SOURCE / 'src/main/java/mods/flammpfeil/slashblade/client/renderer/util/BladeRenderState.java').read_text('utf-8')
    icon_pipeline = render_source.split('private static final RenderPipeline ICON =', 1)[1].split('.build();', 1)[0]
    for forbidden in ('withShaderDefine("PER_FACE_LIGHTING"', 'withShaderDefine("NO_CARDINAL_LIGHTING"', 'withShaderDefine("EMISSIVE"'):
        assert forbidden not in icon_pipeline
    assert 'withShaderDefine("ALPHA_CUTOUT", 0.1f)' in icon_pipeline

    files = sorted(path for path in (ASSETS / 'model').rglob('*') if path.suffix in ('.obj', '.png'))
    assets = []
    for path in files:
        relative = path.relative_to(SOURCE).as_posix()
        data = path.read_bytes()
        original = git_bytes(relative)
        compared = data.replace(b'\r\n', b'\n') if path.suffix == '.obj' else data
        expected = original.replace(b'\r\n', b'\n') if path.suffix == '.obj' else original
        assert compared == expected, 'Model or texture changed: ' + relative
        assets.append({'path': relative, 'sha256': hashlib.sha256(data).hexdigest(),
                       'comparison': 'OBJ text with normalized newlines' if path.suffix == '.obj' else 'identical PNG bytes'})

    gui = current_model['display']['gui']
    normal_transform = {'rotation': gui['rotation'], 'translation': [0, 0, 0], 'scale': [125, 125, 125]}
    samples = []
    for path in (ASSETS / 'model').rglob('*.obj'):
        normals, selected, group = [], [], None
        for line in path.read_text('utf-8').splitlines():
            tokens = line.split()
            if not tokens:
                continue
            if tokens[0] == 'vn':
                normals.append(tuple(map(float, tokens[1:4])))
            elif tokens[0] in ('g', 'o'):
                group = tokens[1]
            elif tokens[0] == 'f' and group in ('item_blade', 'item_bladens', 'item_damaged', 'base', 'color', 'color_r'):
                for token in tokens[1:]:
                    parts = token.split('/')
                    if len(parts) == 3 and parts[2]:
                        selected.append(normals[int(parts[2]) - 1])
        if not selected:
            continue
        lights = [item_light(normalize(vector), False) for vector in ((.2, 1, -.7), (-.2, 1, .7))]
        prior_lights = [item_light(normalize(vector), True) for vector in ((.2, 1, -.7), (-.2, 1, .7))]
        errors, back_face_differences, prior_errors = [], [], []
        for normal in selected:
            x, y, z = project(normal, normal_transform)
            normal = normalize((x, -y, z))  # GUI pose flips the Y axis.
            expected = mix_light(normal, lights, *old_coefficients)
            actual = mix_light(normal, lights, *new_coefficients)
            prior = mix_light(normal, prior_lights, *new_coefficients)
            back_face = mix_light(tuple(-value for value in normal), lights, *new_coefficients)
            errors.append(abs(expected - actual))
            prior_errors.append(abs(expected - prior))
            back_face_differences.append(abs(expected - back_face))
        samples.append({'model': path.relative_to(ASSETS).as_posix(), 'lighting': 'ITEMS_FLAT',
                        'vertex_normal_occurrences': len(selected), 'maximum_restored_coefficient_error': max(errors),
                        'maximum_prior_side_light_coefficient_error': max(prior_errors),
                        'maximum_possible_back_face_coefficient_difference': max(back_face_differences)})
    assert samples and max(sample['maximum_restored_coefficient_error'] for sample in samples) < 1e-6
    report = {'passed': True, 'upstream_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=SOURCE, text=True).strip(),
              'reference_client': {'path': str(reference), 'version': '1.20.1', 'sha256': hashlib.sha256(reference.read_bytes()).hexdigest(),
                                   'sha1': reference_sha1, 'matches_official_download_hash': True},
              'current_client': {'path': str(modern), 'version': '26.1.2', 'sha256': hashlib.sha256(modern.read_bytes()).hexdigest()},
              'shader_coefficients': {'diffuse': old_coefficients[0], 'ambient': old_coefficients[1], 'alpha_cutout': .1},
              'gui_light_selection': {'upstream_uses_block_light': False, 'upstream_mode': 'ITEMS_FLAT',
                                      'current_json_gui_light': current_model['gui_light'], 'current_mode': 'ITEMS_FLAT',
                                      'prior_port_5_json_gui_light': 'omitted: default SIDE', 'prior_port_5_mode': 'ITEMS_3D',
                                      'upstream_and_current_match': True},
              'shipped_models_and_textures_unchanged': True, 'assets': assets, 'samples': samples,
              'normal_occurrences_compared': sum(sample['vertex_normal_occurrences'] for sample in samples),
              'maximum_restored_coefficient_error': max(sample['maximum_restored_coefficient_error'] for sample in samples),
              'scope': 'Checks actual model GUI-light selection against upstream usesBlockLight=false, then samples shader coefficients with normalized reference light directions. Previous port.5 checked both shader modes separately but failed to verify which mode was selected. This contract does not measure final GPU pixels; live reference-client screenshots are measured separately in icon-brightness-comparison.json.'}
    destination = ROOT / '.work/verification/icon-lighting-contract.json'
    destination.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(f"{len(assets)} unchanged models/textures; {report['normal_occurrences_compared']} normal/light samples; restored coefficient error {report['maximum_restored_coefficient_error']}; {destination}")
