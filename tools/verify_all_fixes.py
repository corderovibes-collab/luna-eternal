import subprocess
import sys

SSH_KEY = r"C:\Users\JUAN\.ssh\pokereport_node01_ed25519"
SERVER_HOST = "root@15.235.16.131"
CONTAINER_ID = "1f23bff0-bc4c-4c00-bbcf-2fe81ff666dd"
VOLUME_DIR = f"/var/lib/pterodactyl/volumes/{CONTAINER_ID}"

REMOTE_SCRIPT = f"""
import struct, zlib, io, json, zipfile
from pathlib import Path

CONTAINER_ID = "{CONTAINER_ID}"
VOLUME_DIR = "{VOLUME_DIR}"

RECEPTIONS = {{
    "brock": {{"cap": 15, "file": "kanto_brock.json"}},
    "misty": {{"cap": 19, "file": "kanto_misty.json"}},
    "surge": {{"cap": 24, "file": "kanto_ltsurge.json"}},
    "erika": {{"cap": 28, "file": "kanto_erika.json"}},
    "koga": {{"cap": 33, "file": "kanto_koga.json"}},
    "sabrina": {{"cap": 37, "file": "kanto_sabrina.json"}},
    "blaine": {{"cap": 42, "file": "kanto_blaine.json"}},
    "giovanni": {{"cap": 46, "file": "kanto_giovanni.json"}},
    "campeon_kanto": {{"cap": 63, "file": "kanto_champion_blue.json"}}
}}

print("=== 1. VERIFICACION DE TOPES EN DATAPACK ===")
dp_path = f"{{VOLUME_DIR}}/world/datapacks/COBBLEVERSE-RCT-DP-v20.zip"
z = zipfile.ZipFile(dp_path)
all_ok = True
for gid, info in RECEPTIONS.items():
    d = json.loads(z.read('data/rctmod/trainers/' + info['file']))
    levels = [p['level'] for p in d['team']]
    cap = info['cap']
    max_lvl = max(levels)
    ok = max_lvl <= cap
    if not ok: all_ok = False
    print(f"  {{gid:<15}} Tope: {{cap}} | Niveles: {{levels}} | Cumple: {{ok}}")
print(f"Resultado Datapack: {{'CORRECTO' if all_ok else 'FALLO'}}")

print("\\n=== 2. VERIFICACION ENTIDADES CIUDADELA (r.-1.0.mca) ===")
try:
    import nbtlib
    mca_path = f"{{VOLUME_DIR}}/world/dimensions/lunaeternal/ciudadela/entities/r.-1.0.mca"
    with open(mca_path, 'rb') as f:
        data = f.read()
    header = data[:4096]
    trainers = []
    pokemon = []
    carteles = []
    for cz in range(32):
        for cx in range(32):
            idx = 4 * (cx + cz * 32)
            offset = (header[idx] << 16) | (header[idx+1] << 8) | header[idx+2]
            if offset == 0: continue
            sec_start = offset * 4096
            length = struct.unpack('>I', data[sec_start:sec_start+4])[0]
            compressed = data[sec_start+5:sec_start+4+length]
            raw = zlib.decompress(compressed)
            nbt = nbtlib.File.parse(io.BytesIO(raw))
            for ent in nbt.get('Entities', []):
                eid = str(ent.get('id', ''))
                pos = [round(float(p), 2) for p in ent.get('Pos', [0,0,0])]
                if eid == 'rctmod:trainer':
                    trainers.append((str(ent.get('TrainerId', '')), pos))
                elif eid == 'cobblemon:pokemon':
                    pokemon.append((str(ent.get('Species', '')), pos))
                elif eid == 'minecraft:text_display':
                    txt = str(ent.get('text', ''))[:35].replace('\\n', ' ')
                    carteles.append((txt, pos))

    print(f"Total Entrenadores encontrados en Ciudadela: {{len(trainers)}}")
    for t in trainers:
        print(f"  Entrenador: {{t[0]}} en {{t[1]}}")
    print(f"Total Pokemon decorativos: {{len(pokemon)}}")
    for p in pokemon:
        print(f"  Pokemon: {{p[0]}} en {{p[1]}}")
    print(f"Total Carteles (TextDisplay): {{len(carteles)}}")
    for c in carteles:
        print(f"  Cartel: {{c[0]}}... en {{c[1]}}")
except Exception as e:
    print(f"Error inspeccionando MCA: {{e}}")

"""

def main():
    p = subprocess.Popen([
        "ssh", "-i", SSH_KEY, "-o", "StrictHostKeyChecking=no",
        SERVER_HOST, "cat > /tmp/verify_remote.py && python3 /tmp/verify_remote.py"
    ], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    out, err = p.communicate(input=REMOTE_SCRIPT)
    print(out)
    if err:
        print("STDERR:", err)

if __name__ == "__main__":
    main()
