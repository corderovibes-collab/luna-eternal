import subprocess
import os
import sys
import time
import zlib
import struct
import io
import math
from pathlib import Path
import nbtlib

SSH_KEY = r"C:\Users\JUAN\.ssh\pokereport_node01_ed25519"
SERVER_HOST = "root@15.235.16.131"
CONTAINER_ID = "1f23bff0-bc4c-4c00-bbcf-2fe81ff666dd"
VOLUME_DIR = f"/var/lib/pterodactyl/volumes/{CONTAINER_ID}"

RECEPTIONS = {
    "brock": {
        "lider": (-137.95, 69.0, 49.37),
        "pokemon": (-137.544, 69.0, 52.0),
        "trainer_id": "kanto_brock"
    },
    "misty": {
        "lider": (-137.55, 69.09, 58.85),
        "pokemon": (-137.61, 69.09, 60.39),
        "trainer_id": "kanto_misty"
    },
    "surge": {
        "lider": (-137.57, 69.0, 66.53),
        "pokemon": (-137.92, 69.0, 68.02),
        "trainer_id": "kanto_ltsurge"
    },
    "erika": {
        "lider": (-150.543, 69.0, 49.50),
        "pokemon": (-150.64, 69.0, 51.60),
        "trainer_id": "kanto_erika"
    },
    "koga": {
        "lider": (-150.76, 69.0, 59.51),
        "pokemon": (-150.88, 69.0, 61.44),
        "trainer_id": "kanto_koga"
    },
    "sabrina": {
        "lider": (-150.58, 69.0, 66.65),
        "pokemon": (-150.661, 69.0, 68.495),
        "trainer_id": "kanto_sabrina"
    },
    "blaine": {
        "lider": (-145.206, 69.0, 66.93),
        "pokemon": (-143.267, 69.0, 67.05),
        "trainer_id": "kanto_blaine"
    },
    "giovanni": {
        "lider": (-145.84, 79.0, 71.27),
        "pokemon": (-143.296, 79.0, 70.52),
        "trainer_id": "kanto_giovanni"
    },
    "campeon_kanto": {
        "lider": (-145.22, 81.0, 47.52),
        "pokemon": (-142.902, 81.0, 47.94),
        "trainer_id": "kanto_champion_blue"
    }
}

def dist_sq(p1, p2):
    return (p1[0] - p2[0])**2 + (p1[1] - p2[1])**2 + (p1[2] - p2[2])**2

