"""Measure matching blade-body colors in three real, unedited F2 screenshots."""
from collections import Counter
import base64
import hashlib
import html
import json
from pathlib import Path
import statistics
from PIL import Image
from initialize_instance import ROOT

SOURCE = Path(__file__).resolve().parents[1]
SCREENSHOTS = {
    'original': ('原版 1.20.1 / 上游 0.1.2', '.work/test-original-client/screenshots/2026-10-08_15.02.29.png'),
    'port5': ('移植版 port.5 / 修正前', 'screenshots/2026-10-08_14.34.20.png'),
    'port6': ('移植版 port.6 / 修正后', 'screenshots/2026-10-08_14.45.15.png'),
}
FIXTURES = [('default', '无名刀', 0, 0), ('bamboo', '竹光', 0, 1),
            ('iron', '无名刀（铁）', 1, 1), ('white', '白鞘', 3, 1)]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def luma(rgb):
    # Display-code luma, not physical luminance or a monitor measurement.
    return sum(value * weight for value, weight in zip(rgb, (.2126, .7152, .0722)))


if __name__ == '__main__':
    records = []
    for name, label, column, row in FIXTURES:
        rectangle = [378 + 54 * column, 320 + 54 * row, 420 + 54 * column, 362 + 54 * row]
        values = {}
        for version, (_, relative) in SCREENSHOTS.items():
            with Image.open(ROOT / relative) as image:
                assert image.size == (1280, 720)
                pixels = list(image.convert('RGB').crop(rectangle).get_flattened_data())
            # Select the warm blade body; neutral slot background and the cyan /
            # purple durability ring are excluded. No enchanted fixtures used.
            body = [rgb for rgb in pixels if rgb[0] > rgb[1] * 1.15 and rgb[1] > rgb[2] * 1.2 and rgb[1] > 20]
            rgb, count = Counter(body).most_common(1)[0]
            values[version] = {'dominant_body_rgb': rgb, 'dominant_color_pixel_count': count,
                               'body_color_pixels': len(body), 'dominant_body_luma_255': luma(rgb),
                               'warm_body_mean_luma_255': statistics.mean(map(luma, body))}
        reference_value = values['original']['dominant_body_luma_255']
        for value in values.values():
            value['percent_of_original_dominant_body_luma'] = value['dominant_body_luma_255'] / reference_value * 100
        records.append({'fixture': name, 'label': label, 'rectangle_xyxy': rectangle, 'values': values})

    provenance = json.loads((ROOT / '.work/verification/original-reference-provenance.json').read_text('utf-8'))
    client_log = (ROOT / '.work/verification/client-original-reference.log').read_text('utf-8', errors='replace')
    server_log = (ROOT / '.work/verification/dedicated-server-original-reference.log').read_text('utf-8', errors='replace')
    assert 'Stopping!' in client_log and 'BUILD SUCCESSFUL' in client_log
    assert 'OriginalBladeDev joined the game' in server_log and 'All dimensions are saved' in server_log
    assert provenance['runtime_source_and_resources_unchanged'] and provenance['checked_files'] == 411
    original_jar = SOURCE.parent / 'SlashBlade_2-original-1.20.1/build/libs/SlashBlade-1.20.1-0.1.2.jar'
    assert original_jar.is_file()
    screenshot_records = {version: {'label': label, 'path': relative, 'sha256': sha(ROOT / relative)}
                          for version, (label, relative) in SCREENSHOTS.items()}
    report = {
        'passed': True, 'method': 'Same warm blade-body region: mode of selected RGB pixels, then Y-prime = .2126R + .7152G + .0722B.',
        'pixel_filter': 'R > 1.15G, G > 1.2B, G > 20; cyan/purple ring and gray background excluded.',
        'measure': '8-bit display-code luma, range 0 to 255; an image comparison, not a photometric measurement.',
        'settings': {'resolution': [1280, 720], 'actual_gui_scale': 3, 'gamma': .5, 'resource_packs': []},
        'screenshots': screenshot_records, 'samples': records,
        'reference': {'provenance_report': 'original-reference-provenance.json', 'runtime_files_checked': 411,
                      'mod_jar': str(original_jar), 'mod_jar_sha256': sha(original_jar),
                      'minecraft': '1.20.1', 'forge': '47.1.44', 'upstream_commit': provenance['upstream_commit'],
                      'runtime_source_and_resources_unchanged': True, 'normal_client_server_shutdown': True,
                      'logs': ['client-original-reference.log', 'dedicated-server-original-reference.log'],
                      'upstream_model_warning': 'Upstream JSON has a string-valued predicate and triggers its original model-load warning. BladeModel still renders OBJ icons, usesBlockLight=false, and does not delegate getTransforms. Original geometry is therefore larger and flatter; no reference runtime repair was applied.'},
        'port5_sha256': '9b8c28c63f059237fef1eb85ae685a268681a46061f30ba4eef9b03a41072a70',
        'port6_sha256': 'fbb73d374fc119d2952ea27f6ebacb366d4b3331cb617f014d3b2fd0e76db4a3',
        'root_cause': 'Upstream usesBlockLight=false selects front lighting. Port.5 omitted gui_light and selected default SIDE / ITEMS_3D. Port.6 explicitly selects front / ITEMS_FLAT.',
        'limits': ['Samples are principal warm body colors of four unenchanted, undamaged fixtures, not the mean of every icon pixel.',
                   'Original and modern GUI model size/rotation and sampling differ; the report does not claim pixel identity.',
                   'Texture-heavy blades, luminous pixels, durability color fixes and animated glint are not quantified by this brightness sample.',
                   'UI slot/background differences and monitor/HDR settings are outside the measured blade-body colors.'],
        'mod_only_fix': True, 'extra_render_passes_added_by_port6': 0,
    }
    output = ROOT / '.work/verification/icon-brightness-comparison.json'
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    panels = []
    for version, (label, relative) in SCREENSHOTS.items():
        encoded = base64.b64encode((ROOT / relative).read_bytes()).decode()
        panels.append(f'<section><h2>{html.escape(label)}</h2><div class="viewport"><img src="data:image/png;base64,{encoded}" alt="{html.escape(label)}的实际游戏截图"></div><a href="../{relative}">查看完整原始截图</a></section>')
    rows = []
    for item in records:
        cells = ''.join(f'<td>{item["values"][version]["dominant_body_luma_255"]:.1f}<small>{item["values"][version]["percent_of_original_dominant_body_luma"]:.1f}%</small></td>' for version in SCREENSHOTS)
        rows.append(f'<tr><th>{item["label"]}</th>{cells}</tr>')
    document = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>拔刀剑图标亮度实机对照</title><style>
