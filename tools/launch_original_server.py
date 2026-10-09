"""Launch the prepared upstream Forge server with a usable console input pipe."""
import ctypes
from ctypes import wintypes
import os
from pathlib import Path
import subprocess
import sys
import threading
from initialize_instance import ROOT

source = Path(__file__).resolve().parents[1].parent / 'SlashBlade_2-original-1.20.1'
command = (ROOT / '.work/verification/original-server-commandline.txt').read_text('utf-8-sig').strip()
argument_count = ctypes.c_int()
parse = ctypes.windll.shell32.CommandLineToArgvW
parse.argtypes = [wintypes.LPCWSTR, ctypes.POINTER(ctypes.c_int)]
parse.restype = ctypes.POINTER(wintypes.LPWSTR)
parsed = parse(command, ctypes.byref(argument_count))
arguments = [parsed[i] for i in range(argument_count.value)]
ctypes.windll.kernel32.LocalFree(parsed)
for name in ('devtools', 'verification', 'test-original-server'):
    arguments = [argument.replace(str(ROOT / name), str(ROOT / '.work' / name)) for argument in arguments]
assert arguments[0].endswith('bin\\java.exe') and 'forgeserveruserdev' in arguments
environment = os.environ.copy()
environment['MOD_CLASSES'] = ';'.join('slashblade%%' + str(source / directory)
                                     for directory in ('build/resources/main', 'build/classes/java/main'))
with (ROOT / '.work/verification/dedicated-server-original-reference-console.log').open('w', encoding='utf-8') as log:
    process = subprocess.Popen(arguments, cwd=ROOT / '.work/test-original-server', env=environment,
                               stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
                               text=True, encoding='utf-8')
    print(f'Original Forge 1.20.1 server PID {process.pid}; type normal server commands', flush=True)
    def console():
        for line in sys.stdin:
            if process.poll() is not None:
                return
            process.stdin.write(line)
            process.stdin.flush()
    threading.Thread(target=console, daemon=True).start()
    code = process.wait()
    print(f'Original server exit code {code}', flush=True)
    raise SystemExit(code)
