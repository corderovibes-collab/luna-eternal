"""Convierte las cuatro piezas Pikachu de Blockbench a recursos GeckoLib.

Conserva la jerarquia y los pivotes del autor. Las texturas múltiples de una
pieza se reúnen en un atlas por fotograma para que GeckoLib pueda dibujarlas
con un único material, y los ocho fotogramas permanecen como flipbook.
"""
from __future__ import annotations

import base64
import io
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(r"D:\blockbench\kit_exclusivos\pikachu\armadura\pikachu_complete_animated_set_FINAL\armor")
ASSETS = ROOT / "addons/pikachu-armor/src/main/resources/assets/pikachuarmor"
PARTS = {
    "helmet": "pikachu_thunder_helmet.bbmodel",
    "chestplate": "pikachu_thunder_chestplate.bbmodel",
    "leggings": "pikachu_thunder_leggings.bbmodel",
    "boots": "pikachu_thunder_boots.bbmodel",
}
FRAMES = 8


def image(texture: dict) -> Image.Image:
    raw = base64.b64decode(texture["source"].split(",", 1)[1])
    return Image.open(io.BytesIO(raw)).convert("RGBA")


def atlas(textures: list[dict]) -> tuple[Image.Image, dict[int, int], int, int]:
    """Devuelve tira animada, desplazamientos U y tamaño de cada fotograma."""
    frames = []
    widths = [int(t.get("uv_width") or t["width"]) for t in textures]
    heights = [int(t.get("uv_height") or t["height"]) for t in textures]
    total_w, frame_h = sum(widths), max(heights)
    offsets, cursor = {}, 0
    for index, width in enumerate(widths):
        offsets[index] = cursor
        cursor += width
    sources = [image(t) for t in textures]
    for frame in range(FRAMES):
        canvas = Image.new("RGBA", (total_w, frame_h), (0, 0, 0, 0))
        for index, (src, width, height) in enumerate(zip(sources, widths, heights)):
            canvas.paste(src.crop((0, frame * height, width, (frame + 1) * height)),
                         (offsets[index], 0))
        frames.append(canvas)
    strip = Image.new("RGBA", (total_w, frame_h * FRAMES), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        strip.paste(frame, (0, index * frame_h))
    return strip, offsets, total_w, frame_h


def convert(part: str, filename: str) -> None:
    source = json.loads((SOURCE / filename).read_text(encoding="utf-8"))
    textures = source["textures"]
    strip, offsets, width, height = atlas(textures)
    elements = {e["uuid"]: e for e in source["elements"]}
    groups = {g["uuid"]: g for g in source["groups"]}
    bones = []

    def walk(node: dict, parent: str | None = None) -> None:
        group = groups[node["uuid"]]
        bone = {
            "name": group["name"],
            "pivot": group.get("origin", [0, 0, 0]),
        }
        if parent:
            bone["parent"] = parent
        rotation = group.get("rotation", [0, 0, 0])
        if any(abs(float(v)) > 1e-6 for v in rotation):
            bone["rotation"] = rotation
        cubes = []
        children = []
        for child in node.get("children", []):
            if isinstance(child, dict):
                children.append(child)
                continue
            e = elements.get(child)
            if not e or e.get("type", "cube") != "cube":
                continue
            cube = {
                "origin": e["from"],
                "size": [e["to"][i] - e["from"][i] for i in range(3)],
                "pivot": e.get("origin", group.get("origin", [0, 0, 0])),
            }
            if e.get("inflate"):
                cube["inflate"] = e["inflate"]
            if any(abs(float(v)) > 1e-6 for v in e.get("rotation", [0, 0, 0])):
                cube["rotation"] = e["rotation"]
            faces = {}
            for face_name, face in e.get("faces", {}).items():
                if face.get("texture") is None or not face.get("uv"):
                    continue
                tex = int(face["texture"])
                u0, v0, u1, v1 = face["uv"]
                faces[face_name] = {
                    "uv": [u0 + offsets[tex], v0],
                    "uv_size": [u1 - u0, v1 - v0],
                }
            cube["uv"] = faces
            cubes.append(cube)
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)
        for child in children:
            walk(child, group["name"])

    for node in source["outliner"]:
        if isinstance(node, dict):
            walk(node)

    description = {
        "identifier": f"geometry.pikachu_{part}",
        "texture_width": width,
        "texture_height": height,
        "visible_bounds_width": 4,
        "visible_bounds_height": 5,
        "visible_bounds_offset": [0, 1.25, 0],
    }
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": description, "bones": bones,
    }]}
    geo_dir = ASSETS / "geo/armor"
    tex_dir = ASSETS / "textures/armor"
    anim_dir = ASSETS / "animations/armor"
    geo_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    anim_dir.mkdir(parents=True, exist_ok=True)
    (geo_dir / f"pikachu_{part}.geo.json").write_text(
        json.dumps(geo, indent=2), encoding="utf-8")
    strip.save(tex_dir / f"pikachu_{part}.png")
    meta = {"animation": {"frametime": 2, "interpolate": False,
            "frames": [{"index": 0, "time": 5}, 1, 2, 3, 4, 5, 6, 7,
                       6, 5, 4, 3, 2, 1]}}
    (tex_dir / f"pikachu_{part}.png.mcmeta").write_text(
        json.dumps(meta, indent=2), encoding="utf-8")
    animation = {"format_version": "1.8.0", "animations": {
        "animation.pikachu_armor.idle": {"loop": True, "animation_length": 1.0}
    }}
    (anim_dir / f"pikachu_{part}.animation.json").write_text(
        json.dumps(animation, indent=2), encoding="utf-8")


def main() -> None:
    for part, filename in PARTS.items():
        convert(part, filename)
    lang = ASSETS / "lang"
    models = ASSETS / "models/item"
    lang.mkdir(parents=True, exist_ok=True)
    models.mkdir(parents=True, exist_ok=True)
    names = {
        "helmet": "Casco Pikachu Thunderforge",
        "chestplate": "Pechera Pikachu Thunderforge",
        "leggings": "Pantalones Pikachu Thunderforge",
        "boots": "Botas Pikachu Thunderforge",
    }
    (lang / "es_es.json").write_text(json.dumps({
        f"item.pikachuarmor.pikachu_{p}": n for p, n in names.items()
    }, indent=2, ensure_ascii=False), encoding="utf-8")
    for part in PARTS:
        (models / f"pikachu_{part}.json").write_text(
            '{"parent":"minecraft:item/generated"}', encoding="utf-8")
    print("Pikachu: 4 piezas GeckoLib y 4 flipbooks generados")


if __name__ == "__main__":
    main()
