"""Condense javac diagnostics without discarding the complete saved build log."""
from pathlib import Path
import re
import sys
from collections import Counter

path = Path(sys.argv[1])
text = path.read_text('utf-8', errors='replace')
matches = list(re.finditer(r'(?m)^\s*(D:[^\n]+?\.java):(\d+): (?:错误|error): ([^\n]+)', text))
errors = []
for i, match in enumerate(matches):
    end = matches[i+1].start() if i+1 < len(matches) else len(text)
    detail = text[match.end():end].splitlines()[:5]
    if not any(error['file'] == match.group(1) and error['line'] == int(match.group(2)) and error['message'] == match.group(3) for error in errors):
        errors.append({'file':match.group(1), 'line':int(match.group(2)), 'message':match.group(3), 'detail':detail})
print(f'{len(errors)} compiler diagnostics in {len(set(error["file"] for error in errors))} files')
if len(sys.argv) < 3:
    for file, count in Counter(Path(error['file']).name for error in errors).most_common():
        print(f'{count:4} {file}')
if len(sys.argv) > 2:
    name = sys.argv[2]
    for error in errors:
        if name in error['file']:
            print(f"\n{Path(error['file']).name}:{error['line']}: {error['message']}")
            print('\n'.join(error['detail']))
