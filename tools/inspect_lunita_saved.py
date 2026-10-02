"""Read saved entity regions without loading chunks or changing the world."""
import io
import gzip
import json
import sys
import zlib
from pathlib import Path
import nbtlib

found = []
for region in sorted(Path(sys.argv[1]).glob('*.mca')):
    data = region.read_bytes()
    for index in range(1024):
        offset = int.from_bytes(data[index * 4:index * 4 + 3], 'big') * 4096
        if not offset:
            continue
        length = int.from_bytes(data[offset:offset + 4], 'big')
        compression = data[offset + 4] & 127
        payload = data[offset + 5:offset + 4 + length]
        if compression == 2:
            payload = zlib.decompress(payload)
        elif compression == 1:
            payload = gzip.decompress(payload)
        elif compression != 3:
            continue
        if b'lunita' not in payload:
            continue
        chunk = nbtlib.File.parse(io.BytesIO(payload))
        for entity in chunk.get('Entities', []):
            if str(entity.get('id', '')) != 'lunaeternal:lunita':
                continue
            found.append({'region': region.name, 'pos': [float(v) for v in entity['Pos']],
                          'home': int(entity['luna_home']) if 'luna_home' in entity else None,
                          'state': str(entity.get('luna_state', ''))})
print(json.dumps({'saved_lunita': found}, indent=2))
