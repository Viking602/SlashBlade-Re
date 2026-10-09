"""Extract locally resolved Java sources for version-accurate migration work."""
from pathlib import Path
from zipfile import ZipFile

instance = Path(__file__).resolve().parents[3]
cache = instance / '.work/devtools/gradle-cache/caches'
archives = list((cache / 'neoformruntime/intermediate_results').glob('applyNeoforgePatches_*_output.zip'))
archives += list((cache / 'modules-2/files-2.1/net.neoforged/neoforge/26.1.2.114').rglob('*-sources.jar'))
output = instance / '.work/devtools/api-sources'
for archive in archives:
    with ZipFile(archive) as package:
        for name in package.namelist():
            if name.endswith('.java') and not name.startswith('/') and '..' not in Path(name).parts:
                target = output / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(package.read(name))
    print(archive.name)
print(output)