def deduplicate_ciudadela_mca(in_path, out_path):
    with open(in_path, 'rb') as f:
        data = bytearray(f.read())

    header = data[:4096]
    seen_leaders = set()
    seen_pokemon = set()
    seen_carteles = set()

    for cz in range(32):
        for cx in range(32):
            idx = 4 * (cx + cz * 32)
            offset = (header[idx] << 16) | (header[idx+1] << 8) | header[idx+2]
            sector_count = header[idx+3]
            if offset == 0: continue
            sec_start = offset * 4096
            length = struct.unpack('>I', data[sec_start:sec_start+4])[0]
            compressed = data[sec_start+5:sec_start+4+length]
            raw = zlib.decompress(compressed)
            nbt_file = nbtlib.File.parse(io.BytesIO(raw))

            if 'Entities' in nbt_file and len(nbt_file['Entities']) > 0:
                orig_len = len(nbt_file['Entities'])
                kept = []

                for ent in nbt_file['Entities']:
                    eid = str(ent.get('id', ''))
                    tags = [str(t) for t in ent.get('Tags', [])]
                    pos = [float(p) for p in ent.get('Pos', [0, 0, 0])]

                    # 1. TrainerMob
                    if eid == 'rctmod:trainer':
                        tid = str(ent.get('TrainerId', ''))
                        matched_leader = None
                        for gid, r in RECEPTIONS.items():
                            if r['trainer_id'] == tid or f'luna_gym_{gid}' in tags or dist_sq(pos, r['lider']) < 4.0:
                                matched_leader = gid
                                break
                        
                        if matched_leader:
                            if matched_leader not in seen_leaders:
                                ent['CustomNameVisible'] = nbtlib.Byte(0)
                                if 'CustomName' in ent:
                                    del ent['CustomName']
                                kept.append(ent)
                                seen_leaders.add(matched_leader)
                                print(f"  [Chunk {cx},{cz}] Kept 1 {matched_leader} TrainerMob at {pos}")
                            else:
                                print(f"  [Chunk {cx},{cz}] Discarded duplicate {matched_leader} TrainerMob at {pos}")
                        else:
                            kept.append(ent)

                    # 2. Pokémon decorativo
                    elif eid == 'cobblemon:pokemon':
                        if 'luna_decorativo' in tags or any(dist_sq(pos, r['pokemon']) < 4.0 for r in RECEPTIONS.values()):
                            matched_poke = None
                            for gid, r in RECEPTIONS.items():
                                if dist_sq(pos, r['pokemon']) < 4.0:
                                    matched_poke = gid
                                    break
                            
                            if matched_poke:
                                if matched_poke not in seen_pokemon:
                                    kept.append(ent)
                                    seen_pokemon.add(matched_poke)
                                    print(f"  [Chunk {cx},{cz}] Kept 1 {matched_poke} decorative Pokemon at {pos}")
                                else:
                                    print(f"  [Chunk {cx},{cz}] Discarded duplicate {matched_poke} Pokemon at {pos}")
                            else:
                                kept.append(ent)
                        else:
                            kept.append(ent)

                    # 3. TextDisplay
                    elif eid == 'minecraft:text_display':
                        matched_cartel = None
                        for gid, r in RECEPTIONS.items():
                            cartel_pos = (r['lider'][0], r['lider'][1] + 2.45, r['lider'][2])
                            if f'luna_cartel_{gid}' in tags or dist_sq(pos, cartel_pos) < 6.0:
                                matched_cartel = gid
                                break
                        
                        if matched_cartel:
                            txt = str(ent.get('text', ''))
                            bg = int(ent.get('background', 0))
                            # Purge if legacy or missing gold leader title
                            if bg == 0x40000000 or ('✦ LÍDER' not in txt and '✦ CAMPEÓN' not in txt):
                                print(f"  [Chunk {cx},{cz}] Purged legacy cartel for {matched_cartel} at {pos}")
                                continue
                            if matched_cartel not in seen_carteles:
                                kept.append(ent)
                                seen_carteles.add(matched_cartel)
                                print(f"  [Chunk {cx},{cz}] Kept 1 clean Cartel for {matched_cartel} at {pos}")
                            else:
                                print(f"  [Chunk {cx},{cz}] Discarded duplicate Cartel for {matched_cartel} at {pos}")
                        else:
                            kept.append(ent)

                    else:
                        kept.append(ent)

                if len(kept) != orig_len:
                    print(f"Chunk ({cx}, {cz}): {orig_len} -> {len(kept)} entities.")
                    nbt_file['Entities'] = nbtlib.List[nbtlib.Compound](kept)
                    buf = io.BytesIO()
                    nbt_file.write(buf)
                    new_compressed = zlib.compress(buf.getvalue())
                    new_payload_len = len(new_compressed) + 1
                    assert (new_payload_len + 4) <= sector_count * 4096, "New chunk data exceeds sector count!"
                    data[sec_start:sec_start+4] = struct.pack('>I', new_payload_len)
                    data[sec_start+4] = 2
                    data[sec_start+5:sec_start+4+new_payload_len] = new_compressed
                    pad_start = sec_start + 4 + new_payload_len
                    pad_end = sec_start + sector_count * 4096
                    data[pad_start:pad_end] = b'\x00' * (pad_end - pad_start)

    with open(out_path, 'wb') as f:
        f.write(data)
    print(f"Generated clean MCA: {out_path}")
    print(f"Summary kept: {len(seen_leaders)}/9 leaders, {len(seen_pokemon)}/9 pokemon, {len(seen_carteles)}/9 carteles.")

if __name__ == "__main__":
    if len(sys.argv) > 2:
        deduplicate_ciudadela_mca(sys.argv[1], sys.argv[2])
    else:
        print("Usage: python deduplicate.py <in.mca> <out.mca>")
