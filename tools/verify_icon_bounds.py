"""Project every shipped blade icon vertex into the 26.1 GUI atlas slot."""
import json
import math
from pathlib import Path
import subprocess
import argparse

from initialize_instance import ROOT


SOURCE = Path(__file__).resolve().parents[1]
ASSETS = SOURCE / 'src/main/resources/assets/slashblade'


def groups(path):
    vertices, result, group = [], {}, None
    for line in path.read_text('utf-8').splitlines():
        tokens = line.split()
        if not tokens:
            continue
        if tokens[0] == 'v':
            vertices.append(tuple(map(float, tokens[1:4])))
        elif tokens[0] in ('g', 'o'):
            group = tokens[1]
        elif tokens[0] == 'f':
            result.setdefault(group, []).extend(vertices[int(token.split('/')[0]) - 1] for token in tokens[1:])
    return result


def project(vertex, gui):
    # ItemTransform applies rotationXYZ (Rx * Ry * Rz); TEISR's 0.5
    # translation cancels the display transform's -0.5 translation.
    rx, ry, rz = map(math.radians, gui['rotation'])
    x, y, z = vertex
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    return tuple(gui['translation'][i] / 16 + gui['scale'][i] * .008 * value for i, value in enumerate((x, y, z)))


def fitted(points, reference, gui, span):
    reference = [project(vertex, gui) for vertex in reference]
    lower = [min(vertex[i] for vertex in reference) for i in (0, 1)]
    upper = [max(vertex[i] for vertex in reference) for i in (0, 1)]
    scale = span / max(upper[i] - lower[i] for i in (0, 1))
    center = [(upper[i] + lower[i]) / 2 for i in (0, 1)]
    return [tuple((vertex[i] - center[i]) * scale for i in (0, 1))
            for vertex in (project(point, gui) for point in points)]


def measure(gui, normalize=False):
    durability = groups(ASSETS / 'model/util/durability.obj')
    results = []
    gauge_reference = [(x, y, z + .1 - (0 if name == 'base' else 2 * endpoint))
                       for name in ('base', 'color', 'color_r') for x, y, z in durability[name] for endpoint in (0, 1)]
    for path in (ASSETS / 'model').rglob('*.obj'):
        model = groups(path)
        for target in ('item_blade', 'item_bladens', 'item_damaged'):
            if target not in model:
                continue
            points = model[target] + model.get(target + '_luminous', [])
            for damage in (0, .25, .5, .75, .99, 1):
                color = 'color_r' if target == 'item_damaged' else 'color'
                gauge = [(x, y, z + .1) for x, y, z in durability['base']]
                gauge += [(x, y, z + .1 - 2 * damage) for x, y, z in durability[color]]
                blade = fitted(points, points, gui, 15.2 / 16) if normalize else [project(vertex, gui) for vertex in points]
                gauge = fitted(gauge, gauge_reference, gui, 12 / 16) if normalize else [project(vertex, gui) for vertex in gauge]
                projected = blade + gauge
                lower = [min(vertex[i] for vertex in projected) for i in (0, 1)]
                upper = [max(vertex[i] for vertex in projected) for i in (0, 1)]
                results.append({'model': str(path.relative_to(ASSETS)), 'target': target, 'damage': damage,
                                'min_xy': lower, 'max_xy': upper,
                                'outside_slot_vertices': sum(any(abs(value) > .5 for value in vertex[:2]) for vertex in projected),
                                'blade_width_pixels': (max(vertex[0] for vertex in blade) - min(vertex[0] for vertex in blade)) * 16,
                                'blade_height_pixels': (max(vertex[1] for vertex in blade) - min(vertex[1] for vertex in blade)) * 16,
                                'edge_margin_pixels': min(*(value + .5 for value in lower), *(.5 - value for value in upper)) * 16})
    return {'gui': gui, 'combinations': len(results), 'outside_slot_vertices': sum(result['outside_slot_vertices'] for result in results),
            'minimum_edge_margin_pixels': min(result['edge_margin_pixels'] for result in results), 'results': results}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=ROOT / '.work/verification/player-animation-20261008/icon-bounds-port10.json')
    args = parser.parse_args()
    current = json.loads((ASSETS / 'models/item/slashblade.json').read_text('utf-8'))['display']['gui']
    original = json.loads(subprocess.check_output(['git', 'show', 'HEAD:src/main/resources/assets/slashblade/models/item/slashblade.json'], cwd=SOURCE, text=True))['display']['gui']
    prior_file = ROOT / '.work/verification/player-animation-20261008/source-before-icon/src/main/resources/assets/slashblade/models/item/slashblade.json'
    prior = json.loads(prior_file.read_text('utf-8'))['display']['gui']
    report = {'method': 'Independent numeric projection of shipped OBJ icon and luminous vertices; fit the active blade variant to 15.2 pixels and the gauge to 12 pixels using actual GUI transform. Gauge reference includes both damage depth endpoints.',
              'scope': 'Shipped models; does not assert GPU pixels or third-party replacement models.',
              'upstream': measure(original), 'before': measure(prior), 'after': measure(current, normalize=True)}
    assert report['upstream']['outside_slot_vertices'] > 0, 'The regression fixture must reproduce clipping'
    assert report['after']['outside_slot_vertices'] == 0, 'A blade icon extends beyond its GUI atlas slot'
    assert report['after']['minimum_edge_margin_pixels'] >= .399, 'Keep a sampling margin around all icons'
    for before, after in zip(report['before']['results'], report['after']['results']):
        assert after['blade_width_pixels'] > before['blade_width_pixels'], 'Blade width did not increase'
        assert after['blade_height_pixels'] > before['blade_height_pixels'], 'Blade height did not increase'
    report['passed'] = True
    destination = args.output
    destination.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(f"{report['after']['combinations']} model/state combinations are larger and fit; minimum margin {report['after']['minimum_edge_margin_pixels']:.3f} GUI pixels; {destination}")
