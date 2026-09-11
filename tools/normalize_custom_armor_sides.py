"""Normalize custom armor anchor names to Minecraft's BipedEntityModel sides.

The source models for Magikarp and Eeveelution used the visual left/right names
opposite to Minecraft's contract.  This preserves every cube, UV and animation
while swapping only renderer anchor names and parent references.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SWAP = {
    "rightArm": "leftArm",
    "leftArm": "rightArm",
    "armorRightArm": "armorLeftArm",
    "armorLeftArm": "armorRightArm",
    "rightLeg": "leftLeg",
    "leftLeg": "rightLeg",
    "armorRightLeg": "armorLeftLeg",
    "armorLeftLeg": "armorRightLeg",
    "armorRightBoot": "armorLeftBoot",
    "armorLeftBoot": "armorRightBoot",
}

FILES = [
    ROOT / "addons/magikarp-armor/src/main/resources/assets/magikarparmor/geo/armor" / name
    for name in ("magikarp_boots.geo.json", "magikarp_chestplate.geo.json", "magikarp_leggings.geo.json")
] + [
    ROOT / "addons/eeveelution-armor/src/main/resources/assets/eeveelution/geo/armor" / name
    for name in ("eeveelution_boots.geo.json", "eeveelution_chestplate.geo.json", "eeveelution_leggings.geo.json")
]


def normalize(path: Path) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    changed = 0
    for geometry in data["minecraft:geometry"]:
        for bone in geometry.get("bones", []):
            old_name = bone.get("name")
            new_name = SWAP.get(old_name, old_name)
            if new_name != old_name:
                bone["name"] = new_name
                changed += 1
            old_parent = bone.get("parent")
            if old_parent in SWAP:
                bone["parent"] = SWAP[old_parent]
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{path.name}: normalized {changed} anchors")


for file in FILES:
    normalize(file)
