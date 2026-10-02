"""Compile GeckoLib box UVs for Cobblemon's different fractional-size UV layout.

Geometry and the original PNG stay untouched. Only six fractional cubes get
dedicated atlas regions, preventing their correction from repainting other parts.
"""
import copy
import math
from PIL import Image

def faces(u, v, size):
    w, h, d = size
    return [(u, v+d, u+d, v+d+h), (u+d, v+d, u+d+w, v+d+h),
            (u+d+w, v+d, u+2*d+w, v+d+h), (u+2*d+w, v+d, u+2*d+2*w, v+d+h),
            (u+d, v, u+d+w, v+d), (u+d+w, v, u+d+2*w, v+d)]

def compile_atlas(geometry, source_png, destination):
    result = copy.deepcopy(geometry)
    original = Image.open(source_png).convert('RGBA')
    atlas = Image.new('RGBA', (128, 256), (0, 0, 0, 0))
    atlas.paste(original, (0, 0))
    x, y, row_height = 2, 66, 0
    converted = 0
    for bone in result['minecraft:geometry'][0]['bones']:
        for cube in bone.get('cubes', []):
            size = cube['size']
            if all(abs(v-round(v)) < 1e-8 for v in size):
                continue
            width = math.ceil(2*(size[0]+size[2])) + 4
            height = math.ceil(size[1]+size[2]) + 4
            if x + width > 128:
                x, y, row_height = 2, y+row_height, 0
            assert y+height <= 256
            source_faces = faces(*cube['uv'], [math.floor(v) for v in size])
            target_faces = faces(x, y, size)
            for source_rect, target_rect in zip(source_faces, target_faces):
                su0, sv0, su1, sv1 = source_rect
                tu0, tv0, tu1, tv1 = target_rect
                if su0 >= su1 or sv0 >= sv1 or tu0 >= tu1 or tv0 >= tv1:
                    continue
                for py in range(math.floor(tv0), math.ceil(tv1)):
                    if not tv0 <= py+.5 < tv1:
                        continue
                    sy = min(sv1-1, math.floor(sv0+(py+.5-tv0)/(tv1-tv0)*(sv1-sv0)))
                    for px in range(math.floor(tu0), math.ceil(tu1)):
                        if not tu0 <= px+.5 < tu1:
                            continue
                        sx = min(su1-1, math.floor(su0+(px+.5-tu0)/(tu1-tu0)*(su1-su0)))
                        atlas.putpixel((px, py), original.getpixel((sx, sy)))
            cube['uv'] = [x, y]
            x += width
            row_height = max(row_height, height)
            converted += 1
    used_height = 128 if y+row_height <= 128 else 256
    atlas = atlas.crop((0, 0, 128, used_height))
    atlas.save(destination)
    description = result['minecraft:geometry'][0]['description']
    description['texture_width'], description['texture_height'] = atlas.size
    print(f'Compiled UV atlas: {converted} fractional cubes, {atlas.size[0]}x{atlas.size[1]}; 82 integer cubes preserved.')
    return result
