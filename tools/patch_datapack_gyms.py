import zipfile
import json
import io
import os
import sys

TARGET_LEVELS = {
    "kanto_brock.json": {"cap": 15, "last": "onix", "support_lvl": 14, "ace_lvl": 15},
    "kanto_misty.json": {"cap": 19, "last": "starmie", "support_lvl": 18, "ace_lvl": 19},
    "kanto_ltsurge.json": {"cap": 24, "last": "raichu", "support_lvl": 23, "ace_lvl": 24},
    "kanto_erika.json": {"cap": 28, "last": "vileplume", "support_lvl": 27, "ace_lvl": 28},
    "kanto_koga.json": {"cap": 33, "last": "weezing", "support_lvl": 32, "ace_lvl": 33},
    "kanto_sabrina.json": {"cap": 37, "last": "alakazam", "support_lvl": 36, "ace_lvl": 37},
    "kanto_blaine.json": {"cap": 42, "last": "arcanine", "support_lvl": 41, "ace_lvl": 42},
    "kanto_giovanni.json": {"cap": 46, "last": "mewtwo", "support_lvl": 45, "ace_lvl": 46},
    "kanto_champion_blue.json": {"cap": 63, "last": "ditto", "support_lvl": 62, "ace_lvl": 63}
}

def patch_datapack(zip_path, out_path):
    print(f"Reading {zip_path}...")
    with zipfile.ZipFile(zip_path, 'r') as zin:
        with zipfile.ZipFile(out_path, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                content = zin.read(item.filename)
                fname = item.filename.split('/')[-1]
                if item.filename.startswith("data/rctmod/trainers/") and fname in TARGET_LEVELS:
                    spec = TARGET_LEVELS[fname]
                    try:
                        data = json.loads(content)
                        team = data.get("team", [])
                        n = len(team)
                        for i, p in enumerate(team):
                            sp = p.get("species", "").lower()
                            if sp == spec["last"] or i == n - 1:
                                p["level"] = spec["ace_lvl"]
                            else:
                                p["level"] = spec["support_lvl"]
                        print(f"Patched {fname}: {[p.get('species') + ':' + str(p.get('level')) for p in team]}")
                        content = json.dumps(data, indent=2).encode('utf-8')
                    except Exception as e:
                        print(f"Error patching {fname}: {e}")
                zout.writestr(item, content)
    print(f"Patched zip saved to {out_path}")

if __name__ == "__main__":
    if len(sys.argv) > 2:
        patch_datapack(sys.argv[1], sys.argv[2])
    else:
        print("Usage: python patch_datapack_gyms.py <in.zip> <out.zip>")