body{margin:32px auto;padding:0 20px;max-width:1700px;background:#15181d;color:#e5e9ef;font:16px/1.6 system-ui,sans-serif}h1{font-size:26px}h2{font-size:18px}a{color:#89c4ff}.panels{display:flex;gap:18px;flex-wrap:wrap}.viewport{position:relative;width:540px;height:195px;overflow:hidden;background:#8b8b8b}.viewport img{position:absolute;left:-365px;top:-314px;width:1280px;height:720px;image-rendering:pixelated}table{border-collapse:collapse;width:min(900px,100%);margin:24px 0}th,td{padding:12px;border:1px solid #424853;text-align:left}small{display:block;color:#bcc5cf}code{background:#303640;padding:2px 6px}p{max-width:1000px}
</style><h1>拔刀剑图标亮度实机对照</h1>
<p>相同电脑，1280×720，界面缩放 3，游戏亮度 50%，没有资源包。下方显示三份未经调色的 F2 截图中的背包区域；原版模型本身更大。代码、贴图、截图哈希和日志保存在同目录的 JSON 报告中。</p>
<div class="panels">''' + ''.join(panels) + '''</div><table><thead><tr><th>样本</th><th>原版 1.20.1</th><th>移植 port.5</th><th>修正 port.6</th></tr></thead><tbody>''' + ''.join(rows) + '''</tbody></table>
<p>表格为主要刀身颜色的亮度值 Y′（0–255）及相对于原版的百分比。取同款刀的暖色刀身众数像素，排除灰色背景、青色／紫色耐久环；样本均无附魔。它衡量截图颜色，不能解释为整把刀平均亮度或显示器实测亮度。</p>
<p>偏暗原因：原版 <code>usesBlockLight=false</code> 使用正面光照；port.5 缺少 <code>gui_light</code>，采用默认 SIDE 侧面光照。port.6 明确设置 <code>gui_light:front</code>。本次只改模组，没有修改 Minecraft，也没有增加绘制层或后处理。</p>
<p>原版与移植版的旋转、缩放、纹理采样不同，因此保留少量像素差异。四个暖色刀身样本不能代表所有贴图、发光和动态附魔的逐像素一致性。上游原版 JSON 的字符串 predicate 会产生原有模型加载警告，本次保留了原始运行源码与资源。</p>
<p><a href="icon-brightness-comparison.json">完整测量报告</a> · <a href="original-reference-provenance.json">原版源码校验</a></p></html>'''
    (ROOT / '.work/verification/icon-brightness-comparison.html').write_text(document, encoding='utf-8')
    print('Real screenshot body-color luma comparison (original / port.5 / port.6):')
    for item in records:
        values = item['values']
        print(item['fixture'], '/'.join(f'{values[version]["dominant_body_luma_255"]:.1f}' for version in SCREENSHOTS),
              f'current {values["port6"]["percent_of_original_dominant_body_luma"]:.1f}% of original')
    print(output)
