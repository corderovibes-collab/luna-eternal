"""Patch only Lunita into a live jar, preserving every unrelated entry byte-for-byte."""
import hashlib
import copy
import json
import sys
import zipfile
from pathlib import Path

base, compiled, output = map(Path, sys.argv[1:4])
def selected(name):
    return name in {
        'net/pokereport/luna/lunita/LunitaEntity.class',
        'net/pokereport/luna/lunita/LunitaEntity$1.class',
        'net/pokereport/luna/lunita/LunitaEntity$2.class',
        'net/pokereport/luna/lunita/LunitaManager.class',
        'net/pokereport/luna/lunita/LunitaAssets.class',
        'net/pokereport/luna/lunita/LunitaPokemon.class',
        'net/pokereport/luna/client/lunita/LunitaModel.class',
        'assets/lunaeternal/geo/lunita.geo.json',
        'assets/lunaeternal/animations/lunita.animation.json',
        'assets/lunaeternal/textures/entity/lunita.png',
        'assets/lunaeternal/bedrock/pokemon/posers/lunita.json',
        'assets/lunaeternal/textures/pokemon/lunita/lunita.png',
        'data/lunaeternal/species/custom/lunita.json',
        'data/lunaeternal/dex_entries/pokemon/lunita.json',
        'data/lunaeternal/dexes/lunita.json',
    } or any(name.startswith(prefix) for prefix in (
        'assets/lunaeternal/bedrock/pokemon/models/lunita/',
        'assets/lunaeternal/bedrock/pokemon/animations/lunita/',
        'assets/lunaeternal/bedrock/pokemon/resolvers/lunita/',
    ))

languages = {f'assets/lunaeternal/lang/{language}.json' for language in ['en_us', 'es_es', 'es_mx']}
memorial_keys = {'lunaeternal.species.lunita.name', 'lunaeternal.species.lunita.desc', 'cobblemon.ui.pokedex.region.lunita'}

with zipfile.ZipFile(base) as old, zipfile.ZipFile(compiled) as new:
    replacements = {name: new.read(name) for name in new.namelist() if selected(name)}
    assert len(replacements) >= 7, replacements.keys()
    assert 'net/pokereport/luna/lunita/LunitaAssets.class' in replacements
    for name in languages:
        language = json.loads(old.read(name)) if name in old.namelist() else {}
        updates = json.loads(new.read(name))
        language.update({key: updates[key] for key in memorial_keys})
        replacements[name] = (json.dumps(language, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as result:
        for info in old.infolist():
            if not selected(info.filename) and info.filename not in languages:
                result.writestr(copy.copy(info), old.read(info.filename))
        for name, data in replacements.items():
            result.writestr(name, data)
    with zipfile.ZipFile(output) as result:
        assert result.testzip() is None
        preserved = 0
        for name in old.namelist():
            if name in languages:
                before, after = json.loads(old.read(name)), json.loads(result.read(name))
                assert {k: v for k, v in before.items() if k not in memorial_keys} == {k: v for k, v in after.items() if k not in memorial_keys}
            elif not selected(name):
                assert result.read(name) == old.read(name), name
                preserved += 1
print(f'Patched {len(replacements)} entries; preserved {preserved} unrelated entries.')
print('SHA256:', hashlib.sha256(output.read_bytes()).hexdigest())
output.with_suffix('.audit.json').write_text(json.dumps({'base_sha256': hashlib.sha256(base.read_bytes()).hexdigest(),
    'patched_sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
    'preserved_entries': preserved, 'changed_entries': sorted(replacements)}, indent=2) + '\n')
