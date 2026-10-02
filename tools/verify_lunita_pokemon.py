"""Check the complete memorial species against Cobblemon 1.8.0, not hardcoded recollections."""
import copy
import json
import sys
import zipfile
from pathlib import Path
from PIL import Image
import numpy as np
from mecha import bedrock

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'mod/src/main/resources'
ASSETS = RES / 'assets/lunaeternal'
species = json.loads((RES / 'data/lunaeternal/species/custom/lunita.json').read_text())
with zipfile.ZipFile(sys.argv[1]) as jar:
    eevee = json.loads(jar.read('data/cobblemon/species/generation1/eevee.json'))
    sylveon = json.loads(jar.read('data/cobblemon/species/generation6/sylveon.json'))
    originals = json.loads(jar.read('assets/cobblemon/bedrock/pokemon/animations/0133_eevee/eevee.animation.json'))['animations']
assert species['abilities'] == eevee['abilities']
assert set(eevee['moves']).issubset(species['moves'])
assert species['baseStats'] == sylveon['baseStats']
assert sum(species['baseStats'].values()) == 525
assert species['primaryType'] == 'fairy' and species['secondaryType'] == 'psychic'
assert species['shoulderMountable'] and species['maleRatio'] == 0
assert species['eggGroups'] == ['undiscovered'] and not species['evolutions'] and not species['forms']
assert not any('lunita' in str(p).lower() for p in RES.glob('data/**/spawn_pool_world/**/*.json'))
poser = json.loads((ASSETS / 'bedrock/pokemon/posers/lunita.json').read_text())
geometry = json.loads((ASSETS / 'bedrock/pokemon/models/lunita/lunita.geo.json').read_text())
guardian = json.loads((ASSETS / 'geo/lunita.geo.json').read_text())
animations = json.loads((ASSETS / 'bedrock/pokemon/animations/lunita/lunita.animation.json').read_text())['animations']
bones = {b['name']: b for b in geometry['minecraft:geometry'][0]['bones']}
assert poser['rootBone'] in bones
# JsonModelAdapter registers the selected root as __root and only descendants by name.
registered = {'__root'} | (set(bones) - {poser['rootBone']})
for pose in poser['poses'].values():
    for part in pose.get('transformedParts', []):
        assert part['part'] in registered, part['part']
for name, original in originals.items():
    assert animations[name.replace('animation.eevee.', 'animation.lunita.')] == original
for animation in animations.values():
    assert set(animation.get('bones', {})).issubset(bones)
left, right = (poser['poses'][name] for name in ['shoulder_left', 'shoulder_right'])
assert left['poseTypes'] == ['SHOULDER_LEFT'] and right['poseTypes'] == ['SHOULDER_RIGHT']
assert left['transformedParts'][0]['part'] == right['transformedParts'][0]['part'] == '__root'
assert left['transformedParts'][0]['position'] == [5, 0, 0]
assert right['transformedParts'][0]['position'] == [-5, 0, 0]
assert any('quadruped_walk(0.66, 1.4' in animation for animation in poser['poses']['walking']['animations'])
for original, compiled in zip(guardian['minecraft:geometry'][0]['bones'], geometry['minecraft:geometry'][0]['bones']):
    expected = copy.deepcopy(compiled)
    for a, b in zip(original.get('cubes', []), expected.get('cubes', [])):
        b['uv'] = a['uv']
    assert expected == original
atlas = Image.open(ASSETS / 'textures/pokemon/lunita/lunita.png').convert('RGBA')
original_png = Image.open(ASSETS / 'textures/entity/lunita.png').convert('RGBA')
assert atlas.crop((0, 0, 128, 64)).tobytes() == original_png.tobytes()
description = geometry['minecraft:geometry'][0]['description']
assert atlas.size == (description['texture_width'], description['texture_height'])

# Facial details retain precisely their exported rest coordinates after reparenting.
source = json.loads(Path(sys.argv[2]).read_text()) if len(sys.argv) > 2 else None
if source:
    old = source['minecraft:geometry'][0]['bones']
    new = guardian['minecraft:geometry'][0]['bones']
    old_matrices, new_matrices = bedrock.transformaciones(old), bedrock.transformaciones(new)
    old_root = next(b for b in old if b['name'] == 'Lunita')
    for original, name in zip(old_root['cubes'], ['lunita_nose', 'lunita_chin_fur']):
        compiled = next(b for b in new if b['name'] == name)['cubes'][0]
        a = old_matrices['Lunita'] @ bedrock.Afin.giro_en(original.get('pivot', [0, 0, 0]), original.get('rotation', [0, 0, 0]))
        b = new_matrices[name] @ bedrock.Afin.giro_en(compiled.get('pivot', [0, 0, 0]), compiled.get('rotation', [0, 0, 0]))
        for p, q in zip(bedrock.esquinas(original['origin'], original['size']), bedrock.esquinas(compiled['origin'], compiled['size'])):
            assert np.allclose(a(p), b(q), atol=1e-8)
print('PASS: Eevee abilities/moves/animations/sound preserved; Fairy/Psychic; shoulders, exclusive acquisition, facial attachment and UV atlas checked.')

