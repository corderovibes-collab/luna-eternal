"""Render the actual Lunita geometry, hierarchy, texture and animation channels for QA."""
import json
import sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from mecha import bedrock
from mecha.visor import Escena, dibujar, FONDO

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'mod/src/main/resources/assets/lunaeternal'
geo = json.loads((ASSETS / 'bedrock/pokemon/models/lunita/lunita.geo.json').read_text())
animations = json.loads((ASSETS / 'animations/lunita.animation.json').read_text())['animations']
texture = Image.open(ASSETS / 'textures/pokemon/lunita/lunita.png').convert('RGBA')
out = ROOT / 'build/lunita-preview'
out.mkdir(parents=True, exist_ok=True)

def sample(channel, time):
    if isinstance(channel, list):
        return channel
    keys = sorted((float(k), v) for k, v in channel.items())
    def point(value, side):
        return value.get(side, value.get('vector')) if isinstance(value, dict) else value
    if time <= keys[0][0]:
        return point(keys[0][1], 'post')
    for (a, va), (b, vb) in zip(keys, keys[1:]):
        if time <= b:
            p, q = np.array(point(va, 'post')), np.array(point(vb, 'pre'))
            return (p + (q-p)*(time-a)/(b-a)).tolist()
    return point(keys[-1][1], 'post')

def scene(animation, time, shoulder=0):
    channels = animations[f'animation.lunita.{animation}']['bones']
    bones = {b['name']: b for b in geo['minecraft:geometry'][0]['bones']}
    cache = {}
    def transform(name):
        if name in cache:
            return cache[name]
        bone = bones[name]
        current = channels.get(name, {})
        rotation = np.array(bone.get('rotation', [0, 0, 0]), float)
        rotation += np.array(sample(current['rotation'], time) if 'rotation' in current else [0, 0, 0])
        position = sample(current['position'], time) if 'position' in current else [0, 0, 0]
        if name == 'Lunita':
            position = [position[0]+shoulder, position[1], position[2]]
        own = bedrock.Afin(t=np.array(position)) @ bedrock.Afin.giro_en(bone.get('pivot', [0, 0, 0]), rotation)
        matrix = transform(bone['parent']) @ own if bone.get('parent') else own
        cache[name] = matrix
        return matrix
    result = Escena(geo)
    result.cubos = []
    for bone in bones.values():
        matrix = transform(bone['name'])
        for cube in bone.get('cubes', []):
            final = matrix
            if cube.get('rotation'):
                final = matrix @ bedrock.Afin.giro_en(cube.get('pivot', bone['pivot']), cube['rotation'])
            result.cubos.append((bone['name'], cube, final))
    return result

cases = [('Reposo', 'idle', 0, 0), ('Caminata A', 'walk', 0, 0),
         ('Caminata B', 'walk', .3572, 0), ('Saludo', 'greet', .25, 0),
         ('Hombro izquierdo', 'idle', 0, 5), ('Hombro derecho', 'idle', 0, -5)]
sheet = Image.new('RGBA', (720, len(cases)*310), FONDO)
draw = ImageDraw.Draw(sheet)
for row, (label, animation, time, shoulder) in enumerate(cases):
    current = scene(animation, time, shoulder)
    for col, angle in enumerate([35, 90]):
        view = dibujar(current, angle, 10, 360, 285, 13, texture, centro=(0, 8, 0))
        sheet.paste(view, (col*360, row*310+25))
    draw.text((15, row*310+7), label, fill=(235, 224, 255))
sheet.save(out / 'lunita-poses.png')
frames = [dibujar(scene('walk', i*.7143/24), 35, 10, 360, 310, 13, texture,
                 centro=(0, 8, 0)).convert('RGB') for i in range(24)]
frames[0].save(out / 'lunita-walk.gif', save_all=True, append_images=frames[1:], duration=40, loop=0)
print(out)
